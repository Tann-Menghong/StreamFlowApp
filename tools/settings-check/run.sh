#!/usr/bin/env bash
# Structural checks for the two dashboard screens.
#
# Both guard defects the app has already shipped:
#
# 1. check-single-dashboard.js  The Library drew TWO dashboards at once — one
#                               above the tab strip, one injected by the History
#                               tab — showing overlapping numbers that were
#                               computed from two different definitions of
#                               "week" and disagreed on screen.
#
# 2. check-categories.js        Settings routes by category NAME across three
#                               lists that must agree. A tile with no branch
#                               opens a completely blank page; a branch with no
#                               tile is unreachable. Neither is a compile error.
#                               It also refuses to let one control live on two
#                               category pages, which is how the app lock ended
#                               up filed under Playback.
#
# Usage: bash tools/settings-check/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
echo "== library: one dashboard per screen =="
node tools/settings-check/check-single-dashboard.js
echo
echo "== settings: category reachability & row ownership =="
node tools/settings-check/check-categories.js
