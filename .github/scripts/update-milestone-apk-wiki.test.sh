#!/usr/bin/env bash
# AI assistance was used for implementation support.
#
# Tests for update-milestone-apk-wiki.sh. Run with: bash .github/scripts/update-milestone-apk-wiki.test.sh
set -euo pipefail

# The script runs on the Ubuntu CI runner and relies on GNU date (`date -d`).
if ! date -d @0 > /dev/null 2>&1; then
  echo "These tests need GNU date: run them on Linux or in CI (macOS ships BSD date)."
  exit 1
fi

script="$(dirname "$0")/update-milestone-apk-wiki.sh"
# shellcheck source=update-milestone-apk-wiki.sh
source "$script"

failures=0
check() {
  local description=$1 expected=$2 actual=$3
  if [ "$expected" = "$actual" ]; then
    echo "ok   $description"
  else
    echo "FAIL $description: expected '$expected', got '$actual'"
    failures=$((failures + 1))
  fi
}
epoch() { date -u -d "$1" +%s; }

# M4 is listed first to check that the earliest deadline wins, not the first listed.
MILESTONE_DEADLINES="M4=2027-01-10T23:59:00+01:00 M3=2026-12-17T23:59:00+01:00"
m3_deadline=$(epoch 2026-12-17T22:59:00Z)

check "build before the deadline belongs to M3" \
  "M3 $m3_deadline" "$(next_milestone "$(epoch 2026-12-17T22:58:59Z)")"
check "build exactly at the deadline still counts" \
  "M3 $m3_deadline" "$(next_milestone "$m3_deadline")"
check "build one second after the deadline moves to M4" \
  "M4" "$(next_milestone "$((m3_deadline + 1))" | cut -d' ' -f1)"
check "build after every deadline belongs to no milestone" \
  "" "$(next_milestone "$(epoch 2027-02-01T00:00:00Z)")"

MILESTONE_DEADLINES="M3=not-a-date"
if next_milestone 0 2> /dev/null; then status=0; else status=1; fi
check "invalid deadline fails" "1" "$status"

# End to end: the page is written for the right milestone and left alone afterwards.
WIKI_DIR=$(mktemp -d)
trap 'rm -rf "$WIKI_DIR"' EXIT
export WIKI_DIR MILESTONE_DEADLINES="M3=2026-12-17T23:59:00+01:00"
export REPO_URL=https://github.com/o/r RUN_URL=https://github.com/o/r/actions/runs/7 RUN_NUMBER=30
export COMMIT_SHA=abcdef1234567 ARTIFACT_NAME=roamigo-abcdef1.apk ARTIFACT_URL=https://github.com/o/r/a/9

RUN_CREATED_AT=2026-12-17T20:00:00Z bash "$script" > /dev/null
page="$WIKI_DIR/Milestone-M3-—-APK.md"
check "page links the run" "1" "$(grep -c "(https://github.com/o/r/actions/runs/7) (Build Sprint APK run #30" "$page")"
check "page links the artifact" "1" "$(grep -c "\*\*roamigo-abcdef1.apk\*\* (\[direct artifact link\](https://github.com/o/r/a/9))" "$page")"
check "page shows the deadline in Lausanne time" "1" "$(grep -c "17 December 2026 at 23:59 CET" "$page")"

before=$(cat "$page")
RUN_CREATED_AT=2026-12-18T09:00:00Z RUN_NUMBER=31 bash "$script" > /dev/null
check "build after the deadline leaves the page unchanged" "$before" "$(cat "$page")"

if ((failures > 0)); then
  echo "$failures test(s) failed."
  exit 1
fi
echo "All tests passed."
