#!/usr/bin/env bash
# AI assistance was used for implementation support.
#
# Points the wiki page of the next milestone at this run's APK, so the page always
# links the last successful main build that started before the milestone deadline.
#
# Required environment:
#   MILESTONE_DEADLINES  space-separated MILESTONE=ISO-8601 pairs, e.g. "M3=2026-12-11T08:45:00+01:00"
#   RUN_CREATED_AT       ISO-8601 time the run was created (when the merge reached main)
#   REPO_URL, RUN_URL, RUN_NUMBER, SHORT_SHA, ARTIFACT_NAME, ARTIFACT_URL
#   WIKI_DIR             local clone of the wiki repository
set -euo pipefail

# Deadlines are shown to the team in Lausanne time.
readonly DEADLINE_DISPLAY_ZONE=Europe/Zurich

# Prints "MILESTONE EPOCH" for the earliest deadline at or after the epoch $1,
# or nothing when every deadline has passed. Fails on an unparsable deadline.
next_milestone() {
  local created=$1 best_name="" best_deadline="" pair deadline
  for pair in $MILESTONE_DEADLINES; do
    if ! deadline=$(date -u -d "${pair#*=}" +%s 2> /dev/null); then
      echo "::error::Invalid deadline in MILESTONE_DEADLINES: $pair" >&2
      return 1
    fi
    if ((created <= deadline)) && { [ -z "$best_deadline" ] || ((deadline < best_deadline)); }; then
      best_name=${pair%%=*}
      best_deadline=$deadline
    fi
  done
  if [ -n "$best_name" ]; then
    echo "$best_name $best_deadline"
  fi
}

# Path of the wiki page of milestone $1.
page_path() {
  echo "$WIKI_DIR/Milestone-$1-—-APK.md"
}

# Prints the run number the page $1 links to, or nothing when the page does not exist yet.
linked_run_number() {
  [ -f "$1" ] || return 0
  grep -o 'Build Sprint APK run #[0-9]*' "$1" | head -n 1 | grep -o '[0-9]*$' || true
}

# Writes the wiki page of milestone $1, whose deadline is the epoch $2.
write_page() {
  local milestone=$1 deadline=$2 deadline_text
  deadline_text=$(TZ=$DEADLINE_DISPLAY_ZONE date -d "@$deadline" '+%-d %B %Y at %H:%M %Z')
  cat > "$(page_path "$milestone")" << EOF
## Milestone $milestone — APK

[Download the $milestone APK from GitHub Actions]($RUN_URL) (Build Sprint APK run #$RUN_NUMBER, built from \`main\` at commit \`$SHORT_SHA\`)

Open the run, scroll to **Artifacts**, download
**$ARTIFACT_NAME** ([direct artifact link]($ARTIFACT_URL)), unzip it and install the APK.
Requires Android 9 or later.

This page is updated automatically after each successful build of \`main\` started before
the $milestone deadline ($deadline_text), so it links the last build before the deadline.

The APK of the latest commit on \`main\` is also on the [latest release]($REPO_URL/releases/tag/latest) page.
EOF
  echo "Updated the $milestone page to run #$RUN_NUMBER ($SHORT_SHA)."
}

main() {
  : "${MILESTONE_DEADLINES:?}" "${RUN_CREATED_AT:?}" "${REPO_URL:?}" "${RUN_URL:?}" "${RUN_NUMBER:?}"
  : "${SHORT_SHA:?}" "${ARTIFACT_NAME:?}" "${ARTIFACT_URL:?}" "${WIKI_DIR:?}"
  local created next milestone linked
  created=$(date -u -d "$RUN_CREATED_AT" +%s)
  next=$(next_milestone "$created")
  if [ -z "$next" ]; then
    echo "::warning::No milestone deadline after this run in MILESTONE_DEADLINES; add the next one in sprint-apk.yml. Wiki left unchanged."
    return 0
  fi
  milestone=${next%% *}
  linked=$(linked_run_number "$(page_path "$milestone")")
  # Never go back to an older build, e.g. when an old run is re-run after newer ones.
  if [ -n "$linked" ] && ((RUN_NUMBER < linked)); then
    echo "The $milestone page already links the newer run #$linked; wiki left unchanged."
    return 0
  fi
  # shellcheck disable=SC2086 # "$next" holds two words on purpose.
  write_page $next
}

# Run only when executed, so the test can source the functions.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main
fi
