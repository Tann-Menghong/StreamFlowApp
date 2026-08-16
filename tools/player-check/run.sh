#!/usr/bin/env bash
# Structural regression check for the player screen.
#
# Guards against the defect that shipped in v6.3.0 and blacked out video for
# three releases: a SECOND `is PlayerUiState.Ready ->` branch was added ABOVE
# the original one. Kotlin `when` takes the first matching branch, so the
# original branch -- the one that actually composes the PlayerView -- became
# unreachable. Audio kept playing (that runs in the service), the picture never
# appeared, and it compiles cleanly with no error.
#
# Usage: bash tools/player-check/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
node tools/player-check/check-when-branches.js
