#!/usr/bin/env bash
# Fails if any Room entity would be silently lost on backup/restore.
# See check-backup-coverage.js for why this exists.
# Usage: bash tools/backup-check/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
node tools/backup-check/check-backup-coverage.js
