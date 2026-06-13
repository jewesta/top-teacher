# Issue 4

## Summary

- Prepared TopTeacher for jar-based local use and future desktop app packaging.
- Externalized the default H2 database location to operating-system user data
  folders instead of requiring a repository-adjacent path.
- Kept explicit `tt.database.file` overrides available for development,
  migrations, and advanced deployments.
- Documented the new default database locations and plain jar startup path.

## Notes

- macOS default: `~/Library/Application Support/TopTeacher/topteacher`
- Windows default: `%APPDATA%\TopTeacher\topteacher`
- Linux default: `$XDG_DATA_HOME/TopTeacher/topteacher` or
  `~/.local/share/TopTeacher/topteacher`
