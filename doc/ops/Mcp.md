# Exam-oriented MCP interface

## Scope and discovery

TopTeacher's MCP interface follows the teaching workflow: create an exam,
select existing pupils, author Aufgaben and Material, define the level of
expectations (EH), and record results. It does not treat the EH as an alternative
owner of the exam hierarchy. The old combined `create_level_of_expectations`
contract is removed; compatibility with that contract is not required.

The endpoint is disabled by default. Authentication and token maintenance are
specified in [SystemSettings.md](SystemSettings.md#mcp-access-and-pupil-pseudonymization).
The effective access mode comes from the bearer token, never a tool argument.

Start with `get_mcp_context`. It returns contract version `2.0`, the effective
`FULL` or `PSEUDONYMIZED` access level, available academic operations, identity
capabilities, authoring limits, image-import capabilities, Markdown guide
version, and interaction rules. It contains no credentials, private server
mount roots, or exam content. Token administration remains a UI operation.

`get_exam_authoring_options` lists active grading scales and their ranges,
content and EH command types, and limits. With `courseId`, it also identifies
that active course's inherited grading scale. Existing course/pupil/exam lists
remain the discovery path for stable IDs; archived records require explicit
archive discovery and cannot receive new assignments or authoring changes.

## One academic API, two pupil identity representations

Both access modes use the same exam, content, EH, image, assignment and result
operations, with the same validation and result-derived locks. Pseudonymized
access is not read-only and is not complete anonymity.

Pupil projections contain a stable `id`, `displayName`, and
`identityRepresentation` (`FULL_NAME` or `INITIALS`), plus the relevant lifecycle
and class/course context. Real `name` and `surname` fields occur only with full
access. In pseudonymized mode, the display label is surname-first initials;
initials are not unique identifiers. Academic writes use pupil IDs. Resolve
ambiguous initials using class/course context and, if necessary, the ID; never
guess, request, reconstruct or search real names.

Pupil creation/import/renaming and real-name matching are full-access-only,
including indirect name-bearing course-roster operations. ID-based creation of
courses and assignment/removal of existing pupils remain available in both
modes. The server enforces permissions independently of advertised operations.

Responses and retries project current pupil identities under the current
access context; mutation receipts do not cache full-name responses. Generated
identity messages, errors, elicitation, filenames and metadata use the same
name boundary. User-authored Markdown, comments and image pixels are not
automatically redacted: users must remove or cover names before sharing those
contents with the AI. This includes task text and the exam closing text.
No OCR or automatic redaction is promised. Server-side backup creation returns
only its filename; no raw database download is offered.

## Tool boundaries and exam reads

| Tools | Responsibility |
| --- | --- |
| `get_mcp_context`, `get_markdown_guide`, `get_exam_authoring_options` | Discover capabilities, supported syntax and current limits. |
| `create_exam`, `duplicate_exam`, `update_exam`, `list_exams` | Create an exam shell, copy an academic design, and maintain title/date. |
| `get_exam_structure` | Discover the exam outline and stable content IDs without authored content. |
| `get_task`, `get_material`, `get_exam_footer` | Read only one requested pupil-facing task, material or exam closing text. |
| `get_task_requirements` | Read only one requested task's EH requirements and criteria. |
| `get_exam`, `apply_exam_content_changes` | Read and author pupil-facing Aufgaben, part-owned Material and exam closing text. |
| `get_level_of_expectations`, `apply_level_of_expectations_changes` | Read and author requirements against existing task IDs. |
| `import_material_image`, `get_material_image` | Import an image from a client-visible path or retrieve its stored pixels. |
| Existing course/exam roster tools | Assign or remove existing pupils under the established integrity rules. |
| `get_pupil_result`, `save_pupil_result` | Read or record one pupil's targeted assessment. |
| `create_database_backup` | Create a server-side backup without returning its contents. |

### Outline and focused reads

For a request about one task, material, closing text or task's EH, discover the
course/exam ID through the existing lists, then call `get_exam_structure` with
`examId`. Its ordered `parts`, part-owned `materials` and `categories`, and
category-owned `tasks` identify nodes by ID, title and order; task outlines also
include `maxPoints`. `closingTextPresent` indicates whether the exam has closing
text. The outline contains no authored Markdown, requirement/criterion text,
image metadata or bytes, pupil rosters or results.

Select the requested node from that outline and use its stable ID rather than
guessing a task number or treating its display order as an ID:

| Focused tool | Parameters | Returned content |
| --- | --- | --- |
| `get_task` | `examId`, `taskId` | The selected task's title, `descriptionMarkdown`, `maxPoints`, ID and order. |
| `get_material` | `examId`, `materialId` | The selected material's title, `descriptionMarkdown`, ID, order and referenced-image metadata. |
| `get_exam_footer` | `examId` | The exam's `footerMarkdown`; empty text means no closing text. |
| `get_task_requirements` | `examId`, `taskId` | The selected task's context and ordered EH requirements with their derived criteria. |

The outline and each focused response include the current exam-design
`revision` and `permissions`. These reads return only the selected authored
content with the identifiers/context needed to locate it; unrelated tasks,
materials, closing text, EH, pupil rosters and results are excluded. Task and
material IDs must belong to the supplied exam. Missing or foreign IDs are
rejected; a foreign ID cannot be used to read content from another exam.

`get_material` returns image metadata only. Retrieve requested stored pixels
separately with `get_material_image`, using that exam, material and image ID.

### Optional faithful PNG output

`get_task`, `get_material`, `get_exam_footer` and `get_task_requirements` accept
optional `includeFaithfulRender`, defaulting to `false`. Omission and `false`
retain the ordinary structured-content response without browser rendering or
image bytes. `get_exam_structure` remains a content-free outline and has no
render option.

When the user asks to see the rendered content, set `includeFaithfulRender` to
`true` on the selected focused read. TopTeacher retains the same original
Markdown, identifiers, revision and permissions, and adds native MCP
`image/png` content on success. The client can display that image inline. This
uses TopTeacher's existing Markdown viewer and its applicable Material and EH
extensions, including managed images, criteria, alerts, footnotes and literal
numbered source lines. It does not introduce a second Markdown dialect or
promise pupil exam-sheet layout.

The optional `faithfulRender` metadata is absent when rendering was not
requested. On success it contains `status: "rendered"`, `mimeType:
"image/png"`, `pixelWidth`, `pixelHeight` and `byteSize`. The native PNG follows
the JSON text content; the original structured content remains available.
When no image can be produced, the metadata contains `status: "unavailable"`
and `message: "Faithful PNG rendering is unavailable. The original Markdown
is still available."` without image content. A rendering failure,
missing installed browser, timeout or capacity limit preserves the structured
Markdown response without a PNG; clients should show the text and that
message. Exam lookup and node-ownership failures remain tool errors and do not
render content from another exam.

The PNG has a width of 960 pixels and a maximum height of 12000 pixels, with an
8 MiB output limit. A rendered height or PNG size exceeding these limits is
reported as unavailable rather than silently truncated. Rendering is bounded
to approximately 30 seconds per request and uses a bounded queue. The text,
revision and referenced
image snapshot are captured consistently before rendering; browser work does
not hold the exam-design lock. PNG bytes are returned in memory rather than
saved as durable exports.

Rendering requires an installed browser. The optional
`tt.mcp.render.browser-executable` selects an existing browser path;
`tt.mcp.render.browser-channel` defaults to `chrome`.
`tt.mcp.render.timeout-seconds` defaults to 30 and accepts values from 5 to 60.
TopTeacher does not download a browser automatically. Browser setup and
component reuse are described in
[the renderer implementation notes](../tech/MarkdownExtensions.md#mcp-faithful-rendering).

### Complete exam and EH reads

`get_exam` and `get_level_of_expectations` retain their existing complete-read
contracts for requests that need the whole definition or EH. They are not
prerequisites for focused reads.

`get_exam` returns course and exam metadata, `revision`, grading-scale context,
`permissions`, point summary, structured readiness `diagnostics`, ordered
`parts`, and `footerMarkdown`. Each part contains separate ordered `materials`
and `categories`; categories contain ordered tasks. A task has its title,
`descriptionMarkdown`, explicit integer `maxPoints`, ID and order. Material
includes image reference metadata, not image bytes.

All Aufgaben content is pupil-facing. The closing text is optional content
outside every part, not an EH requirement or a repeated page footer. See
[the exam content model](LevelOfExpectations.md#exam-closing-text).
`get_exam` does not include pupil rosters, requirement text, pupil results or
Base64 images. These have their own explicit reads.

`get_level_of_expectations` returns the same design revision, permissions,
point/readiness context and tasks with attached requirements and criteria.
Task context is read-only there; it is not a second editable tree. Criteria
remain derived from requirement Markdown, not independently authored objects.

## Creating and maintaining the exam shell

`create_exam` takes `courseId`, `title` (at most 100 characters), ISO `date`,
and a unique course-scoped `requestId`.
Optional `gradingScaleId` selects an active scale; omission inherits the course
scale. The scale and course relationship cannot later be changed by
`update_exam`. Optional `originalExamId` creates a makeup exam in the same
course, subject to the existing main/makeup date rules.

Optional `initialPupilIds` controls the initial roster. Omission assigns all
active course pupils for a main exam, or active course pupils not assigned to
the original exam for a makeup exam. An explicit empty list selects no pupils.
An explicit nonempty list must contain active pupils already in that course.
Creating academic content never creates pupil identities. Exam creation and
its selected roster are atomic.

`update_exam` takes `examId`, current `expectedRevision`, unique `requestId`,
and a new title, date, or both. Omitted metadata fields stay unchanged. It does
not change the course, grading scale, makeup relationship, assignments, content,
EH or results. The existing date and duplicate-title rules remain in force.

`duplicate_exam` takes `sourceExamId`, the source's current `expectedRevision`,
new `title` and ISO `date`, required `initialPupilIds`, and a unique
course-scoped `requestId`. It creates a new main exam in the same active course
using the source grading scale. Its design contains copied parts, categories,
tasks, ordered material, shared immutable image references, closing text and
EH, with new node/criterion IDs. It never copies results, pupil assignments,
or the source's makeup/original-exam relationship. A source with results may
be copied. Pupil selection is explicit: supply selected active course pupil
IDs or an empty list for none; omission is not a default selection.

Creation/duplication retries retain the originally created exam ID and return
its current metadata/identity projection. Their receipts are scoped to the
course; the same creation key cannot be reused for a different request.

## Typed content and EH batches

`apply_exam_content_changes` takes `examId`, `expectedRevision`, `requestId`,
and ordered `commands`. Optional `requirementChanges` allows explicitly
coordinated EH adjustments in that same transaction, such as reducing
requirement allocations while lowering a task budget. The usual EH workflow
uses `apply_level_of_expectations_changes` with its own `commands` list.

| Content command family | Fields |
| --- | --- |
| `CREATE_PART` | Required `title`; optional `clientKey`. |
| `CREATE_CATEGORY`, `CREATE_MATERIAL` | Required parent reference and `title`; optional `clientKey`, `markdown`. |
| `CREATE_TASK` | Required category parent reference, `title`, `maxPoints`; optional `clientKey`, `markdown`. |
| `UPDATE_PART` | `id` and new `title`. |
| `UPDATE_CATEGORY`, `UPDATE_MATERIAL` | `id` and at least one of `title`, `markdown`. |
| `UPDATE_TASK` | `id` and at least one of `title`, `markdown`, `maxPoints`. |
| `DELETE_PART`, `DELETE_CATEGORY`, `DELETE_TASK`, `DELETE_MATERIAL` | `id`; `acknowledgeDeletion: true` when descendants will be removed. |
| `REORDER_PARTS` | Complete `order` list. |
| `REORDER_CATEGORIES`, `REORDER_TASKS`, `REORDER_MATERIALS` | Parent reference and complete `order` list. |
| `SET_FOOTER` | Required `markdown`; empty text removes the closing text. |

A parent reference supplies exactly one `parentId` or `parentKey`. A
request-local `clientKey` identifies a node created earlier in the same batch;
keys are unique and match `[A-Za-z][A-Za-z0-9_-]{0,63}`. They are not durable
node identities. The response maps them to generated IDs.

For example, initial content can be created in one call without supplying EH:

```json
{
  "examId": 42,
  "expectedRevision": 0,
  "requestId": "exam-42-content-1",
  "commands": [
    {"type": "CREATE_PART", "clientKey": "partA", "title": "Klausurteil A"},
    {"type": "CREATE_CATEGORY", "clientKey": "content", "parentKey": "partA", "title": "Inhaltliche Leistung"},
    {"type": "CREATE_TASK", "clientKey": "task1", "parentKey": "content", "title": "Aufgabe 1", "markdown": "Analysiere die Quelle.", "maxPoints": 6},
    {"type": "CREATE_MATERIAL", "clientKey": "source1", "parentKey": "partA", "title": "Quelle 1", "markdown": "> [!NUMBERED]\n> Erste Quellenzeile.\n> Zweite Quellenzeile."},
    {"type": "SET_FOOTER", "markdown": "**Viel Erfolg!**"}
  ]
}
```

IDs and revisions in examples are illustrative; use values returned by this
installation. Later batches may add only one new part/subtree or one task,
without resending or replacing existing content. Adding a task never creates
an EH requirement.

EH commands are `CREATE_REQUIREMENT`, `UPDATE_REQUIREMENT`,
`DELETE_REQUIREMENT`, and `REORDER_REQUIREMENTS`. Creation requires `maxPoints`
and a task reference: exactly one `taskId` or `taskKey`. A task key can refer
to a task explicitly created in the content portion of the same request.
Creation accepts optional `clientKey`, `markdown` and `bonus` (default false).
Updates use `id` and one or more of `markdown`, `maxPoints`, `bonus`; they
cannot reparent a requirement. Deletion uses `id` and acknowledges removed
criteria when needed. Reordering supplies the task reference and complete
requirement order.

Null/omitted update fields mean unchanged; an empty Markdown string explicitly
clears content. Omitting nodes never deletes them. Unsupported fields for a
command are rejected rather than ignored. Task/requirement maximum points are
whole points; criterion half-points are represented by the existing Markdown
syntax and point units.

### Ordering, deletion and stable identities

An `order` list contains objects with exactly one `id` or earlier `clientKey`.
Commands run in their listed order. The order list must include every sibling
present when that command runs exactly once and no foreign nodes; delete
unwanted siblings before reordering the survivors.
Ordering is relative to the existing parent; it never silently moves a task
between categories or another node between parents. New nodes are appended
in command order unless an explicit reorder is supplied. Persisted IDs and
attached EH remain stable during editing and reordering.

Deletion is explicit and cascading. A part can remove its categories, tasks,
materials, requirements and criteria; deleting a category/task removes its EH
descendants. The server reports descendant counts and requires
`acknowledgeDeletion: true` when necessary. It does not delete unrelated
content or the exam-level closing text. Managed bytes are removed only after
their last material reference disappears.

### Revisions, retries and mutation responses

Each batch validates and commits atomically. An error rolls back every change,
including inserted IDs, image references, design revision advances and retry
receipt. The shared exam lock serializes UI/MCP design writes with result
entry. Editors and MCP mutations compare the revision of their loaded snapshot;
a stale request returns a conflict instead of overwriting newer work.

Use a new `requestId` for a new logical mutation, and reuse the exact original
request—including its original `expectedRevision`—only when retrying it.
Request IDs contain 1–100 letters, digits, dots, underscores, colons or hyphens.
Retry receipts are checked before the original revision, so a successful
request can be retried after subsequent changes without applying it again.
Reusing a key for a different request is rejected.

Authoring responses contain created-key/ID mappings, deletion impacts,
diagnostics and canonical affected nodes/orders read under the same lock.
`revision` identifies the current returned content; `mutationRevision`
identifies the original committed mutation; `replayed` distinguishes a retry.
A replay returns stable creation IDs but refreshed current content, not cached
old text. Nodes deleted since the original request are omitted from current
changes. Receipts store neutral IDs/counts, not pupil-name or authored-content
responses.

### Validation and result-derived locks

Incomplete task/EH allocation is a saveable authoring stage and produces
diagnostics. Final-batch over-allocation is rejected: regular requirement maxima
must not exceed their task's budget, criterion declarations must not exceed their
requirement budget, and task totals must not exceed the grading-scale maximum.
Regular requirement maxima must equal task budgets and the task total must
match the scale before result entry. The read model's `points.validDesign`
checks this scale/task/regular-requirement equality. Criterion under-allocation
produces a warning but does not by itself block result entry: the uncovered
portion can be assessed through free points. Bonus requirements are separate; see
[the authoritative points model](LevelOfExpectations.md#authoritative-budgets-and-distributions).

While results exist—even an initial zero-point result—Aufgaben, Material,
managed image changes and the closing text are locked. EH permits only the
established result-preserving text/label corrections: task ownership, order,
maximum, bonus status, criterion keys and criterion point values remain fixed.
No tool bypasses that boundary. Correction mode is derived from currently
persisted results, not a newly introduced permanent lifecycle/publish flag.

`get_exam.permissions` reports editability separately from token capabilities;
`diagnostics` reports readiness separately from editability. No separate
validate/preview/render tool or pupil-facing exam-sheet export is introduced.

## Markdown dialect discovery

Call `get_markdown_guide` before authoring unfamiliar TT syntax. Optional
`context` is `ALL` (default), `MATERIAL`, `TASK`, `CATEGORY`, `EXAM_FOOTER`, or
`REQUIREMENT`. The bundled guide has version `1.0`, complete examples and a
capability summary, identical in both access modes. Reading is recommended;
valid writes are not blocked because a client has not read the guide.

The guide follows [Markdown.md](Markdown.md) and the
[Material/criterion specification](LevelOfExpectations.md). It covers ordinary
Markdown, five alerts, footnotes, literal `> [!NUMBERED]` source lines,
Material-only `img:` image declarations and EH-only `crit:` criterion links.
Server validation rejects misplaced criteria and malformed managed-image
declarations independently of the guide. Placement checks distinguish literal
code examples from active Markdown links. EH criterion declarations are still
parsed from their existing source syntax: do not include copyable `crit:`
declarations as literal examples inside a requirement, where they can be
counted as criteria.

Numbered source blocks use deliberate source breaks: each non-empty quoted
line counts, blank lines do not, each block restarts at 1, and displayed labels
occur at 1 and multiples of 5. Lines do not wrap; numbered blocks cannot be
nested. Footnote definitions stay outside them. Numbered blocks and footnotes
render on other Markdown surfaces although their toolbar commands are enabled
only in Material. Preview width does not guarantee future print fit.

## Material image exchange

`import_material_image` takes `examId`, `materialId`, absolute `sourcePath`,
nonblank `altText`, optional `widthPercent` (1–100), `expectedRevision`, and
`requestId`. Local installation resolves paths directly; remote installation
uses configured shares. See [file access](SystemSettings.md#file-access).
Private server roots are not disclosed by MCP.

Where supported, file reads use secure directory handles and reject symlinks
during traversal. Other providers use best-effort canonical-path and scope
rechecks around the bounded read, not a filesystem sandbox guarantee. Local
filesystems and mounted shares must be trusted against malicious concurrent
path replacement.

The import normalizes the PNG/JPEG, stores its immutable bytes and appends its
Markdown reference to the selected material in one transaction. Its response
returns image metadata and current canonical material. Use an `UPDATE_MATERIAL`
command to reposition the returned declaration if needed; do not invent image
IDs or put private paths/Base64 into Markdown. Omitted width uses automatic
sizing. Metadata deduplication and image processing match the Material UI.
Only actual rendered Markdown image declarations grant managed-image access.
Plain text, ordinary links, inline/fenced code and image alternative-text
examples do not create managed references; image-ID rewriting touches actual
destinations, not incidental IDs in captions or code.

An identical retry checks its receipt before reading the path again; a changed
or unavailable source file does not cause a second import. The stable original
image metadata is retained, while current material is refreshed; material
deleted since the original import is omitted from the current response.

`get_material_image` takes `examId`, `materialId`, and `imageId`; the image must
be referenced by that material in that exam. It returns native MCP image
content with the stored MIME type and Base64-encoded stored bytes, not an image
URL or a Base64 field in the exam definition:

```json
{
  "content": [{"type": "image", "mimeType": "image/png", "data": "<base64>"}]
}
```

Both modes receive the same original stored pixels. Image content is not
automatically pseudonymized. Retrieve images on demand rather than inflating
every exam/EH read with their bytes.

## Recording results

`save_pupil_result` takes `examId`, an active assigned `pupilId`, current
`expectedRevision`, unique `requestId`, and 1–500 targeted `assessments`.
Each assessment names `requirementId` and one or more of `freePointUnits`,
`criterionAwards`, and `comment`. Awards specify existing `criterionId` and
`pointUnits`. Two units equal one point. Free points specify their absolute
component, not a delta; omitted awards/fields/requirements preserve saved
values, and an empty comment clears that comment.

The request must fit each requirement's maximum and pass complete exam
readiness/assignment checks before any assessment is saved. Its first result
locks pupil-facing content. The result response is freshly projected under
the current token mode and follows the same current/original revision and
retry distinction. Half-up rounding occurs per requirement, then task results
are capped at task maxima; bonus from one task cannot compensate another task.

## Published limits and deferred work

The runtime's context/options response is the discoverable source for limits:

- 500 authoring commands per atomic batch;
- 200 characters per node title; 50,000 per Markdown field;
- 100 characters per exam title and 200 initial pupil IDs;
- 100,000 maximum points per task/requirement; nonnegative whole values;
- 5,000 total design nodes, including criteria, and 2,000,000 Markdown
  characters per request/design;
- 20 parts; 50 categories and 100 materials per part; 100 tasks per category;
  200 requirements per task and 1,000 requirements per exam;
- image input: 10 MiB, 40 decoded megapixels; normalized longest side 3,000
  pixels; client path 4,096 characters; alternative text 1,000 characters.

New pupil exam-sheet printing/export remains deferred. Its eventual order is
materials before categories/tasks within each part, a new page for subsequent
parts, and one closing text after all parts; existing EH/result PDFs are
separate. Cross-source footnote composition and print-specific layout are also
deferred. No unrestricted database dispatcher, token-administration tools,
raw database transmission or standalone image-upload protocol is added.
