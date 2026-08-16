#!/usr/bin/env bash
# Fails if an icon-only button has no screen-reader label.
# A null contentDescription is correct for a DECORATIVE icon beside text; it is
# wrong inside an IconButton, where the icon is the control and TalkBack would
# announce only "button". See check-icon-labels.js.
# Usage: bash tools/a11y-check/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
node tools/a11y-check/check-icon-labels.js
