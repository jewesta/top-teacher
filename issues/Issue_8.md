# Issue 8

## Rework Settings Backup View and Clipboard Copying

This issue restructured the settings view so backup handling is no longer
embedded directly in `SettingsView`, added visibility for the active H2 database
file, and tightened coding conventions around comments and code literals.

## Settings Structure

- Introduced `SettingsTab` as a small extension point for settings sections.
- Refactored the database backup UI into `DatabaseBackupSettingsTab`.
- Kept `SettingsView` focused on hosting injected tabs in a `TabSheet`.
- Preserved the existing backup form behavior, validation, save handling,
  manual backup action, and scheduler refresh.

## H2 Database File Display

- Added a read-only field at the top of the backup settings tab showing the
  currently used H2 `.mv.db` file.
- Resolved the displayed file from `tt.database.file` and, as a fallback, from
  file-based `spring.datasource.url` values.
- Added a separator below the database file field so the existing backup
  description remains visually separated.
- Covered the field ordering, read-only behavior, and copy button wiring in the
  settings view tests.

## Clipboard Copy Component

- Added `ClipboardCopyButton` as a reusable `Composite<Button>` for icon-only
  clipboard actions.
- Implemented the clipboard write in a browser-side click handler so the browser
  still treats the operation as user-initiated.
- Bridged the async JavaScript result back into Vaadin with custom DOM
  success/failure events to show German notifications.
- Added explanatory comments around the lower-level element event bridge.

## Code Style and Cleanup

- Added AGENTS rules for block comments on multi-line comments.
- Added AGENTS guidance for embedded code indentation inside Java text blocks.
- Applied the agreed three-space indentation style to the clipboard JavaScript
  text block.
- Included branch-wide formatting cleanup from the code-formatting pass.

## Verification

- Ran `mvn -pl topteacher-app -am test`.
- Ran `git diff --check`.
