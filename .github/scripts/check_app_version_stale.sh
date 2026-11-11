#!/usr/bin/env bash
# Early warning: APP_VERSION in the About box is behind the latest release tag,
# i.e. a release went out but the About box was never bumped.
#
# Usage: ./check_app_version_stale.sh [--strict]
#   (default) : warning only  — use on pull requests, so contributors aren't blocked
#   --strict  : fail          — use on push to main and for releases
set -euo pipefail
cd "$(dirname "$0")/../.."
# shellcheck source=.github/scripts/app_version.sh
source .github/scripts/app_version.sh

strict=0
[[ "${1:-}" == "--strict" ]] && strict=1

actual="$(app_version_read)"
latest="$(latest_tag)"
if [[ -z "$latest" ]]; then
  echo "No v* tags yet — nothing to compare."
  exit 0
fi

case "$(semver_cmp "$actual" "$latest")" in
  eq)
    echo "OK: APP_VERSION = $actual (matches the latest tag)"
    ;;
  gt)
    echo "OK: APP_VERSION = $actual (ahead of the latest tag v$latest — version prepped for the next release)"
    ;;
  lt)
    msg="APP_VERSION is \"$actual\" but the latest tag is v$latest — bump it so the About box isn't stale ('./dev.sh bump patch')."
    if ((strict)); then
      fail "$msg"
    else
      echo "::warning file=$APP_VERSION_SRC::$msg"
    fi
    ;;
esac
