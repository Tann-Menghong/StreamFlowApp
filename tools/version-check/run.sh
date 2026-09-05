#!/usr/bin/env bash
# The "What's New" dialog must describe the version the user is running.
#
# Changelog.VERSION_NAME is a hand-maintained constant that has to be bumped in
# the same commit as versionName. v6.20.0 shipped without it: the dialog opened
# headed "Version 6.19.0" and listed the previous release's changes. There is no
# compile error and no test for a stale string constant, so it reached users.
#
# Scope note: this deliberately does NOT compare versionCode against the last
# released tag. Releases are cut through the GitHub API, which creates the tag
# server-side, so the newest tag in a local clone or a CI checkout is years out
# of date -- the comparison would print a number from the 2.x line and call it
# "last released". A check that reports a confident wrong answer is worse than
# no check, so what is verified here is only what can be verified from the tree.
#
# Usage: bash tools/version-check/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

GRADLE="app/build.gradle"
CHANGELOG="app/src/main/java/com/streamflow/data/Changelog.kt"

version_name=$(grep -m1 'versionName "' "$GRADLE" | sed 's/.*versionName "//; s/".*//')
version_code=$(grep -m1 'versionCode ' "$GRADLE" | sed 's/.*versionCode //; s/[^0-9].*//')
changelog_name=$(grep -m1 'VERSION_NAME = "' "$CHANGELOG" | sed 's/.*VERSION_NAME = "//; s/".*//')
note_count=$(sed -n '/val notes = listOf(/,/^    )/p' "$CHANGELOG" | grep -c '^        "' || true)

printf '  %-22s %s\n' "versionName"           "$version_name"
printf '  %-22s %s\n' "versionCode"           "$version_code"
printf '  %-22s %s\n' "Changelog.VERSION_NAME" "$changelog_name"
printf '  %-22s %s\n' "release notes"         "$note_count"
echo

fail=0
if [ "$version_name" != "$changelog_name" ]; then
  echo "  Changelog.VERSION_NAME is \"$changelog_name\" but the app builds as \"$version_name\""
  echo "  -> What's New would show the wrong version and the wrong notes"
  fail=1
fi
if [ "$note_count" -eq 0 ]; then
  echo "  Changelog.notes is empty — What's New would open blank"
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "PASS - the changelog describes the version this build reports"
else
  echo "FAIL - see above"
fi
exit $fail
