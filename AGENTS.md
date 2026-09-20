# AGENTS.md

## Engineering Notes

- Do not casually alter established spacing, padding, borders, or EH layout CSS. The EH and results UIs have been tuned carefully; make layout changes only when explicitly requested.
- Use English for Java/domain identifiers and operational docs; keep German for user-facing labels where the app needs it.
- Address users as `Du`, not `Sie`, in TopTeacher UI copy.
- Use block comments for multi-line comments.
- In code literals such as Java text blocks, formatter-produced indentation around the literal is fine; indentation that is part of the embedded code should use spaces, preferably three spaces per level.
- Translate `Leistungshorizont` as `level of expectations` in Java and technical documentation.
- Keep module boundaries clear: domain types live in `topteacher-model`, repositories/export services and SQL/templates in `topteacher-backend`, and Vaadin/application wiring in `topteacher-app`.
- Preserve UI state where possible. Avoid unnecessary full rerenders, especially in EH design and result entry flows.
- Save actions should respect dirty state where applicable.
- Treat archived records as historical data throughout TopTeacher. Exclude them from default discovery, identity matching, duplicate detection, and new assignments; expose them only through explicitly requested archive views, and never reactivate them implicitly as a side effect of another operation.
- Prefer Vaadin Binder for form validation instead of manual if/else validation in form editors.
- Keep `db/schema.sql`, `db/base-data.sql`, and `db/demo-data.sql` in sync when persistence changes.
- Treat `doc/ops/` as the authoritative functional specification (the Lastenheft). Update the relevant operational document whenever user-visible or domain behavior changes.
- Treat `doc/tech/` as reusable implementation guidance for architecture, components, technical constraints, and lessons learned. Do not use it as the source of functional requirements.
- For issue branches, maintain a concise decision and implementation-status summary in `issues/Issue_<nr>.md`; derive the issue number from the branch name when possible. Once behavior is settled, link to the authoritative `doc/ops/` document instead of duplicating its detailed specification in the issue note.
- Do not run smoke tests by default. Use them only when the issue warrants it, for example when investigating or verifying a UI-specific problem.
- Run `mvn -pl topteacher-app -am test` and `git diff --check` before handing off code changes.
- Do not revert user changes or clean unrelated worktree changes.
