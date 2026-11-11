#!/usr/bin/env bash
# Rewrites the APP_VERSION constant in AboutScreen.kt. The single place that knows
# the line format; used by ./dev.sh bump / tag / release.
#
# Usage: ./bump_version.sh <major|minor|patch|X.Y.Z> [--print-only] [--dry-run]
#   --print-only : only print the resulting version (don't touch the file)
#   --dry-run    : print what would change, leave the file untouched
set -euo pipefail
cd "$(dirname "$0")/../.."
# shellcheck source=.github/scripts/app_version.sh
source .github/scripts/app_version.sh

bump="${1:-}"
[[ -n "$bump" ]] || { echo "usage: $0 <major|minor|patch|X.Y.Z>" >&2; exit 2; }
shift
print_only=0
dry_run=0
for arg in "$@"; do
  case "$arg" in
    --print-only) print_only=1 ;;
    --dry-run) dry_run=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

current="$(app_version_read)"

case "$bump" in
  major | minor | patch)
    IFS=. read -r major minor patch_v <<<"$current"
    case "$bump" in
      major) major=$((10#$major + 1)); minor=0; patch_v=0 ;;
      minor) minor=$((10#$minor + 1)); patch_v=0 ;;
      patch) patch_v=$((10#$patch_v + 1)) ;;
    esac
    next="$major.$minor.$patch_v"
    ;;
  *)
    # Explicit X.Y.Z; validated here so a typo can't reach a tag.
    if [[ ! "$bump" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "invalid version: $bump (expected major|minor|patch or X.Y.Z)" >&2
      exit 2
    fi
    next="$bump"
    ;;
esac

if ((print_only)); then
  printf '%s\n' "$next"
  exit 0
fi

if ((dry_run)); then
  echo "[dry-run] $APP_VERSION_SRC: APP_VERSION \"$current\" -> \"$next\""
  exit 0
fi

# Portable in-place sed (GNU and BSD/macOS): write via a temp file.
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
sed -E "s/^(private const val APP_VERSION = \").*(\")$/\1$next\2/" "$APP_VERSION_SRC" >"$tmp"
grep -q "private const val APP_VERSION = \"$next\"" "$tmp" ||
  { echo "failed to rewrite $APP_VERSION_SRC (pattern not found)" >&2; exit 1; }
mv "$tmp" "$APP_VERSION_SRC"
trap - EXIT
echo "APP_VERSION $current -> $next"
