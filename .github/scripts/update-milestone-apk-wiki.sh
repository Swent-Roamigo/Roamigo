#!/usr/bin/env bash
# AI assistance was used for implementation support.
#
# Points the wiki page of the next milestone at this run's APK, so the page always
# links the last successful main build that started before the milestone deadline.
#
# Required environment:
#   MILESTONE_DEADLINES  space-separated MILESTONE=ISO-8601 pairs, e.g. "M3=2026-12-11T08:45:00+01:00"
#   RUN_CREATED_AT       ISO-8601 time the run was created (when the merge reached main)
#   REPO_URL, RUN_URL, RUN_NUMBER, COMMIT_SHA, ARTIFACT_NAME, ARTIFACT_URL
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

# Writes the wiki page of milestone $1, whose deadline is the epoch $2.
write_page() {
  local milestone=$1 deadline=$2 short_sha=${COMMIT_SHA::7} deadline_text
  deadline_text=$(TZ=$DEADLINE_DISPLAY_ZONE date -d "@$deadline" '+%-d %B %Y at %H:%M %Z')
  cat > "$WIKI_DIR/Milestone-$milestone-—-APK.md" << EOF
## Milestone $milestone — APK

[Download the $milestone APK from GitHub Actions]($RUN_URL) (Build Sprint APK run #$RUN_NUMBER, built from \`main\` at commit \`$short_sha\`)

Open the run, scroll to **Artifacts**, download
**$ARTIFACT_NAME** ([direct artifact link]($ARTIFACT_URL)), unzip it and install the APK.
Requires Android 9 or later.

This page is updated automatically after each successful build of \`main\` started before
the $milestone deadline ($deadline_text), so it links the last build before the deadline.

The APK of the latest commit on \`main\` is also on the [latest release]($REPO_URL/releases/tag/latest) page.
EOF
  echo "Updated the $milestone page to run #$RUN_NUMBER ($short_sha)."
}

main() {
  : "${MILESTONE_DEADLINES:?}" "${RUN_CREATED_AT:?}" "${REPO_URL:?}" "${RUN_URL:?}" "${RUN_NUMBER:?}"
  : "${COMMIT_SHA:?}" "${ARTIFACT_NAME:?}" "${ARTIFACT_URL:?}" "${WIKI_DIR:?}"
  local created next
  created=$(date -u -d "$RUN_CREATED_AT" +%s)
  next=$(next_milestone "$created")
  if [ -z "$next" ]; then
    echo "Run started after every milestone deadline; wiki left unchanged."
    return 0
  fi
  # shellcheck disable=SC2086 # "$next" holds two words on purpose.
  write_page $next
}

# Run only when executed, so the test can source the functions.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main
fi
