#!/usr/bin/env bash
# Regression suite for the in-page ad blocker.
#
# The blocker lives as a raw JS string (AD_BLOCK_JS) inside AdblockBrowserScreen.kt
# so it can be injected into every WebView frame. That makes it invisible to any
# Kotlin test, so this extracts the real script and runs it against a synthetic
# DOM containing every ad shape that has actually reached a user, plus the legit
# floating controls that must survive.
#
# Usage:  bash tools/adblock-test/run.sh
# Exits non-zero if any case fails.

set -euo pipefail
cd "$(dirname "$0")"

SRC="../../app/src/main/java/com/streamflow/ui/browser/AdblockBrowserScreen.kt"

if [ ! -f "$SRC" ]; then
  echo "Cannot find $SRC" >&2
  exit 1
fi

# Pull the raw string literal out of the Kotlin source so the test always runs
# the SHIPPING script — never a stale copy that has drifted from the app.
awk '/private val AD_BLOCK_JS = """/{f=1;next} /"""\.trimIndent\(\)/{f=0} f' "$SRC" > adblock.js
echo "extracted $(wc -l < adblock.js) lines of AD_BLOCK_JS"

node --check adblock.js
echo "syntax OK"

if [ ! -d node_modules/jsdom ]; then
  echo "installing jsdom..."
  npm install jsdom --silent --no-fund --no-audit
fi

node test.js
