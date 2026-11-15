#!/usr/bin/env bash
set -euo pipefail
cd /home/rua/firefox

echo "[1/3] Incremental C++/Rust build (binaries only, ThinLTO cached)..."
./mach build binaries

echo "[2/3] Staging updated binaries and prefs into dist/geckoview..."
/usr/bin/gmake -C objdir-native-arm64/faster -j15 -s install-dist/bin
if [ "${STAGE_PACKAGE:-0}" = "1" ]; then
  /usr/bin/gmake -C objdir-native-arm64 -j15 -s stage-package
else
  cp -f objdir-native-arm64/dist/bin/libxul.so objdir-native-arm64/dist/geckoview/lib/arm64-v8a/libxul.so
  cp -f objdir-native-arm64/dist/bin/libmozglue.so objdir-native-arm64/dist/geckoview/lib/arm64-v8a/libmozglue.so
fi

echo "[3/3] Packaging Fenix arm64-v8a Release APK (skipping redundant mach tasks & lint)..."
GRADLE_INVOKED_WITHIN_MACH_BUILD=1 ./mach gradle fenix:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease
