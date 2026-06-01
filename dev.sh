#!/usr/bin/env bash
# Avenarius developer convenience script.
# Usage: ./dev.sh <command>   (run ./dev.sh help for the list)
set -euo pipefail
cd "$(dirname "$0")"

# --- locate the Android SDK / adb -------------------------------------------
if [[ -z "${ANDROID_HOME:-}" ]]; then
  for candidate in "/opt/homebrew/share/android-commandlinetools" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [[ -d "$candidate" ]] && export ANDROID_HOME="$candidate" && break
  done
fi
ADB="${ANDROID_HOME:-}/platform-tools/adb"
[[ -x "$ADB" ]] || ADB="$(command -v adb || true)"

PKG="com.avenarius.app"
ACTIVITY="$PKG/.MainActivity"
APK="composeApp/build/outputs/apk/debug/composeApp-debug.apk"

require_adb() { [[ -n "$ADB" && -x "$ADB" || -n "$(command -v adb || true)" ]] || { echo "adb not found (set ANDROID_HOME)"; exit 1; }; }

cmd="${1:-help}"; shift || true
case "$cmd" in
  test)            ./gradlew :composeApp:desktopTest "$@" ;;          # fast JVM run of commonTest
  test:android)    ./gradlew :composeApp:testDebugUnitTest "$@" ;;
  test:all)        ./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest "$@" ;;
  lint)            ./gradlew :composeApp:ktlintCheck "$@" ;;            # ktlint style check
  format|fmt)      ./gradlew :composeApp:ktlintFormat "$@" ;;          # ktlint auto-fix
  build|apk)       ./gradlew :composeApp:assembleDebug "$@" && echo "APK: $APK" ;;
  check)           ./gradlew :composeApp:ktlintCheck :composeApp:desktopTest :composeApp:assembleDebug :composeApp:compileKotlinDesktop "$@" ;;
  install)         require_adb; "$ADB" install -r "$APK" ;;
  launch)          require_adb; "$ADB" shell am start -n "$ACTIVITY" ;;
  run)             # build + install + launch
                   ./gradlew :composeApp:assembleDebug
                   require_adb; "$ADB" install -r "$APK"
                   "$ADB" shell am force-stop "$PKG" || true
                   "$ADB" shell am start -n "$ACTIVITY" ;;
  desktop)         ./gradlew :composeApp:run "$@" ;;                  # run the desktop app
  logs)            require_adb; "$ADB" logcat --pid="$("$ADB" shell pidof "$PKG" | tr -d '\r')" ;;
  logclear)        require_adb; "$ADB" logcat -c ;;
  devices)         require_adb; "$ADB" devices -l ;;
  clean)           ./gradlew clean ;;
  help|*)
    cat <<EOF
Avenarius dev script

  ./dev.sh test          Run unit tests (commonTest on JVM, fast)
  ./dev.sh test:android  Run Android unit tests
  ./dev.sh test:all      Run both test targets
  ./dev.sh lint          Run ktlint style check
  ./dev.sh format        Auto-fix style with ktlint (alias: fmt)
  ./dev.sh apk           Build the debug APK
  ./dev.sh check         Lint + tests + build both targets (what CI does)
  ./dev.sh install       adb install the debug APK
  ./dev.sh launch        Launch the app on the device
  ./dev.sh run           Build + install + launch
  ./dev.sh desktop       Run the desktop app
  ./dev.sh logs          Tail logcat for the app
  ./dev.sh logclear      Clear logcat
  ./dev.sh devices       List adb devices
  ./dev.sh clean         Gradle clean

  ANDROID_HOME is auto-detected (currently: ${ANDROID_HOME:-<unset>}).
EOF
    ;;
esac
