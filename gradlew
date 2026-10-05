#!/usr/bin/env sh
# Minimal Gradle launcher: uses a local `gradle` install (no vendored jar).
# Usage: ./gradlew <tasks...>  (needs Gradle 8.7+ and JDK 17)
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "Gradle not found. Install Gradle 8.7+ (https://gradle.org/install/) and JDK 17," 1>&2
  echo "then re-run: ./gradlew $*" 1>&2
  exit 1
fi
