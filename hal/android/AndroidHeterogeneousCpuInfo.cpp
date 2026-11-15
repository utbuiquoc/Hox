/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

#include "Hal.h"
#include "HalLog.h"
#include "HalTypes.h"

#include "mozilla/CheckedInt.h"
#include "prsystem.h"
#include <fstream>

namespace mozilla::hal_impl {

using HeterogeneousCpuInfo = hal::HeterogeneousCpuInfo;

mozilla::Maybe<HeterogeneousCpuInfo> CreateHeterogeneousCpuInfo() {
  CheckedInt<size_t> cpuCount = PR_GetNumberOfProcessors();
  if (!cpuCount.isValid()) {
    HAL_ERR("HeterogeneousCpuInfo: Failed to query processor count");
    return Nothing();
  }

  if (cpuCount.value() > HeterogeneousCpuInfo::MAX_CPUS) {
    HAL_ERR("HeterogeneousCpuInfo: Too many cpus");
    return Nothing();
  }

  struct CpuEntry {
    size_t cpu;
    int freq;
    int pkg;
  };
  std::vector<CpuEntry> cpu_entries;
  cpu_entries.reserve(cpuCount.value());
  for (size_t i = 0; i < cpuCount.value(); i++) {
    std::stringstream freq_filename;
    // On all devices tested, even with isolated content processes we do have
    // permission to read this file. If, however, this function stops working
    // one day, then this may be a good place to start investigating.
    freq_filename << "/sys/devices/system/cpu/cpu" << i
                  << "/cpufreq/cpuinfo_max_freq";
    std::ifstream ifs(freq_filename.str());
    if (!ifs) {
      HAL_ERR("HeterogeneousCpuInfo: Failed to open file %s",
              freq_filename.str().c_str());
      return Nothing();
    }
    int freq;
    if (!(ifs >> freq)) {
      HAL_ERR("HeterogeneousCpuInfo: Failed to parse file %s",
              freq_filename.str().c_str());
      return Nothing();
    }
    int pkg = -1;
    std::stringstream pkg_filename;
    pkg_filename << "/sys/devices/system/cpu/cpu" << i
                 << "/topology/physical_package_id";
    std::ifstream pkg_ifs(pkg_filename.str());
    if (pkg_ifs) {
      pkg_ifs >> pkg;
    }
    cpu_entries.push_back({i, freq, pkg});
  }

  // Normalize spoofed per-thread max_freq outliers within the same physical
  // package (e.g. Kirin 9020A spoofs cpu7 in pkg 1 to 2400000 kHz while its
  // sibling cpu6 and the rest of pkg 1 report 2002000 kHz).
  for (auto& entry : cpu_entries) {
    if (entry.pkg >= 0) {
      int pkg_min_freq = entry.freq;
      for (const auto& other : cpu_entries) {
        if (other.pkg == entry.pkg && other.freq < pkg_min_freq) {
          pkg_min_freq = other.freq;
        }
      }
      entry.freq = pkg_min_freq;
    }
  }

  std::vector<std::pair<int, int>> cpu_freqs;
  cpu_freqs.reserve(cpu_entries.size());
  for (const auto& entry : cpu_entries) {
    cpu_freqs.push_back(
        std::make_pair(static_cast<int>(entry.cpu), entry.freq));
  }

  std::sort(cpu_freqs.begin(), cpu_freqs.end(),
            [](auto lhs, auto rhs) { return lhs.second < rhs.second; });

  HeterogeneousCpuInfo info;
  info.mTotalNumCpus = cpuCount.value();

  int lowest_freq = cpu_freqs.front().second;
  int highest_freq = cpu_freqs.back().second;
  for (const auto& [cpu, freq] : cpu_freqs) {
    if (freq == highest_freq) {
      info.mBigCpus[cpu] = true;
    } else if (freq == lowest_freq) {
      info.mLittleCpus[cpu] = true;
    } else {
      info.mMediumCpus[cpu] = true;
    }
  }

  HAL_LOG("HeterogeneousCpuInfo: little: %zu, med: %zu, big: %zu",
          info.mLittleCpus.Count(), info.mMediumCpus.Count(),
          info.mBigCpus.Count());

  return Some(info);
}

const Maybe<HeterogeneousCpuInfo>& GetHeterogeneousCpuInfo() {
  static const Maybe<HeterogeneousCpuInfo> cpuInfo =
      CreateHeterogeneousCpuInfo();
  return cpuInfo;
}

}  // namespace mozilla::hal_impl
