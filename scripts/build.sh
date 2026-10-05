#!/usr/bin/env bash
# Build the Android APK. Run from rustpin-android/: ./scripts/build.sh [debug|release]
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="${1:-debug}"
if [ ! -f local.properties ] && [ -n "${ANDROID_HOME:-}" ]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
fi
if [ "$MODE" = release ]; then
  ./gradlew assembleRelease
  echo "APK: app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew assembleDebug
  echo "APK: app/build/outputs/apk/debug/app-debug.apk"
fi
