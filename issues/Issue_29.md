# Issue 29: MCP interface

## Intent

Expose TopTeacher through a bounded Model Context Protocol interface so an AI
client can inspect the teaching context, read levels of expectations and pupil
results, create a complete level of expectations for a blank exam, and create a
course from a pupil roster without silently merging people who share a name.

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
  exam, level-of-expectations, and pupil-result tool groups. Each group owns its
  schemas; there is no central all-purpose tool or DTO class.
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
  are written in one transaction.
- Keep course-roster protocol handling, read-only pupil identity resolution,
  and transactional persistence in separate components.
- Subjects and grading scales remain read-only reference data. Course creation
  options expose their active IDs without adding mutation plumbing for them.
- Derive sort order from the nested request order. Criteria continue to be
  derived by the existing `[label](eh:key)` Markdown convention.
- Bound nested request sizes and Markdown lengths at the MCP boundary.

## Initial tool surface

- `list_courses`
- `get_course_creation_options`
- `create_course_with_pupils`
- `list_exams`
- `get_level_of_expectations`
- `list_exam_pupils`
- `get_pupil_result`
- `create_level_of_expectations`

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
- [x] Added a read-only course-creation options tool; subjects and grading scales
  still have no MCP write path.
- [x] Added schema, behavior, security, context, and HTTP integration tests.
- [x] Documented configuration and client connection details.
- [x] Ran the canonical formatter and the required reactor test suite.

## Verification

- `mvn -pl topteacher-app -am test`: 225 tests passed across the six-module
  reactor, including 21 focused `topteacher-mcp` tests and 112 assembled
  `topteacher-app` tests.
- The disabled context exposes no MCP tools. The enabled context registers the
  eight intended object-rooted tool schemas.
- The HTTP integration test verifies an unauthenticated `401` response, an
  authenticated MCP initialization using protocol `2025-06-18`, the initialized
  notification, discovery of all eight tools, the no-elicitation
  `NEEDS_RESOLUTION` path without a write, and a successful conversational retry
  at `/top-teacher/mcp`.
- Credential loading and endpoint filtering fail closed when configuration is
  missing or invalid.
- The canonical devtools formatter was applied to all 172 tracked Java files
  and committed separately before the MCP work was reapplied. The formatter
  changed 50 pre-existing files.
- `git diff --check` passed, and the new files contain no trailing whitespace.
