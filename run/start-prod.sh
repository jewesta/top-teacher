#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

usage() {
    echo "Usage: $0 <h2-file>" >&2
    echo "Example: $0 /Users/<user_name>/topteacher/data/topteacher" >&2
    echo "Note: pass the H2 file path without .mv.db; the suffix is stripped if present." >&2
}

if [ "$#" -ne 1 ]; then
    usage
    exit 1
fi

H2_FILE=$1
case "$H2_FILE" in
    *.mv.db)
        H2_FILE=${H2_FILE%".mv.db"}
        ;;
esac

H2_DIR=$(dirname -- "$H2_FILE")
H2_NAME=$(basename -- "$H2_FILE")

if [ ! -d "$H2_DIR" ]; then
    mkdir -p "$H2_DIR"
fi

if [ ! -w "$H2_DIR" ]; then
    echo "H2 database directory is not writable: $H2_DIR" >&2
    exit 1
fi

H2_DIR_ABS=$(CDPATH= cd -- "$H2_DIR" && pwd)
H2_FILE_ABS="$H2_DIR_ABS/$H2_NAME"
H2_DB_FILE="$H2_FILE_ABS.mv.db"

case "$H2_FILE_ABS" in
    "$PROJECT_ROOT"/*)
        echo "H2 database must live outside the repository: $H2_FILE_ABS" >&2
        exit 1
        ;;
esac

if [ -d "$H2_FILE_ABS" ]; then
    echo "H2 database file path points to a directory: $H2_FILE_ABS" >&2
    exit 1
fi

if [ -e "$H2_DB_FILE" ]; then
    if [ ! -f "$H2_DB_FILE" ]; then
        echo "H2 database file is not a regular file: $H2_DB_FILE" >&2
        exit 1
    fi
    if [ ! -r "$H2_DB_FILE" ]; then
        echo "H2 database file is not readable: $H2_DB_FILE" >&2
        exit 1
    fi
    if [ ! -w "$H2_DB_FILE" ]; then
        echo "H2 database file is not writable: $H2_DB_FILE" >&2
        exit 1
    fi
fi

if [ -x /usr/libexec/java_home ]; then
    JAVA_21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    if [ -n "$JAVA_21_HOME" ]; then
        export JAVA_HOME="$JAVA_21_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven (mvn) is required but was not found on PATH." >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Java (java) is required but was not found on PATH." >&2
    exit 1
fi

cd "$PROJECT_ROOT"

APPLICATION_PROPERTIES="topteacher-app/src/main/resources/application.properties"
APP_PORT=$(sed -n 's/^server\.port=//p' "$APPLICATION_PROPERTIES" | tail -n 1)
APP_CONTEXT_PATH=$(sed -n 's/^server\.servlet\.context-path=//p' "$APPLICATION_PROPERTIES" | tail -n 1)
APP_PORT=${APP_PORT:-8080}

APP_VERSION=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' topteacher-app/pom.xml | sed -n '1p')
APP_VERSION=${APP_VERSION:-0.0.1-SNAPSHOT}
START_PROD_TARGET="$PROJECT_ROOT/target/start-prod"
PACKAGED_JAR="$START_PROD_TARGET/v$APP_VERSION/TopTeacher.jar"

echo "Building TopTeacher production jar..."
"$SCRIPT_DIR/package.sh" jar "$START_PROD_TARGET"

if [ ! -f "$PACKAGED_JAR" ]; then
    echo "Could not find the packaged TopTeacher jar at $PACKAGED_JAR." >&2
    exit 1
fi

echo "Starting TopTeacher in production mode..."
echo "Database: ${H2_FILE_ABS}.mv.db"
echo "App:      http://localhost:${APP_PORT}${APP_CONTEXT_PATH}/"

exec java -jar "$PACKAGED_JAR" \
    --tt.database.file="$H2_FILE_ABS" \
    --spring.h2.console.enabled=false \
    --spring.devtools.restart.enabled=false \
    --spring.devtools.livereload.enabled=false
