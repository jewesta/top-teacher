# AGENTS.md

## Engineering Notes

- Do not casually alter established spacing, padding, borders, or EH layout CSS. The EH and results UIs have been tuned carefully; make layout changes only when explicitly requested.
- Preserve UI state where possible. Avoid unnecessary full rerenders, especially in EH design and result entry flows.
- Save actions should respect dirty state where applicable.
- Prefer Vaadin Binder for form validation instead of manual if/else validation in form editors.
- Keep `schema.sql` and `demo-data.sql` in sync when persistence changes.
- Run `mvn -pl topteacher-app -am test` and `git diff --check` before handing off code changes.
- Do not revert user changes or clean unrelated worktree changes.
