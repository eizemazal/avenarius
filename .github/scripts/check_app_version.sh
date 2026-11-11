#!/usr/bin/env bash
# Release gate: fails when the About box version differs from the version being
# released, so a pushed tag can't ship with a stale number in AboutScreen.
#
# Usage: ./check_app_version.sh <version>      (a leading "v" is ignored)
set -euo pipefail
cd "$(dirname "$0")/../.."
# shellcheck source=.github/scripts/app_version.sh
source .github/scripts/app_version.sh

expected="${1:-}"
if [[ -z "$expected" ]]; then
  echo "usage: $0 <version>" >&2
  exit 2
fi
expected="${expected#v}"

actual="$(app_version_read)"
if [[ "$actual" != "$expected" ]]; then
  fail "APP_VERSION is \"$actual\" but this release is \"$expected\" — run './dev.sh tag' (or './dev.sh bump' + commit) to sync them."
fi
echo "OK: APP_VERSION = $actual"
