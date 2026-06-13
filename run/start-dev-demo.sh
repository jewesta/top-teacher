#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <h2-file>" >&2
    echo "Example: $0 /Users/<user_name>/topteacher/data/topteacher-demo" >&2
    echo "Note: pass the H2 file path without .mv.db; the suffix is stripped if present." >&2
    exit 1
fi

export TT_DEMO_DATA_CREATE=true
exec "$SCRIPT_DIR/start-dev.sh" "$1"
