#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

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

echo "Starting TopTeacher..."
echo "App:        http://localhost:8080/"
echo "H2 console: http://localhost:8080/h2-console/"

mvn -pl westarps-vaadin-markdown -am install -DskipTests
mvn -pl topteacher-app vaadin:prepare-frontend -DskipTests

exec mvn -pl topteacher-app spring-boot:run -Dspring-boot.run.workingDirectory="$PROJECT_ROOT" "$@"
