#!/usr/bin/env bash
# Shared helpers for the version checks. Sourced by the check_*/bump_* scripts,
# not executed directly.
set -euo pipefail

# Repo-relative path to the one committed copy of the app version (About box).
APP_VERSION_SRC="composeApp/src/commonMain/kotlin/com/avenarius/app/ui/screens/AboutScreen.kt"

# Prints the APP_VERSION constant from AboutScreen.kt (e.g. "1.0.9").
app_version_read() {
  local v
  v="$(sed -nE 's/^[[:space:]]*private const val APP_VERSION = "([^"]+)".*/\1/p' "$APP_VERSION_SRC" | head -n1)"
  if [[ -z "$v" ]]; then
    echo "::error file=$APP_VERSION_SRC::APP_VERSION declaration not found" >&2
    return 1
  fi
  printf '%s\n' "$v"
}

# Latest release tag without the leading "v" (empty when the repo has no v* tags).
latest_tag() {
  git describe --tags --abbrev=0 --match 'v*' 2>/dev/null | sed 's/^v//' || true
}

# Compares two X.Y.Z versions and prints "lt", "eq" or "gt".
semver_cmp() {
  local a1 a2 a3 b1 b2 b3 i a b
  IFS=. read -r a1 a2 a3 <<<"$1"
  IFS=. read -r b1 b2 b3 <<<"$2"
  for i in 1 2 3; do
    eval "a=\${a$i}"; eval "b=\${b$i}"
    if ((10#$a < 10#$b)); then
      echo lt
      return
    fi
    if ((10#$a > 10#$b)); then
      echo gt
      return
    fi
  done
  echo eq
}

# Emits a GitHub Actions error annotation and exits non-zero.
fail() {
  echo "::error file=$APP_VERSION_SRC::$1"
  exit 1
}
