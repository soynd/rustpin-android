#!/usr/bin/env bash
# Install debug APK on a connected device/emulator.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew installDebug
