#!/usr/bin/env bash
# Structural regression checks for Compose `when` blocks.
#
# Guards the defect that shipped in v6.3.0 and blacked out video for three
# releases: a SECOND `is PlayerUiState.Ready ->` branch was added ABOVE the
# original one. Kotlin `when` runs only the first matching branch, so the
# original branch -- the one that composes the PlayerView -- became unreachable.
# Audio kept playing (that runs in the media service), the picture never
# appeared, and it compiled with no error and no lint warning.
#
# 1. check-when-branches.js      player screen: is the PlayerView reachable?
# 2. check-duplicate-branches.js whole codebase: any shadowed `when` branch?
#
# Usage: bash tools/player-check/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
echo "== player screen: PlayerView reachability =="
node tools/player-check/check-when-branches.js
echo
echo "== codebase: duplicate when-branches =="
node tools/player-check/check-duplicate-branches.js
