# Issue 4

## Summary

- Prepared TopTeacher for jar-based local use and future desktop app packaging.
- Added a `jpackage` app-image build path for a macOS local app with a
  bundled Java runtime.
- Added `run/package.sh` to create either a runnable production jar under
  `<release-target>/v<version>/TopTeacher.jar` or a macOS app
  archive under `<release-target>/v<version>/TopTeacher.app.<architecture>.zip`.
- Added `run/package-all.sh` as a convenience script that calls both package
  modes for the same release target.
- Externalized the default H2 database location to operating-system user data
  folders instead of requiring a repository-adjacent path.
- Kept explicit `tt.database.file` overrides available for development,
  migrations, and advanced deployments.
- Documented the new default database locations and plain jar startup path.
- Added an opt-in local browser launcher used by the packaged TopTeacher! app so
  users land directly on the local TopTeacher URL after starting the app.
- Added opt-in Dock lifecycle integration so clicking the running app icon opens
  the TopTeacher browser window.
- Added an `About TopTeacher!` item to the macOS Dock context menu.
- Added the packaged app version next to the Vaadin header logo.
- Bundled the square TopTeacher icon for the app image and Dock lifecycle icon.
- Generate the macOS `.icns` app icon from the curated TopTeacher PNG during
  app packaging.

## Notes

- macOS default: `~/Library/Application Support/TopTeacher/topteacher`
- Windows default: `%APPDATA%\TopTeacher\topteacher`
- Linux default: `$XDG_DATA_HOME/TopTeacher/topteacher` or
  `~/.local/share/TopTeacher/topteacher`
- Build the jar with `./run/package.sh jar <release-target>`; output goes to
  `<release-target>/v<version>/TopTeacher.jar`.
- Build the app image with `./run/package.sh macos-app <release-target>`; output goes
  to `<release-target>/v<version>/TopTeacher.app.<architecture>.zip` on macOS.
- Build both with `./run/package-all.sh <release-target>`.
