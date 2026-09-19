# Issue 29: MCP interface

## Intent

Expose TopTeacher through a bounded Model Context Protocol interface so an AI
client can inspect the teaching context, read levels of expectations and pupil
results, create a complete level of expectations for a blank exam, create a
course from a pupil roster, create and maintain exams and their pupil rosters,
maintain pupil names without silently merging people who share a name, and
trigger the existing database-backup service.

The GitHub issue contains only the title, "MCP-Schnittstelle für KI
implementieren". This first interface therefore stays deliberately small and
does not expose general-purpose database mutation.

## Design

- Use Spring AI's synchronous WebMVC MCP server with Streamable HTTP at `/mcp`.
  With TopTeacher's servlet context path, the external endpoint is
  `/top-teacher/mcp`.
- Keep the protocol adapter, transport security, and focused tests in the
  dedicated `topteacher-mcp` library module. The executable application remains
  in `topteacher-app` and verifies the assembled HTTP endpoint there.
- Split the protocol surface by use case into course discovery, course-roster,
  pupil maintenance, exam, level-of-expectations, and pupil-result tool groups.
  Each group owns focused behavior; there is no central all-purpose tool class.
- Keep the server disabled by default. Enabling it requires an explicit bearer
  token credential file so pupil data is never exposed anonymously on the LAN.
- Expose object-rooted, generated input and output schemas through annotated
  MCP tools.
- Keep tool identifiers and protocol documentation in English while preserving
  the application's German domain values and content.
- Read operations are local, deterministic, and side-effect free.
- A level-of-expectations mutation creates a complete design for an exam whose
  design and notes are still blank. It never overwrites an existing design and
  runs in one transaction.
- Course creation resolves all exact-name and repeated-roster conflicts before
  writing. `REUSE`, `CREATE`, and `SKIP` decisions arrive through native MCP
  elicitation when supported or through a structured `NEEDS_RESOLUTION` result
  and conversational retry otherwise. The final course, pupils, and assignments
  are written in one transaction. If every pupil is skipped, no empty course is
  created.
- Existing-course assignment is a separate, add-only operation. It selects an
  active course by the ID from `list_courses`, applies the same
  `REUSE`/`CREATE`/`SKIP` decisions, treats an already-assigned uniquely named
  active pupil as satisfied, and never removes pupils or changes course
  properties.
- Existing-course removal is a separate destructive operation using exact pupil
  IDs. It delegates to the same backend integrity rule as the UI: pupils
  assigned to any exam in the course cannot be removed, regardless of whether
  results exist. Locked batches are resolved before writing with `SKIP` (keep
  locked pupils and remove the eligible remainder) or `CANCEL` (remove nobody),
  using native elicitation or the conversational fallback. Pupil records and
  exam assignments are never changed.
- Course membership has a dedicated authoritative read operation. It returns
  active assigned pupils by default and includes archived historical pupils
  only when explicitly requested; write responses are not treated as complete
  roster snapshots.
- Exam creation writes the exam and its initial roster atomically for active
  courses. The grading scale defaults to the course scale, an optional active
  override is allowed only at creation, and an optional original-exam ID creates
  a makeup exam under the backend's existing course and date rules. Omitted
  pupil IDs mirror the UI defaults; an explicit empty list creates an empty
  exam.
- Exam updates expose title and date only. Course, grading scale, makeup
  relationship, pupil assignments, level of expectations, and results remain
  unchanged.
- Exam-pupil assignment and removal are separate operations so clients receive
  accurate destructive metadata. Assignment accepts only existing active
  pupils already belonging to the course and is idempotent. Removal delegates
  to the backend's result lock and uses the same portable `SKIP`/`CANCEL`
  handling as course removal. Both batch operations are transactional.
- Keep course-roster protocol handling, read-only pupil identity resolution,
  and transactional persistence in separate components.
- Treat archived records as historical throughout TopTeacher: exclude them from
  default discovery, identity matching, duplicate detection, and new
  assignments; expose them only through explicitly requested archive views, and
  never reactivate them implicitly. This is also recorded as a repository-wide
  engineering rule in `AGENTS.md`.
- Standalone pupil creation creates active records and resolves conflicts with
  `CREATE` or `SKIP`; `REUSE` is deliberately absent because there is no new
  relationship to establish. Pupil updates are practical partial renames by
  internal ID and do not expose lifecycle changes. A rename that would produce
  an exact active-name duplicate requires `UPDATE_ANYWAY` or `CANCEL`.
- Subjects and grading scales remain read-only reference data. Course creation
  options expose their active IDs without adding mutation plumbing for them.
- Manual backup creation delegates to TopTeacher's existing backup service and
  configured target folder. The MCP operation accepts no path, changes no
  settings, and returns a structured failure when backup is not configured or
  cannot be written.
- The EH and Results editors expose an explicit database reload action. Reload
  remains unavailable while local edits are dirty and preserves the selected
  pupil, EH collapse state, and the logical item at the top of the scroll
  viewport.
- Concrete EH and result links use stable database IDs for the complete
  part/category/task/requirement chain. Opening a link validates that chain,
  expands its EH ancestors, and scrolls the requirement into view. Result links
  additionally require and select an assigned pupil.
- Derive sort order from the nested request order. Criteria continue to be
  derived by the existing `[label](eh:key)` Markdown convention.
- Bound nested request sizes and Markdown lengths at the MCP boundary.

## Tool surface

- `list_courses`
- `list_course_pupils`
- `get_course_creation_options`
- `create_course_with_pupils`
- `assign_pupils_to_course`
- `remove_pupils_from_course`
- `list_pupils`
- `create_pupils`
- `update_pupil`
- `list_exams`
- `create_exam`
- `update_exam`
- `assign_pupils_to_exam`
- `remove_pupils_from_exam`
- `get_level_of_expectations`
- `list_exam_pupils`
- `get_pupil_result`
- `create_level_of_expectations`
- `create_database_backup`

## Progress

- [x] Created the dedicated issue worktree and branch from current `main`.
- [x] Reviewed the canonical devtools worktree, issue-note, formatting, and
  verification rules.
- [x] Selected Spring AI 2.0.1, synchronous WebMVC, and Streamable HTTP.
- [x] Added the protected MCP transport and tool adapter.
- [x] Extracted the adapter, endpoint security, and focused tests into the
  dedicated `topteacher-mcp` library module.
- [x] Split the operations across focused MCP tool components.
- [x] Added atomic course-and-roster creation with portable conflict resolution
  and optional native elicitation.
- [x] Added atomic, add-only pupil assignment to existing active courses with
  already-assigned requests handled as no-ops.
- [x] Added atomic pupil removal from active courses with the UI's existing exam
  locks and portable `SKIP`/`CANCEL` resolution.
- [x] Added authoritative course-roster discovery with active-by-default archive
  handling.
- [x] Added atomic main- and makeup-exam creation plus title/date-only exam
  updates.
- [x] Added transactional, idempotent exam-pupil assignment and result-aware
  removal with portable `SKIP`/`CANCEL` resolution.
- [x] Added active-by-default pupil discovery, standalone pupil creation, and
  partial pupil renaming with portable duplicate confirmation.
- [x] Enforced the archive policy in pupil matching and course assignment; MCP
  cannot change lifecycle state or implicitly reactivate a pupil.
- [x] Closed the existing exam-assignment archive gap in the shared backend and
  UI so archived pupils cannot receive new exam assignments.
- [x] Added stable exam-view deep links for the exam itself, its level of
  expectations, its Results tab, and an assigned pupil within Results.
- [x] Added scroll-preserving reload actions to the EH and Results toolbars and
  concrete requirement deep links for both editors.
- [x] Positioned reload at the end of both toolbars and added an explicit
  Markdown viewer render-completion event so result scrolling is restored only
  after asynchronously rendered content has settled.
- [x] After adding an EH part, category, task, or requirement, rerender the
  hierarchy and scroll the new element to the top of the viewport.
- [x] Show the EH hierarchy at the viewport's top as a live breadcrumb beside
  the total points in both the EH and results views.
- [x] Make breadcrumb levels clickable for in-document navigation and add an
  EH or pupil-initial root link that returns to the top.
- [x] Keep the breadcrumb hidden until the first EH hierarchy level reaches the
  viewport edge.
- [x] Keep aggregate EH expand/collapse controls in sync when a parent section
  is collapsed individually and thereby hides otherwise-open descendants.
- [x] Added a read-only course-creation options tool; subjects and grading scales
  still have no MCP write path.
- [x] Added manual database-backup creation through the existing configured
  backup service, including a structured unconfigured/failure result.
- [x] Added schema, behavior, security, context, and HTTP integration tests.
- [x] Documented configuration and client connection details.
- [x] Ran the canonical formatter and the required reactor test suite.

## Verification

- `mvn -pl topteacher-app -am test`: 286 tests passed across the six-module
  reactor, including 70 focused `topteacher-mcp` tests and 123 assembled
  `topteacher-app` tests.
- Focused editor and route tests verify clean-state reload actions, stable
  hierarchy anchors, full parent-chain validation, EH ancestor expansion, and
  concrete EH/result route selection.
- Live demo-data checks opened concrete EH and result requirement URLs, found
  the requested requirement at the top of each viewport, and confirmed that
  toolbar reloads preserved that anchored scroll position.
- The disabled context exposes no MCP tools. The enabled context registers the
  nineteen intended object-rooted tool schemas.
- The HTTP integration test verifies an unauthenticated `401` response, an
  authenticated MCP initialization using protocol `2025-06-18`, the initialized
  notification, discovery of all nineteen tools, active-by-default and explicit
  archived pupil discovery, the no-elicitation `NEEDS_RESOLUTION` path without a
  write, a successful conversational retry, and both the write and idempotent
  no-op paths for existing-course assignment at `/top-teacher/mcp`. It also
  verifies authoritative course-roster readback, valid `list_exams` output when
  optional exam relationship IDs are absent, and the locked-removal fallback in
  which `SKIP` keeps an exam pupil while removing the eligible remainder. It
  also verifies exam creation with the default roster, title/date update,
  exam-pupil assignment and removal, and that a manual backup request reports a
  missing backup target as a structured failure.
- Credential loading and endpoint filtering fail closed when configuration is
  missing or invalid.
- The canonical devtools formatter was applied to all 172 tracked Java files
  and committed separately before the MCP work was reapplied. The formatter
  changed 50 pre-existing files.
- `git diff --check` passed, and the new files contain no trailing whitespace.
