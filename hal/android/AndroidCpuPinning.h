/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef mozilla_AndroidCpuPinning_h
#define mozilla_AndroidCpuPinning_h

#ifdef MOZ_WIDGET_ANDROID

#  include <dirent.h>
#  include <errno.h>
#  include <limits.h>
#  include <sched.h>
#  include <sys/resource.h>
#  include <unistd.h>
#  include <cstdio>
#  include <cstdlib>
#  include <cstring>

#  include "mozilla/BitSet.h"
#  include "mozilla/Hal.h"

namespace mozilla {

struct SinglePrimeCpusets {
  cpu_set_t mMedium;
  cpu_set_t mMedPrime;
  cpu_set_t mNonPrime;
  cpu_set_t mPrime;
  size_t mTotalNumCpus = 0;
};

enum class PinCpuGroup : uint8_t {
  NonPrime,
  Medium,
  MedPrime,
};

inline constexpr int kPreserveNice = INT_MAX;

struct ThreadPinRule {
  const char* mPrefix;
  PinCpuGroup mGroup;
  int mNice = 0;
  bool mPreserveIfAlreadyOffPrime = false;
};

// Matches single-Prime heterogeneous mobile SoCs (e.g. Kirin 9020A: 2 Prime
// SMT threads on 1 physical core, >=4 logical Medium threads, plus E-cores).
inline bool IsSinglePrimeSoc(const hal::HeterogeneousCpuInfo& aInfo) {
  return aInfo.mBigCpus.Count() <= 2 && aInfo.mMediumCpus.Count() >= 4;
}

inline cpu_set_t BuildCpuSetFromBitSet(
    const BitSet<hal::HeterogeneousCpuInfo::MAX_CPUS>& aCpus,
    size_t aTotalNumCpus) {
  static_assert(hal::HeterogeneousCpuInfo::MAX_CPUS <= CPU_SETSIZE,
                "HeterogeneousCpuInfo::MAX_CPUS is too large for CPU_SETSIZE");
  cpu_set_t cpuset;
  CPU_ZERO(&cpuset);
  for (size_t i = 0; i < aTotalNumCpus; i++) {
    if (aCpus.test(i)) {
      CPU_SET(i, &cpuset);
    }
  }
  return cpuset;
}

inline bool GetSinglePrimeCpusets(SinglePrimeCpusets* aOut) {
  const auto& cpuInfo = hal::GetHeterogeneousCpuInfo();
  if (!cpuInfo || cpuInfo->mBigCpus.Count() == cpuInfo->mTotalNumCpus ||
      !IsSinglePrimeSoc(*cpuInfo)) {
    return false;
  }
  auto nonPrimeCpus = cpuInfo->mLittleCpus;
  nonPrimeCpus |= cpuInfo->mMediumCpus;
  auto medPrimeCpus = cpuInfo->mMediumCpus;
  medPrimeCpus |= cpuInfo->mBigCpus;
  aOut->mMedium =
      BuildCpuSetFromBitSet(cpuInfo->mMediumCpus, cpuInfo->mTotalNumCpus);
  aOut->mMedPrime =
      BuildCpuSetFromBitSet(medPrimeCpus, cpuInfo->mTotalNumCpus);
  aOut->mNonPrime =
      BuildCpuSetFromBitSet(nonPrimeCpus, cpuInfo->mTotalNumCpus);
  aOut->mPrime =
      BuildCpuSetFromBitSet(cpuInfo->mBigCpus, cpuInfo->mTotalNumCpus);
  aOut->mTotalNumCpus = cpuInfo->mTotalNumCpus;
  return true;
}

inline bool GetSinglePrimeMediumCpuset(cpu_set_t* aOutCpuset,
                                       size_t* aOutTotalNumCpus = nullptr) {
  SinglePrimeCpusets cpusets;
  if (!GetSinglePrimeCpusets(&cpusets)) {
    return false;
  }
  *aOutCpuset = cpusets.mMedium;
  if (aOutTotalNumCpus) {
    *aOutTotalNumCpus = cpusets.mTotalNumCpus;
  }
  return true;
}

// Re-applies aTargetCpuset only if the current mask differs, avoiding redundant
// affinity syscalls and Stylo/scheduler locks during idle watchdogs.
inline bool PinThreadIfDrifted(pid_t aTid, const cpu_set_t& aTargetCpuset) {
  cpu_set_t current;
  CPU_ZERO(&current);
  if (sched_getaffinity(aTid, sizeof(cpu_set_t), &current) == 0 &&
      CPU_EQUAL(&current, &aTargetCpuset)) {
    return false;
  }
  sched_setaffinity(aTid, sizeof(cpu_set_t), &aTargetCpuset);
  return true;
}

namespace detail {

inline bool HasAnyCpuOverlap(const cpu_set_t& aMask, const cpu_set_t& aSubset,
                             size_t aTotalNumCpus) {
  for (size_t i = 0; i < aTotalNumCpus; i++) {
    if (CPU_ISSET(i, &aMask) && CPU_ISSET(i, &aSubset)) {
      return true;
    }
  }
  return false;
}

inline bool ReadThreadComm(const char* aTidName, char* aOutComm,
                           size_t aOutSize) {
  char path[64];
  int n = snprintf(path, sizeof(path), "/proc/self/task/%s/comm", aTidName);
  if (n <= 0 || static_cast<size_t>(n) >= sizeof(path)) {
    return false;
  }
  FILE* f = fopen(path, "r");
  if (!f) {
    return false;
  }
  bool ok = fgets(aOutComm, static_cast<int>(aOutSize), f) != nullptr;
  fclose(f);
  if (!ok) {
    return false;
  }
  size_t len = strlen(aOutComm);
  if (len > 0 && aOutComm[len - 1] == '\n') {
    aOutComm[--len] = '\0';
  }
  return true;
}

inline const ThreadPinRule* FindMatchingRule(const char* aComm,
                                             const ThreadPinRule* aRules,
                                             size_t aNumRules) {
  for (size_t i = 0; i < aNumRules; i++) {
    if (strncmp(aComm, aRules[i].mPrefix, strlen(aRules[i].mPrefix)) == 0) {
      return &aRules[i];
    }
  }
  return nullptr;
}

inline const cpu_set_t& ResolveRuleCpuset(const SinglePrimeCpusets& aCpusets,
                                          PinCpuGroup aGroup) {
  switch (aGroup) {
    case PinCpuGroup::Medium:
      return aCpusets.mMedium;
    case PinCpuGroup::MedPrime:
      return aCpusets.mMedPrime;
    case PinCpuGroup::NonPrime:
    default:
      return aCpusets.mNonPrime;
  }
}

inline void SetThreadNiceIfOther(pid_t aTid, int aTargetNice) {
  if (aTargetNice == kPreserveNice || sched_getscheduler(aTid) != SCHED_OTHER) {
    return;
  }
  errno = 0;
  int currentNice = getpriority(PRIO_PROCESS, aTid);
  if (errno == 0 && currentNice != aTargetNice) {
    setpriority(PRIO_PROCESS, aTid, aTargetNice);
  }
}

}  // namespace detail

// Keeps all non-allowed threads in /proc/self/task off Prime CPUs so OS
// cgroup rules (e.g. HarmonyOS cpu.smt_expeller) and same-process siblings
// cannot evict or preempt an active Prime-pinned content main thread.
// Matches aRules by prefix for target cpuset and CFS nice value; unmatched
// threads fall back to NonPrime and reset inherited aNormalizeNiceEquals to 0.
inline void PinSelfThreadsOffPrime(const SinglePrimeCpusets& aCpusets,
                                   const ThreadPinRule* aRules,
                                   size_t aNumRules, pid_t aAllowedPrimeTid,
                                   bool aFullScan, int aNormalizeNiceEquals) {
  DIR* dir = opendir("/proc/self/task");
  if (!dir) {
    return;
  }
  while (struct dirent* ent = readdir(dir)) {
    if (ent->d_name[0] < '0' || ent->d_name[0] > '9') {
      continue;
    }
    char* endPtr = nullptr;
    long rawTid = strtol(ent->d_name, &endPtr, 10);
    if (rawTid <= 0 || !endPtr || *endPtr != '\0') {
      continue;
    }
    const pid_t tid = static_cast<pid_t>(rawTid);
    if (tid == aAllowedPrimeTid) {
      continue;
    }

    cpu_set_t current;
    CPU_ZERO(&current);
    if (sched_getaffinity(tid, sizeof(cpu_set_t), &current) != 0) {
      continue;
    }
    const bool hasPrime = detail::HasAnyCpuOverlap(current, aCpusets.mPrime,
                                                   aCpusets.mTotalNumCpus);
    if (!aFullScan && !hasPrime) {
      if (aNormalizeNiceEquals < 0 && sched_getscheduler(tid) == SCHED_OTHER) {
        errno = 0;
        int niceVal = getpriority(PRIO_PROCESS, tid);
        if (errno == 0 && niceVal == aNormalizeNiceEquals) {
          setpriority(PRIO_PROCESS, tid, 0);
        }
      }
      continue;
    }

    char comm[32];
    if (!detail::ReadThreadComm(ent->d_name, comm, sizeof(comm))) {
      continue;
    }
    const ThreadPinRule* rule =
        detail::FindMatchingRule(comm, aRules, aNumRules);
    if (rule) {
      detail::SetThreadNiceIfOther(tid, rule->mNice);
      if (rule->mPreserveIfAlreadyOffPrime && !hasPrime &&
          (CPU_EQUAL(&current, &aCpusets.mMedium) ||
           CPU_EQUAL(&current, &aCpusets.mNonPrime))) {
        continue;
      }
    } else if (aNormalizeNiceEquals < 0 &&
               sched_getscheduler(tid) == SCHED_OTHER) {
      errno = 0;
      int niceVal = getpriority(PRIO_PROCESS, tid);
      if (errno == 0 && niceVal == aNormalizeNiceEquals) {
        setpriority(PRIO_PROCESS, tid, 0);
      }
    }

    const cpu_set_t& target =
        rule ? detail::ResolveRuleCpuset(aCpusets, rule->mGroup)
             : aCpusets.mNonPrime;
    if (!CPU_EQUAL(&current, &target)) {
      sched_setaffinity(tid, sizeof(cpu_set_t), &target);
    }
  }
  closedir(dir);
}

}  // namespace mozilla

#endif  // MOZ_WIDGET_ANDROID

#endif  // mozilla_AndroidCpuPinning_h
