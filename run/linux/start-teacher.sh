#!/usr/bin/env bash
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
DEFAULT_JAR="$SCRIPT_DIR/TopTeacher.jar"

usage() {
    echo "Usage: $0 <TopTeacher.jar> [Spring Boot options...]" >&2
    echo "The jar argument may be omitted when TopTeacher.jar is beside this script." >&2
}

if [ "$#" -gt 0 ]; then
    TOPTEACHER_JAR=$1
    shift
else
    TOPTEACHER_JAR=$DEFAULT_JAR
fi

if [ ! -f "$TOPTEACHER_JAR" ]; then
    echo "Could not find the TopTeacher jar at $TOPTEACHER_JAR." >&2
    usage
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Java 21 or newer is required but was not found on PATH." >&2
    exit 1
fi

echo "TopTeacher is running in this terminal. Press Ctrl-C to stop it."

exec java -jar "$TOPTEACHER_JAR" \
    --tt.launch-browser=true \
    --spring.h2.console.enabled=false \
    --spring.devtools.restart.enabled=false \
    --spring.devtools.livereload.enabled=false \
    "$@"
