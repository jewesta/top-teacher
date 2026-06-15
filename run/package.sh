#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
APP_MODULE="$PROJECT_ROOT/topteacher-app"
APP_TARGET="$APP_MODULE/target"
APP_NAME="TopTeacher"
DISPLAY_APP_NAME="TopTeacher!"
APP_ICON_PNG="$PROJECT_ROOT/topteacher-app/src/main/resources/META-INF/resources/images/topteacher-icon.png"
MAC_APP_ICON="$PROJECT_ROOT/packaging/topteacher.icns"

usage() {
    echo "Usage: $0 <jar|macos-app> <release-target-directory>" >&2
    exit 2
}

if [ "$#" -ne 2 ]; then
    usage
fi

PACKAGE_MODE=$1
case "$PACKAGE_MODE" in
    jar|macos-app)
        ;;
    *)
        usage
        ;;
esac

case "$2" in
    /*)
        RELEASE_TARGET=$2
        ;;
    *)
        RELEASE_TARGET="$(pwd)/$2"
        ;;
esac

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "$2" >&2
        exit 1
    fi
}

create_macos_icon() {
    require_command sips "sips is required to create the macOS app icon but was not found on PATH."
    require_command python3 "python3 is required to create the macOS app icon but was not found on PATH."
    if [ ! -f "$APP_ICON_PNG" ]; then
        echo "Could not find the TopTeacher PNG app icon at $APP_ICON_PNG." >&2
        exit 1
    fi

    ICON_WORK_DIR=$(mktemp -d)
    ICONSET_DIR="$ICON_WORK_DIR/topteacher.iconset"
    mkdir -p "$ICONSET_DIR" "$(dirname -- "$MAC_APP_ICON")"

    sips -z 16 16 "$APP_ICON_PNG" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
    sips -z 32 32 "$APP_ICON_PNG" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
    sips -z 64 64 "$APP_ICON_PNG" --out "$ICONSET_DIR/icon_64x64.png" >/dev/null
    sips -z 128 128 "$APP_ICON_PNG" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
    sips -z 256 256 "$APP_ICON_PNG" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
    sips -z 512 512 "$APP_ICON_PNG" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
    sips -z 1024 1024 "$APP_ICON_PNG" --out "$ICONSET_DIR/icon_1024x1024.png" >/dev/null

    python3 - "$ICONSET_DIR" "$MAC_APP_ICON" <<'PY'
import struct
import sys
from pathlib import Path

iconset = Path(sys.argv[1])
output = Path(sys.argv[2])
chunks = [
    ("icp4", "icon_16x16.png"),
    ("icp5", "icon_32x32.png"),
    ("icp6", "icon_64x64.png"),
    ("ic07", "icon_128x128.png"),
    ("ic08", "icon_256x256.png"),
    ("ic09", "icon_512x512.png"),
    ("ic10", "icon_1024x1024.png"),
]

parts = []
for chunk_type, filename in chunks:
    data = (iconset / filename).read_bytes()
    parts.append(chunk_type.encode("ascii") + struct.pack(">I", len(data) + 8) + data)

output.write_bytes(b"icns" + struct.pack(">I", sum(len(part) for part in parts) + 8) + b"".join(parts))
PY

    rm -rf "$ICON_WORK_DIR"
}

patch_vaadin_frontend() {
    if [ ! -f "$APP_TARGET/classes/META-INF/VAADIN/webapp/index.html" ]; then
        echo "Could not find the Vaadin production frontend in $APP_TARGET/classes." >&2
        exit 1
    fi

    FRONTEND_JAR_PATCH=$(mktemp -d)
    mkdir -p "$FRONTEND_JAR_PATCH/BOOT-INF/classes/META-INF"
    cp -R "$APP_TARGET/classes/META-INF/VAADIN" "$FRONTEND_JAR_PATCH/BOOT-INF/classes/META-INF/VAADIN"
    (cd "$FRONTEND_JAR_PATCH" && jar uf "$APP_JAR" BOOT-INF/classes/META-INF/VAADIN)
    rm -rf "$FRONTEND_JAR_PATCH"
    if ! jar tf "$APP_JAR" | grep -qx "BOOT-INF/classes/META-INF/VAADIN/webapp/index.html"; then
        echo "The packaged TopTeacher jar is missing Vaadin's production index.html." >&2
        exit 1
    fi
}

package_jar() {
    JAR_TARGET="$RELEASE_TARGET/v$APP_VERSION"
    PACKAGED_JAR="$JAR_TARGET/TopTeacher.jar"
    mkdir -p "$JAR_TARGET"
    cp -f "$APP_JAR" "$PACKAGED_JAR"
    cp ./run/linux/start-teacher.sh "$JAR_TARGET/start-teacher.sh"
    chmod +x "$JAR_TARGET/start-teacher.sh"
    
    echo "Created runnable jar at $PACKAGED_JAR."
}

package_macos_app() {
    require_command jpackage "jpackage is required but was not found on PATH. Use a JDK 21 installation."
    require_command ditto "ditto is required to create the macOS app archive but was not found on PATH."

    PACKAGE_TARGET="$RELEASE_TARGET/v$APP_VERSION"
    PACKAGE_WORK="$PROJECT_ROOT/target/package-macos-app/v$APP_VERSION"
    PACKAGE_INPUT="$PACKAGE_WORK/input"
    PACKAGE_STAGING="$PACKAGE_WORK/staging"
    APP_BUNDLE="$PACKAGE_STAGING/$APP_NAME.app"
    APP_ZIP_NAME="$APP_NAME.app.$(uname -m).zip"
    APP_ZIP="$PACKAGE_WORK/$APP_ZIP_NAME"
    PACKAGED_APP_ZIP="$PACKAGE_TARGET/$APP_ZIP_NAME"
    rm -rf "$PACKAGE_INPUT" "$PACKAGE_STAGING"
    rm -f "$APP_ZIP"
    mkdir -p "$PACKAGE_INPUT" "$PACKAGE_STAGING"
    mkdir -p "$PACKAGE_TARGET"
    cp -f "$APP_JAR" "$PACKAGE_INPUT/$(basename -- "$APP_JAR")"

    set -- \
        --type app-image \
        --dest "$PACKAGE_STAGING" \
        --name "$APP_NAME" \
        --app-version "$PACKAGE_VERSION" \
        --vendor Westarps \
        --input "$PACKAGE_INPUT" \
        --main-jar "$(basename -- "$APP_JAR")" \
        --java-options "-Dtt.app.version=$PACKAGE_VERSION" \
        --java-options "-Dtt.launch-browser=true" \
        --java-options "-Dtt.dock-icon=true" \
        --java-options "-Dspring.datasource.url=jdbc:h2:file:\${tt.database.file}" \
        --java-options "-Dspring.h2.console.enabled=false" \
        --java-options "-Dspring.devtools.restart.enabled=false" \
        --java-options "-Dspring.devtools.livereload.enabled=false"

    case "$(uname -s)" in
        Darwin)
            create_macos_icon
            set -- "$@" --mac-package-identifier de.westarps.topteacher
            set -- "$@" --java-options "-Djava.awt.headless=false"
            set -- "$@" --java-options "-Dapple.awt.application.name=$DISPLAY_APP_NAME"
            set -- "$@" --java-options "-Xdock:name=$DISPLAY_APP_NAME"
            set -- "$@" --java-options "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED"
            set -- "$@" --icon "$MAC_APP_ICON"
            ;;
        Linux)
            set -- "$@" --icon "$PROJECT_ROOT/topteacher-app/src/main/resources/META-INF/resources/images/topteacher-icon.png"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            if [ -f "$PROJECT_ROOT/packaging/topteacher.ico" ]; then
                set -- "$@" --icon "$PROJECT_ROOT/packaging/topteacher.ico"
            fi
            ;;
    esac

    jpackage "$@"

    if [ "$(uname -s)" = "Darwin" ]; then
        plutil -replace CFBundleName -string "$DISPLAY_APP_NAME" "$APP_BUNDLE/Contents/Info.plist"
        plutil -replace CFBundleDisplayName -string "$DISPLAY_APP_NAME" "$APP_BUNDLE/Contents/Info.plist"
        (cd "$PACKAGE_STAGING" && ditto -c -k --sequesterRsrc --keepParent "$APP_NAME.app" "$APP_ZIP")
        cp -f "$APP_ZIP" "$PACKAGED_APP_ZIP"
        open "$APP_BUNDLE"
    fi

    echo "Created macOS app archive at $PACKAGED_APP_ZIP."
}

require_command mvn "Maven (mvn) is required but was not found on PATH."
require_command jar "jar is required but was not found on PATH. Use a JDK 21 installation."

cd "$PROJECT_ROOT"

APP_VERSION=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' topteacher-app/pom.xml | sed -n '1p')
APP_VERSION=${APP_VERSION:-0.0.1-SNAPSHOT}
PACKAGE_VERSION=${APP_VERSION%-SNAPSHOT}
case "$PACKAGE_VERSION" in
    0.*|0)
        PACKAGE_VERSION=1.0.0
        ;;
esac

mvn -pl topteacher-app -am -Pproduction package -DskipTests

APP_JAR="$APP_TARGET/topteacher-app-$APP_VERSION.jar"
if [ ! -f "$APP_JAR" ]; then
    echo "Could not find the packaged TopTeacher jar at $APP_JAR." >&2
    exit 1
fi
patch_vaadin_frontend

case "$PACKAGE_MODE" in
    jar)
        package_jar
        ;;
    macos-app)
        package_macos_app
        ;;
esac
