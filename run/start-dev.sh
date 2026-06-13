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

export TT_DATABASE_FILE="$H2_FILE_ABS"
export TT_DEMO_DATA_CREATE=${TT_DEMO_DATA_CREATE:-false}

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

cd "$PROJECT_ROOT"

APPLICATION_PROPERTIES="topteacher-app/src/main/resources/application.properties"
APP_PORT=$(sed -n 's/^server\.port=//p' "$APPLICATION_PROPERTIES" | tail -n 1)
APP_CONTEXT_PATH=$(sed -n 's/^server\.servlet\.context-path=//p' "$APPLICATION_PROPERTIES" | tail -n 1)
H2_CONSOLE_PATH=$(sed -n 's/^spring\.h2\.console\.path=//p' "$APPLICATION_PROPERTIES" | tail -n 1)
APP_PORT=${APP_PORT:-8080}
H2_CONSOLE_PATH=${H2_CONSOLE_PATH:-/h2-console}

echo "Starting TopTeacher..."
echo "Database:   ${TT_DATABASE_FILE}.mv.db"
if [ "$TT_DEMO_DATA_CREATE" = "true" ]; then
    echo "Demo data:  create if database is empty"
fi
echo "App:        http://localhost:${APP_PORT}${APP_CONTEXT_PATH}/"
echo "H2 console: http://localhost:${APP_PORT}${APP_CONTEXT_PATH}${H2_CONSOLE_PATH}/"

mvn -pl westarps-vaadin-markdown,topteacher-backend -am install -DskipTests
mvn -pl topteacher-app vaadin:prepare-frontend -DskipTests

rm -f topteacher-app/src/main/bundles/dev.bundle
rm -rf topteacher-app/target/dev-bundle

exec mvn -pl topteacher-app spring-boot:run -Dspring-boot.run.workingDirectory="$PROJECT_ROOT"
