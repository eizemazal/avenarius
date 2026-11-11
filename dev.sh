#!/usr/bin/env bash
# Avenarius developer convenience script.
# Usage: ./dev.sh <command>   (run ./dev.sh help for the list)
#
# Releasing: `./dev.sh tag [patch|minor|major|X.Y.Z]` bumps the app version shown in
# the About box, commits it, creates the matching vX.Y.Z tag and pushes both, which
# triggers .github/workflows/release.yml. Use `-n` to preview without touching git.
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

# The debug build carries applicationIdSuffix ".dev" (see composeApp/build.gradle.kts),
# so it installs alongside the production app as a separate package. Everything below
# drives the DEV copy; export AVENARIUS_PKG=com.avenarius.app to target production.
APP_ID="com.avenarius.app"
PKG="${AVENARIUS_PKG:-$APP_ID.dev}"
# The applicationId gets the suffix, the Activity class does not — hence the fully
# qualified class name rather than "$PKG/.MainActivity", which pointed at the
# production app and restarted that instead of the one just installed.
ACTIVITY="$PKG/$APP_ID.MainActivity"
APK="composeApp/build/outputs/apk/debug/composeApp-debug.apk"

require_adb() { [[ -n "$ADB" && -x "$ADB" || -n "$(command -v adb || true)" ]] || { echo "adb not found (set ANDROID_HOME)"; exit 1; }; }

# --- release helpers --------------------------------------------------------
# The About box version (APP_VERSION in AboutScreen.kt) is the one number that is
# committed by hand; the rest of the build takes it from the git tag. These helpers
# keep the two in sync: `bump` edits the constant, `tag` bumps + commits + tags +
# pushes, which triggers .github/workflows/release.yml.
require_git_clean() {
  [[ -z "$(git status --porcelain --untracked-files=no)" ]] ||
    { echo "Working tree has uncommitted changes; commit or stash them first."; exit 1; }
}
require_origin() {
  git remote get-url origin >/dev/null 2>&1 ||
    { echo "No 'origin' remote configured."; exit 1; }
}

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
  version)         # About box version vs latest release tag
                   source .github/scripts/app_version.sh
                   local_tag="$(latest_tag)"
                   echo "About box:  $(app_version_read)"
                   echo "Latest tag: ${local_tag:-<none>}"
                   .github/scripts/check_app_version_stale.sh ;;
  bump)            # ./dev.sh bump [major|minor|patch|X.Y.Z] [-n|--dry-run]  (default: patch)
                   dry_run=0
                   bump_arg=""
                   for a in "$@"; do
                     if [[ "$a" == "--dry-run" || "$a" == "-n" ]]; then dry_run=1; else bump_arg="$a"; fi
                   done
                   bump_opts=()
                   ((dry_run)) && bump_opts=(--dry-run)
                   # ${arr[@]+...} rather than "${arr[@]}": bash < 4.4 (macOS's 3.2)
                   # treats an empty array as an unbound variable under `set -u`.
                   .github/scripts/bump_version.sh "${bump_arg:-patch}" ${bump_opts[@]+"${bump_opts[@]}"} ;;
  tag)             # ./dev.sh tag [<bump>] [-n|--dry-run]  → bump + commit + tag + push + release
                   dry_run=0
                   bump_arg=""
                   for a in "$@"; do
                     if [[ "$a" == "--dry-run" || "$a" == "-n" ]]; then dry_run=1; else bump_arg="$a"; fi
                   done
                   bump_arg="${bump_arg:-patch}"
                   source .github/scripts/app_version.sh
                   if ((dry_run)); then
                     next="$(.github/scripts/bump_version.sh "$bump_arg" --print-only)"
                     echo "[dry-run] bump $bump_arg: $(app_version_read) -> $next"
                     echo "[dry-run] git commit -m 'Bump version to $next' -- $APP_VERSION_SRC"
                     echo "[dry-run] git tag -a v$next -m 'Avenarius $next'"
                     echo "[dry-run] git push origin HEAD"
                     echo "[dry-run] git push origin v$next"
                     exit 0
                   fi
                   require_git_clean
                   require_origin
                   old="$(app_version_read)"
                   .github/scripts/bump_version.sh "$bump_arg"
                   new="$(app_version_read)"
                   if git rev-parse -q --verify "refs/tags/v$new" >/dev/null; then
                     echo "Tag v$new already exists; choose another version."; exit 1
                   fi
                   git commit -q -m "Bump version to $new" -- "$APP_VERSION_SRC"
                   git tag -a "v$new" -m "Avenarius $new"
                   echo "Committed and tagged v$new (was $old); pushing…"
                   git push origin HEAD
                   git push origin "v$new"
                   echo "Release v$new triggered: https://github.com/eizemazal/avenarius/actions/workflows/release.yml" ;;
  release)         # ./dev.sh release [<bump>]  → check (lint+tests+build) first, then tag
                   "$0" check
                   "$0" tag "$@" ;;
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

Release / version (About box + git tags)
  ./dev.sh version       Show the About box version and the latest release tag
  ./dev.sh bump [x]      Bump APP_VERSION (major|minor|patch|X.Y.Z; default patch)
  ./dev.sh bump [x] -n   Dry run: show what would change, touch nothing
  ./dev.sh tag [x]       Bump, commit, tag vX.Y.Z, push, trigger the Release workflow
  ./dev.sh tag [x] -n    Dry run: show what would be committed/tagged/pushed
  ./dev.sh release [x]   Same as "tag", but runs "check" (lint+tests+build) first

  [x] is major, minor, patch or an explicit X.Y.Z; -n is short for --dry-run.

  ANDROID_HOME is auto-detected (currently: ${ANDROID_HOME:-<unset>}).
EOF
    ;;
esac
