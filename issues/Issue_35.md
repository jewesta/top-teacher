# Issue 35: Add exam tasks

## Intent

Extend TopTeacher from managing only results and the level of expectations on
which they are based to managing the actual written exam and its tasks as well.

The exam workflow is deliberately represented by the exam tabs from left to
right:

1. create the exam;
2. assign pupils;
3. create the exam tasks;
4. add the source material;
5. define the level of expectations (EH);
6. print the tasks and material and write the exam in the real world;
7. record the results.

Defining the tasks before the EH reflects the real teaching workflow.

## Existing structure

The EH already uses the hierarchy:

`Part > Category > Task > Requirement`

This hierarchy also describes the exam itself. The actual task content belongs
to the `Task` level, while the requirements below a task belong only to its EH.
Task content should be authored as Markdown with the existing Markdown editor.

## Design direction

- Add an `Aufgaben` tab immediately before the new `Material` tab.
- Add the dedicated `Material` tab immediately before `EH`.
- Reuse one shared hierarchy and the existing EH editing concepts rather than
  introduce a second, independently maintained exam-task tree.
- Treat `Aufgaben` and `EH` as two views of the same structure with different
  visibility and editing concerns:
  - `Aufgaben` shows parts, categories, and tasks, including the Markdown task
    content, but not requirements.
  - `EH` shows the shared hierarchy as read-only context and edits only the
    requirements below tasks, including their points and criteria.
- Preserve the intentional tab order because it communicates the workflow.
- Keep the two views synchronized without requiring a full page reload or
  losing expansion, selection, or dirty editing state unnecessarily.

The preferred implementation should share the underlying data and reusable UI
building blocks. It does not necessarily have to render one physical editor
instance in both tabs; separate configured views may be safer if they need
independent view state or dirty-state handling.

## Decisions

- `Aufgaben` is the sole authority for the hierarchy down to and including the
  task level. Parts, categories, and tasks can be created, changed, reordered,
  and removed only there.
- The levels from part through task are always read-only in `EH`, even before
  results exist. EH editing begins at the requirement level. This reflects the
  workflow and removes structural controls from the already dense EH view.
- A task created in `Aufgaben` does not automatically create a requirement.
  Requirements are created as part of the subsequent EH workflow.
- As soon as the first result is recorded for an exam, the entire `Aufgaben`
  view becomes read-only. This existing correction-mode boundary also serves
  as the indication that the real-world exam has been written.
- `Material` becomes read-only at the same first-result boundary as
  `Aufgaben`.
- In the locked `Aufgaben` and `Material` views, Markdown content is rendered
  for reading instead of leaving disabled editor controls on screen. This
  matches the read-only category and task context already used in EH.
- After results exist, EH uses its existing safeguards. Only corrections to the
  actual requirement content that preserve result-bearing semantics remain
  possible; task assignment, ordering, points, bonus status, criterion
  identities, and criterion point values remain locked.
- The new pupil-facing exam-sheet printing and export are deferred until the
  `Aufgaben` and `Material` tabs are fully functional. No separate teacher
  version of that future exam sheet is currently needed. This does not affect
  the existing pupil and teacher EH result PDFs.
- Material footnotes and literal source-line numbering are now implemented.
  Cross-editor footnote collection and pupil-facing print layout remain deferred;
  see the implementation status below.
- Material supports managed PNG/JPEG images through the image workflow recorded
  below and specified in the
  [operational documentation](../doc/ops/LevelOfExpectations.md#managed-images).
- MCP-oriented image exchange starts with two reusable prerequisites: mapped
  access to client-visible shared-folder paths and conversion of stored
  material images to native Base64 MCP image content. See
  [system settings](../doc/ops/SystemSettings.md).
- MCP access tokens are maintained in `MCP > Sicherheit` and persisted
  in the database with full or pseudonymized access. Restricted access protects
  pupil names, prevents pupil creation/renaming and real-name probing, and
  retains other operations using pupil IDs, initials, and class context.
  User-authored content follows the agreed name-removal protocol. See
  [MCP access and pupil pseudonymization](../doc/ops/SystemSettings.md#mcp-access-and-pupil-pseudonymization).
- `Aufgaben` also owns an optional exam-level Markdown closing text outside the
  tree. It appears once after all parts in the future pupil exam sheet, not as
  a repeated page footer; see
  [exam closing text](../doc/ops/LevelOfExpectations.md#exam-closing-text).
- Remove the `Notizen` tab and the complete exam-note-section feature. It was
  accidental legacy from the original exam template and must not be repurposed
  as the implementation of `Material`.
- The existing `Loe` name remains accepted for the objects and persistence
  structures representing the general exam hierarchy. A broad rename is not
  part of this issue.

## Shared tree editor

`Aufgaben`, `Material`, and `EH` must use the same underlying tree rendering
and editor infrastructure. This is a maintainability requirement, not only a
request for similar styling. The implementation must avoid three independently
evolving copies that can drift during later refactoring.

The three tabs configure the shared tree differently:

| Tab | Tree | Editable levels |
| --- | --- | --- |
| `Aufgaben` | Part > Category > Task | Part, category, and task |
| `Material` | Part > Material | Material only; part is read-only |
| `EH` | Part > Category > Task > Requirement | Requirement only; all ancestors are read-only |

The shared infrastructure should own the common designer shell, nested section
rendering, summaries, indentation and styling, collapse state behavior,
breadcrumbs, save/discard and dirty-state handling, action placement, lock
presentation, and accessibility behavior. Tab-specific configuration or
composed node editors supply the visible levels, editable fields, commands,
point information, and persistence callbacks.

Section headers use two explicit, adjacent collapse controls throughout
`Aufgaben`, `Material`, and `EH`: a single chevron toggles only that section,
while a double chevron toggles the section and its complete subtree. Leaf
sections show only the single chevron. Clicking elsewhere in a header does not
expand or collapse it; this avoids accidental changes while interacting with
titles, badges, or text. The global whole-tree control remains in the designer
toolbar. A subtree or whole-tree control offers expansion until every section
in its scope is expanded; only a completely expanded scope switches the control
to collapse mode.

Each tab still has its own configured editor instance and its own transient
view state. Sharing implementation must not mean moving one live Vaadin
component between tabs or sharing dirty/collapse state accidentally.

## Material

`Material` is the neutral term for source texts and other sources handed out
with the tasks. It applies equally to language texts, historical sources,
diagrams, images, data, and similar subject-specific material.

- An exam can contain multiple materials.
- Every material belongs to exactly one `Klausurteil` (`LoePart`).
- Materials within a part have an explicit order.
- A material has a title and Markdown content.
- Material remains separate from the task hierarchy; tasks refer to material
  through the wording of the task rather than owning or duplicating it.
- The future pupil printout places each part's material on separate pages
  before its categories and tasks, with a new page for each subsequent part.
  All Aufgaben content is pupil-facing. The optional exam closing text follows
  all parts; see the
  [authoritative export scope](../doc/ops/LevelOfExpectationsPdfExport.md#deferred-exam-sheet-export).

The `Material` view uses the shared tree editor. Materials appear as
collapsible, titled Markdown children beneath read-only `Klausurteil` headings,
with save, add, reorder, and delete actions. Adding a material within a group
assigns it to that part, and reordering is scoped to siblings in the same part.

## Points

The task author assigns the available points to each task before defining its
EH. The requirements beneath that task subsequently distribute those assigned
points.

- `LoeTask` owns an explicit maximum-point value edited in `Aufgaben`.
- The maximum point totals for categories, parts, and the exam are calculated
  from their tasks.
- EH continues to assign maximum points to individual requirements.
- The sum of a task's non-bonus requirement maxima must equal the task's
  maximum points.
- Bonus requirement maxima are separate and do not contribute to that equality.
- A pupil's effective result for a task is capped at the task maximum:

  `min(task maximum, achieved regular points + achieved bonus points)`

- The existing requirement-local half-up rounding happens before those rounded
  requirement results are aggregated and capped at the task boundary. No half
  point leaves its requirement.
- The effective result for the exam is the sum of the individually capped task
  results. Bonus points from one task cannot compensate for missing points in
  another task.
- EH badges show the regular points currently assigned to requirements;
  existing inline validation messages describe missing or excess allocation
  against the task budget while the EH is incomplete.
- A temporary mismatch must be allowed while the EH is being authored; an
  aggregate database constraint cannot represent that editing workflow.
- Results must not be recorded until every task's requirement-point
  distribution is valid.
- Once results exist, task points are locked with `Aufgaben`, while requirement
  points remain protected by the existing EH correction-mode safeguards.

Before this issue, all higher-level point totals were derived upward from
requirements and `eh_task` had no point column. This issue deliberately
introduces a stored task point budget because task points and requirement
points represent two separate workflow stages. Existing task point values are
backfilled from their current non-bonus requirement totals.

The existing bonus cap is applied once at exam level. It must move to the task
boundary throughout result entry, grading, evaluation, existing EH/result
exports, and MCP result summaries.

## Initial implications

- Previously, `LoeTask` contained only its title and sort order. It gains a
  Markdown field and a maximum-point field for the actual task, with
  corresponding database columns and repository mappings.
- A material domain type and persistence table are needed with a required
  `LoePart` reference, title, Markdown content, and per-part sort order.
- Remove `ExamNoteSection`, `ExamNotesEditor`, the `exam_note_section` table,
  repository operations, duplication behavior, EH export content and template,
  MCP fields, reset handling, styles, and tests belonging exclusively to the
  Notes feature. Existing notes are not converted into material because their
  semantics and ownership differ.
- Schema, base data, demo data, duplication, and automated tests must remain in
  sync with the extended task model.
- Structural edits made in `Aufgaben` affect the hierarchy shown by `EH` and
  therefore must immediately become visible there.
- Dirty state must be coordinated when switching between `Aufgaben` and `EH`.
  One view must not silently overwrite or discard unsaved changes made in the
  other.
- The repository must enforce the ownership and first-result locks; hiding UI
  controls alone is not sufficient protection.
- Exam duplication should copy the task Markdown together with the shared
  hierarchy, material, and EH.
- Removing a part also removes its assigned material, so the existing part
  deletion confirmation must make that consequence clear.
- A printable exam artifact is a later part of the workflow but should inform
  the model now. Task content should not be stored only as presentation state.
- Deep links, refresh propagation, accessibility labels, and tab restoration
  need to recognize both additional exam tabs.

## Deferred follow-up work

- The first implementation is limited to Markdown authoring for task and
  material content.
- Exam-sheet printing and print-specific layout such as page breaks and answer
  space are deferred.
- Managed image support is implemented as described below; generic document
  attachments remain outside that scope.
- Material footnotes and source-text line numbering are implemented; composed
  footnote scopes and numbered-text print layout remain deferred.

## Reimplementation status

The reactor and packaged application version for this branch is `1.3.0`.

The first implementation pass reached the scope described below and is
preserved on `codex/issue35-pre-rebuild` at commit `2d484a2`. On 10 October
2026, `issues/Issue_35` was deliberately restarted from the current `main`
baseline (`9aa2100`) so that Issue 32's half-point model, point aggregation,
and status tray remain authoritative instead of being reconstructed through a
large conflict resolution.

The Issue 35 work was planned for these reviewable stages:

1. remove the accidental Notes feature;
2. add task and material domain/persistence support;
3. add the shared `Aufgaben`/`Material`/EH tree and enforce edit ownership;
4. integrate task budgets and per-task bonus caps with the Issue 32 validation,
   results, exports, and MCP surfaces;
5. restore rendered-only Markdown in locked views;
6. add the explicit self/subtree collapse controls and their mixed-state
   behavior;
7. retain the concise point-distribution label without the redundant
   `passend` text.

Current progress: the restart foundation and stage 1 are committed at
`301ec3c`; the combined implementation and its follow-up refinements are
committed through `8b70361`, with subsequent UI and Markdown follow-ups committed
through `358a22c`. The stages were reapplied together because their
domain, validation, result, export, MCP, shared-rendering, and lock behavior are
tightly coupled. The running application has been visually checked for the
workflow tab order, editable Aufgaben/Material views, read-only EH ancestors,
explicit collapse controls, concise point-distribution labels, half-point
handling, and the preserved Issue 32 status tray.

The combined review pass is intended to provide the following end state:

- `Aufgaben`, `Material`, and `EH` are separate tabs in workflow order and use
  the same tree-section, designer, collapse, breadcrumb, save/dirty, and lock
  infrastructure.
- `Aufgaben` owns editing for parts, categories, task Markdown, and explicit
  task maximum points.
- The fixed `Abschlusstext` Markdown block below the Aufgaben tree is persisted
  independently on the exam, copied with its design, and covered by save,
  confirmed discard, dirty-state guards, and the first-result lock. Structural
  tree actions cannot discard pending content; see
  [the operational specification](../doc/ops/LevelOfExpectations.md#exam-closing-text).
- Task maximum points use the shared compact minus/value/plus control with a
  permanent background in the task summary's point column; see the
  [operational specification](../doc/ops/LevelOfExpectations.md#task).
- EH requirement maxima use the same unlabeled control; all three authoring
  views share the Markdown right inset and aligned points column. See
  [requirements](../doc/ops/LevelOfExpectations.md#requirement) and
  [shared layout behavior](../doc/ops/LevelOfExpectations.md#editing-ownership-and-shared-tree-behavior).
- `Ergebnisse` uses the same right inset and points-column alignment; see
  [result layout](../doc/ops/LevelOfExpectationsResults.md#quick-marking-column).
- Application toolbars adapt to the number of visible elements: a lone action
  is a labeled secondary button; multi-element bars use transparent icons.
  Filters, selectors, and disabled actions count; hidden elements do not.
  Contextual labeled additions keep their button shape in both modes, without
  primary highlighting. All action-bar controls, including nested tree
  add/reorder/delete rows, use normal rather than
  compact sizing, with download menu triggers matching the other icons.
  All action bars share the tree rows' compact horizontal spacing without extra
  icon padding, including those with quick filters; dialog-opening actions
  consistently use an ellipsis.
  Form actions are at the top of each tab;
  `Neu` and `Anlegen` follow the adaptive rule; list bars place `Schnellfilter`
  first and `Neu` last, after any other filters or selectors. Quick filters
  consistently use `Suchbegriff`; contextual tree additions retain their
  labels. Used grading scales show a
  lock with the reason as a tooltip on their editor tab instead of a form
  message; save and archive stay visible but disabled while locked.
  `Zurücksetzen` remains an exception. See
  [application action bars and locks](../doc/ops/ApplicationActions.md).
  Normal record editing has no tab pencil; new-item plus, locks, selection
  counts, and accessible mode descriptions remain. See
  [editor-tab indicators](../doc/ops/ApplicationActions.md#editor-tab-indicators).
  Dropdown selections use dots to distinguish selection from completion;
  see [selection indicators](../doc/ops/ApplicationActions.md#selection-indicators).
  Aufgaben task and EH requirement action rows sit above their Markdown;
  task sections match EH requirements' bottom inset. See
  [shared layout behavior](../doc/ops/LevelOfExpectations.md#editing-ownership-and-shared-tree-behavior).
  Discarding unsaved authoring work now requires confirmation, matching
  Ergebnisse; see
  [shared toolbar and discard behavior](../doc/ops/LevelOfExpectations.md#editing-ownership-and-shared-tree-behavior).
- `Material` stores ordered title/Markdown entries under exactly one read-only
  exam part.
- EH shows part, category, task Markdown, and task points as read-only context;
  only requirements remain editable there.
- The repository enforces the first-result lock for tasks and material, the EH
  correction safeguards, and valid task/requirement point distribution before
  result entry.
- Result totals, evaluation, exports, and MCP summaries cap bonus points at the
  individual task boundary after requirement-local rounding.
- Existing task point budgets are migrated from their regular requirements,
  while duplication copies task content, points, material, and EH data.
- Locked task, category, and material Markdown is shown as rendered content;
  no Markdown editing controls remain in the component tree.
- Section headers use the adjacent self/subtree controls, header clicks no
  longer toggle a section, and mixed scopes continue to offer expansion until
  every child is expanded.
- Point badges in Aufgaben, EH, and Ergebnisse now show centered plain numbers
  without labels, ratios, or bonus brackets, with a shared narrower blue badge
  and the existing right alignment. Part percentage chips share the same blue
  styling. EH totals show assigned regular requirement
  points; Ergebnisse totals use individually capped task scores. Whole-point
  controls share badge width and a permanent blue background without a hover
  tint. Ergebnisse uses a two-column checkbox/status and points layout, with
  lighter blue badge-width controls and the existing translucent red highlight
  across both columns only for criteria linked to text, not freely awarded points.
  Empty status slots remain empty until the deferred status-icon work.
  The checkbox/status slot is compact with a small breathing margin;
  the points width and right edge remain unchanged. Labels use `n von m Pkt.`
  and `Frei vergebene Pkt.` with full tooltip/accessibility wording. Inline
  criteria place the checkbox before the points pill. New badge warning/error
  styling and PDF layout changes are deferred. See
  [point presentation](../doc/ops/LevelOfExpectations.md#authoritative-budgets-and-distributions)
  and [result layout](../doc/ops/LevelOfExpectationsResults.md#quick-marking-column).
  Missing/excess task allocations use the same
  inline yellow/red message bars as requirement validation instead of trailing
  `offen`/`zu viel` text. Task messages explicitly ask for requirement-point
  allocation; location labels appear only in Status, not in the task header. See
  [validation presentation](../doc/ops/LevelOfExpectations.md#validation-presentation).
- The accidental legacy Notes feature has been removed end-to-end, including
  its persistence, export, and MCP surfaces.

## Criterion URL declarations

- Criteria now use `crit:<key>[?points=<value>]`, retaining stable keys, half-point
  values, and the omitted one-point default. The editor generates the new form,
  and previews, validation, results, PDFs, MCP descriptions, and demo data agree
  on it. Existing stored requirement declarations are converted once without
  changing criterion identities or scores. See
  [criterion syntax](../doc/ops/LevelOfExpectations.md#tag-syntax).
- The editor remains the primary interface; users do not need to learn URI
  syntax. This change prepares the shared declaration convention for images.

## Managed image support

### Scope and editor workflow

- Images are supported only in `Material`, not in Aufgaben, EH, or other
  Markdown fields. Material remains subject-neutral rather than a special
  language-teaching source-text editor.
- Keep Markdown as the authoring format. A Material-only image toolbar icon
  opens a dialog for choosing an image and entering its description/alternative
  text, with a rendering-width option. Confirmation uploads the image and
  inserts its Markdown declaration at the cursor.
- Dropping one image directly into editable Material opens the same dialog with
  the dropped file prefilled and not replaceable.
- All Markdown editors must intercept file drops, including editors without
  image support. Unsupported drops show a clear message instead of allowing
  the browser to navigate to the file or replace the current page. Those other
  editors do not receive an image toolbar icon.
- Image insertion must respect Material's existing first-result lock.

### Storage and limits

- Store image data in the database, rather than relying on external documents
  or images on the teacher's filesystem.
- Accept PNG and JPEG only. Uploads are limited to 10 MiB and 40 decoded
  megapixels. Images above 3,000 pixels on their longest side are reduced
  proportionally; JPEGs are normalized at 90% quality, while PNG transparency
  is preserved.
- Use short opaque IDs containing lowercase letters and digits, such as
  `4n8k2p7c`, rather than long technical-looking identifiers. IDs contain eight
  random lowercase letters/digits. Database constraints enforce their
  uniqueness, and saving retries with a new ID if one collides. SHA-256 content
  hashes provide internal deduplication.

### Markdown convention and rendering

Use ordinary Markdown image syntax with a TT-interpreted `img:` target and named
URL query parameters, matching the new criterion convention:

```markdown
[Nennt drei Präsidenten der USA](crit:1?points=3)
![Diagramm 1](img:4n8k2p7c?width=75)
```

- The short ID identifies the stored image; the description is the Markdown
  alternative text. Use the friendly `img:` scheme without a `tt-img:` prefix.
- `width=75` means 75 percent of the available content width. No unit means
  percentage; omitted width means automatic sizing. Preserve the image's aspect
  ratio and fit it within the available preview/rendering area.
- Explicit units such as `width=7.5cm` may be introduced later with print
  support; they are not part of the initial implementation.
- Attribute syntax such as `![](image.jpg){width=75%}` was considered. The agreed
  direction is instead URL query parameters for both criteria and images,
  avoiding two different extension conventions.
- Showing images in the Markdown preview is feasible. TT must resolve managed
  image references for its renderer; `img:` and `crit:` are application
  declarations, not browser navigation protocols.
- The future pupil printout is the benchmark for sizing behavior. Width should
  be relative to available page content, not a fixed editor-pane pixel width.
  Print generation itself remains a later step.

### Implementation status

The criterion change, Material footnotes, literal line numbering, Markdown
alerts, and managed-image follow-up are
implemented. Material now has the image command and shared upload dialog,
direct file-drop handling, cursor insertion, content-relative width choices,
session-scoped preview and inspection rendering, immutable deduplicated
database storage, reference-aware cleanup, duplication reuse, save/discard
behavior, and repository-enforced first-result locking. Existing declarations
can be reopened at the cursor, edited with their description and width
prefilled, and replaced through `Bild bearbeiten` / `Übernehmen`; the insert
dialog presents its dashed drop target, `oder`, and file chooser as separate
horizontal elements. Only the dashed area accepts dragged files. All other
editable and rendered Markdown surfaces safely intercept file drops and explain
the supported Material workflow.

Editable Material Markdown also enables the shared editor's optional footnote
command. The dialog inserts a numeric reference at the remembered cursor and
appends its definition to the Material source; preview and locked rendering use
the renderer's local footnote list. References render as plain superscript
numbers there, without inactive in-preview links or return-arrow controls;
ordinary Markdown links remain active. See the
[operational specification](../doc/ops/LevelOfExpectations.md#footnotes) for the
settled behavior.

Literal line numbering uses the agreed `> [!NUMBERED]` blockquote convention,
preserves authored line breaks including empty lines, and clips excess text
without deleting it. Only line 1 and multiples of five are labelled in the
margin, while only non-empty source lines count. Empty and whitespace-only
lines remain visible as spacing without advancing the counter. A separate
Material command inserts the block. Line numbers now match the source text's
size and baseline
instead of appearing like superscript footnote references. All current
Markdown editors offer the five GitHub alerts through their own icon and the
shared dialog factory. Alerts render in preview, read-only views, and the
existing EH PDFs. The exact five GitHub Octicons from the issue examples are
bundled with their MIT licence and shared by preview masks and vector PDF
rendering; no GitHub request is made during rendering. See
[the Markdown specification](../doc/ops/Markdown.md).

TopTeacher's own [MIT licence](../LICENSE.md) now explicitly names Jens Westarp
as the 2026 copyright holder. Maven metadata declares MIT, and the app packages
the project licence alongside the unchanged third-party notices.

The settled processing policy is a 10 MiB upload limit, a 40-megapixel decoded
limit, proportional resizing above 3,000 pixels on the longest side, JPEG
orientation correction, metadata removal through re-encoding, PNG transparency
preservation, and 90% JPEG quality. Public image endpoints are deliberately not
used; previews use active-session resources. See the
[operational specification](../doc/ops/LevelOfExpectations.md#managed-images)
for the authoritative behavior. Cross-editor footnote collection and
exam-sheet print/export layout, including numbered text on the printed page,
remain separate follow-up work.

## Session summary — 10–11 October 2026

The session completed the Issue 35 reimplementation and extended the authoring
workflow. The detailed settled behavior remains in the operational documents
linked above.

- Rebuilt on current `main`, removed the legacy Notes feature, and added the
  shared Aufgaben/Material/EH workflow, task budgets, per-task bonus caps,
  first-result locks, and rendered-only locked Markdown. Version is `1.3.0`.
- Refined action bars, collapse controls, point controls/badges, alignment,
  selection indicators, and editor-tab presentation. Material's action row now
  sits above its Markdown, matching Aufgaben and EH. See
  [application actions](../doc/ops/ApplicationActions.md) and
  [the shared exam editor](../doc/ops/LevelOfExpectations.md).
- Unified dialog construction through `TopTeacherDialogs`, with back/cancel on
  the left and actions on the right. Added the mandatory dialog-opening
  ellipsis rule to `AGENTS.md`.
- Adopted query-parameter declarations for criteria and managed images. Added
  Material image upload, database storage/deduplication, and safe session-scoped
  rendering. Image processing is isolated in `MaterialImageProcessor` and uses
  JDK image APIs, without an additional image-processing dependency. Corrected
  preview widths and existing-image editing via `Bild bearbeiten` / `Übernehmen`.
  The upload row is dashed drop target, `oder`, `Datei auswählen...`, without an
  upload icon; only the dashed area accepts and highlights dragged files.
- Added optional shared footnote commands, initially enabled only for Material.
  Insertion places the reference at the cursor and appends the definition.
  Preview/read-only footnotes stay editor-local and have no active reference
  links or return icons. See
  [footnotes](../doc/ops/LevelOfExpectations.md#footnotes).
- Added all five GitHub alerts to Markdown editors, previews, read-only views,
  and existing EH PDFs. Bundled the matching GitHub SVGs with their MIT notice;
  PDF icons render as vectors. Added literal `> [!NUMBERED]` blocks for Material,
  with clipping diagnostics, preserved blank lines, labels at 1/5/10/15/…, and
  normal-sized, baseline-aligned numbers rather than superscript-like styling.
  See [Markdown rendering](../doc/ops/Markdown.md).
- Added TopTeacher's MIT licence naming Jens Westarp only, plus Maven metadata
  and packaged licence text; third-party notices remain separate.
- Corrected the manual local TT launch by disabling both Spring DevTools restart
  and LiveReload. This removed the failed port-35729 browser connections without
  changing the normal development configuration. The separate Vaadin/Atmosphere
  `unload` warning remains a framework/browser compatibility follow-up; the
  React DevTools message is informational.

Verification: the required `mvn -pl topteacher-app -am test` reactor run passed
(including 204 application tests), all 13 frontend tests passed, and
`git diff --check` passed. Live preview checks confirmed sparse numbering and
equal font size/baseline for line numbers and source text; actual PDF checks
confirmed the five vector alert icons. TT was refreshed only after checking
saved state and remains running with the Material editor reopened.

Remaining scope: pupil-facing exam-sheet export, print-specific numbered-text
layout, and cross-editor footnote collection/renumbering. The former blanket
deferral of footnotes and line numbering no longer applies to Material authoring.

## MCP image-exchange prerequisite — 11 October 2026

The settings UI now keeps only `Fächer`, `Notenschlüssel`, and
`Systemeinstellungen` as top-level tabs. `Systemeinstellungen` provides a
fixed, single-selection list for `Dateizugriff`, `Sicherung`, and
`Zurücksetzen`; the selected content is rendered directly on the right without
another tab bar. The context area owns each section's action bar and retains
unsaved component state while switching. The existing backup and reset content
was moved into that structure without changing its behavior.

`Dateizugriff` persists ordered mappings from absolute client share roots to
absolute server mount roots. Input validation requires unique client roots and
currently reachable, readable server directories. The backend path resolver
uses the most specific matching mapping, requires the final target to be a
readable regular file, and verifies its real path remains beneath the real
server root.

The MCP module now also provides a reusable conversion from a database-backed
`LoeMaterialImage` to the SDK's native image content, preserving its MIME type
and Base64-encoding its bytes. These are infrastructure pieces for later exam
tools; no new public MCP tool contract is introduced by this step.

`Dateizugriff` now starts its action bar with the persisted `Installationsort`
choice (`Lokal` / `Entfernt`). Local installations resolve file paths directly
and hide the mapping editor; remote installations use the configured mappings.
Switching modes retains existing mappings and unsaved drafts. New installations
default to local, while existing mapped installations initially stay remote.
See [system settings](../doc/ops/SystemSettings.md#file-access).

Verification: the required `mvn -pl topteacher-app -am test` reactor run passed
with 211 application tests. Local/remote selection, hidden local mapping
controls, the exact local explanation, and restoration of the existing mapping
were checked in the running local demo. `git diff --check` passed.

### Morning follow-up — 11 October 2026

Corrected literal line numbering to exclude empty and whitespace-only lines
from the counter while retaining their visual spacing. Labels remain 1/5/10/15/…
and restart at the first non-empty line of each block. Clipping diagnostics now
use the same logical numbering, including unlabelled text lines after blanks.
Regression tests cover stanza separators and leading/consecutive/trailing blank
lines. See [the updated specification](../doc/ops/Markdown.md#literal-numbered-source-lines).

Verification: all 16 frontend tests, the required Maven reactor tests, the
freshly prepared frontend's TypeScript check, and `git diff --check` passed.
The Markdown module was installed locally for the next TT startup; the server
remains stopped as requested.

## MCP privacy implementation — 11 October 2026

Implemented the agreed name-focused contract in
[system settings](../doc/ops/SystemSettings.md#mcp-access-and-pupil-pseudonymization).
`MCP > Sicherheit` maintains named full/pseudonymized tokens with one-time
credential display, optional expiry, usage metadata, and immediate revocation.
Only credential hashes are stored. Existing file credentials migrate once;
revocation and the migration marker survive dataset reset. Atomic optimistic
saves prevent stale settings forms from undoing newer access-level changes.

Every MCP pupil-name projection and generated identity message uses the shared
pseudonymizer. Restricted discovery uses initials/IDs, and identity creation,
import, renaming, and name matching are guarded before mutations. Course tools
accept IDs for existing-pupil assignment and empty-course creation. Other
academic operations and the user-content protocol remain available.
Authentication is checked per request; sessions are bound to token and access
level so a downgraded token cannot replay full-name responses.

Renamed `Dateizugriff` to `MCP > Dateibehandlung`. Both MCP settings reuse one
line-item component; its action baseline stays fixed when field errors appear.

Verification: the required `mvn -pl topteacher-app -am test` reactor run passed,
including HTTP checks for restricted reads/writes, real-name probing, concurrent
access levels, revocation, and session replay. Actual Vaadin/Lumo component
layout was checked with synthetic data for validation errors and narrow widths.
`git diff --check` passed.

## Exam-first MCP design proposal — 11 October 2026

Discussion only: this section records the proposed next MCP structure. No
implementation, database, running-demo, or security changes belong to this
step. Token maintenance and pseudonymization are being implemented separately;
this proposal consumes their authenticated access context rather than replacing
that work. Tool names and request granularity below remain provisional.

### Established constraints

- There is no requirement to preserve the old MCP contract. Its EH-centred
  naming and combined creation payload reflect an outdated domain boundary.
- Exam-first is agreed: the exam is the aggregate root, not a replace-only
  document. Its shared parts/categories/tasks are authored in Aufgaben;
  Material belongs to parts; EH attaches requirements to existing tasks;
  results belong to pupils and assessment requirements/criteria. Targeted edits,
  incremental additions, and explicit reordering are mandatory.
- `footerMarkdown` is optional exam-level content outside every part. It is
  pupil-facing, just like all Aufgaben content; it is not an EH requirement or
  a repeated page footer. See
  [exam closing text](../doc/ops/LevelOfExpectations.md#exam-closing-text).
- The access modes and user-content boundary are already defined in
  [system settings](../doc/ops/SystemSettings.md#mcp-access-and-pupil-pseudonymization).
  Pseudonymized access is not read-only and does not imply complete anonymity.
- New pupil exam-sheet printing remains deferred. Its future order is material
  before categories/tasks within each part, a new page for each subsequent part,
  and one closing text after all parts. Existing EH result exports remain
  separate; see
  [the export scope](../doc/ops/LevelOfExpectationsPdfExport.md#deferred-exam-sheet-export).

### One academic API, two identity projections

Use one exam/content/EH/result model and the same academic operation names in
both modes. The bearer token determines effective access on the server; no tool
argument can select or override it. A small `get_mcp_context` response should
report contract version, effective access level, available operations, import
capabilities, limits, Markdown dialect/guide version and discovery, and the
relevant AI interaction rules. It must not expose
credentials or require disclosure of internal server mount paths.

The new pupil view should explicitly distinguish `pupilId`, `displayName`,
and identity representation (`FULL_NAME` or `INITIALS`), with real-name fields
available only under full access. Do not retain the transitional convention of
putting initials into a field called `name` and an empty string into `surname`.
All academic writes address existing pupils by ID. Initials remain nonunique,
surname-first labels; ambiguity is resolved using class/course context and ID.

Real-name searching and pupil creation/import/renaming are full-access-only.
ID/initials discovery and existing-pupil assignments/removals remain shared.
Identity-sensitive requests must be denied before real-name lookup, including
indirect roster creation and duplicate detection. A mode-aware advertised tool
list is desirable where supported, but every operation must enforce permission
independently of discovery. Token administration remains a UI concern, not a
new MCP tool family.

Reuse one access-policy and identity-projection boundary for structured output,
messages, elicitation, errors, exports, filenames, and metadata. Cached results,
resource retrieval, and retries must not replay a full-name response through
pseudonymized access. User-authored text and images, including the footer, stay
unchanged under the agreed user responsibility to remove names; automatic
redaction or OCR is not introduced. Server-side backup creation stays available
when only its filename is returned; raw database transmission does not.

### Proposed tool families

| Area | Proposed operations and boundary |
| --- | --- |
| Context and discovery | `get_mcp_context`, `get_markdown_guide`, course/pupil/exam lists, authoring options including grading scales; lightweight, active-by-default discovery. |
| Exam | `create_exam`, metadata updates, duplication, `get_exam`; metadata, content, footer, image-reference metadata, edit permissions and design diagnostics. Rosters, EH and results have explicit reads rather than being returned indiscriminately. |
| Aufgaben and Material | Bulk initial content plus targeted transactional creation, updates, deletion and reordering, provisionally `apply_exam_content_changes`; parts, categories, tasks, material, explicit task maxima and `footerMarkdown`. Incremental additions do not require resending existing content. No implicit EH creation. |
| Material images | `import_material_image` and `get_material_image`, scoped to an exam/material; import a client path through the configured resolver, and retrieve native MCP image content explicitly. |
| Level of expectations | `get_level_of_expectations` and targeted/bulk requirement creation, updates, deletion and reordering against existing task IDs; no second editable task hierarchy. Criteria remain derived from `crit:` Markdown declarations. |
| Assignments and results | ID-based roster operations, separate result reads and result writes; the same academic rules in both access modes. Existing export/backup capabilities remain within their agreed boundaries. |

`get_exam` should describe the pupil-facing definition in its natural shape:
ordered parts with separate `materials` and `categories` collections, ordered
tasks below categories, and `footerMarkdown` directly on the exam. It returns
image IDs/references and metadata, not all image bytes. EH reads may include
read-only task context, but they do not become an alternative content editor.

### Incremental authoring and targeted editing — agreed

- An AI can add a second part and its category/task subtree to an existing exam
  in one atomic request, using request-local keys for newly created parents.
  It can also append or insert individual tasks, materials, or requirements
  later. Existing exam content is neither resent nor implicitly replaced.
- Updates address stable node IDs and specified fields: for example one task's
  text, one material, the exam closing text, or one EH requirement. Validate
  each target's actual exam and parent ownership on the server. Untargeted
  nodes and attached EH remain unchanged; adding a task does not generate EH.
- Explicit sibling reordering is supported for parts, categories, tasks,
  materials, and requirements. Preserve IDs and EH associations; do not
  recreate nodes or redistribute points. Reordering changes order within the
  existing parent, not parent ownership. Cross-parent moves are distinct edits,
  never inferred from a reorder request.
- Prefer a complete sibling-ID sequence or a relative before/after sibling
  placement over exposing raw sort numbers. Complete sequences must contain
  every current sibling exactly once; relative targets must belong to the same
  parent. Validate stale requests, duplicates and foreign IDs. The exact
  request schema remains open.
- Task-budget edits preserve existing requirements. Saveable incompleteness
  produces diagnostics; over-allocation follows the existing rejection rules.
  The AI must explicitly request any corresponding EH adjustment rather than
  having it silently regenerated.
- Apply the existing result-derived lock to every operation and recheck it at
  commit, including results recorded since the AI read the exam. While results
  exist, Aufgaben/Material additions, updates, deletions and reordering are
  forbidden throughout that exam, including otherwise untouched or new parts;
  the closing text and images are locked too. EH allows only its established
  result-preserving corrections, not new requirements, structural changes,
  reordering, or changed assessment semantics. There is no MCP lock bypass.
- Batches are all-or-nothing, including mixed additions and edits within their
  authoring scope. Return created-ID mappings, affected canonical content/order,
  revision and diagnostics so the AI can continue without rereading the whole
  exam. Full and pseudonymized access share these academic editing rules.

### Markdown dialect discovery — proposal

The AI must discover TT's supported Markdown instead of guessing custom syntax.
Expose a compact, versioned `get_markdown_guide`, optionally filtered by content
context (Material, task/category, exam closing text, EH requirement). Return
readable rules and complete copyable examples as tool text, with a capability
summary where useful. `get_mcp_context` and relevant write-tool/field
descriptions point to this guide and include critical local restrictions.
Reading the guide is recommended before authoring; it is not a security check
or a brittle session prerequisite for otherwise valid writes.

An MCP resource may expose the same guide, but cannot be the only delivery
path: resource inclusion is controlled by the host, whereas tools are designed
for model discovery/invocation. See the
[MCP resource interaction model](https://modelcontextprotocol.io/specification/2025-06-18/server/resources#user-interaction-model).
The guide is bundled with the running TT version, identical in both access
modes, and contains no exam data or private installation paths.

Cover ordinary supported Markdown plus alerts, literal `> [!NUMBERED]` source
lines, footnotes, managed `img:` references and EH `crit:` declarations. Use
[the Markdown contract](../doc/ops/Markdown.md) and
[Material/criterion rules](../doc/ops/LevelOfExpectations.md) as the source of
truth rather than maintaining an independent dialect. Distinguish rendering
support from toolbar availability: numbered blocks and footnotes render on
other Markdown surfaces even though their commands are enabled only in
Material; managed images are Material-specific and criteria belong to EH
requirements.

Explain literal source breaks, uncounted blank lines, per-block numbering,
labels at 1 and multiples of five, no wrapping/nesting, and footnote definitions
outside numbered blocks. The AI must not manually prefix line numbers or
silently reflow an existing numbered source. Image IDs come from TT; criterion
keys remain stable during targeted corrections. Do not advertise unimplemented
syntax or guarantee that preview lines fit the deferred exam-sheet printout.

Guide examples and capability claims need conformance tests against the actual
parsers/renderers. Writes retain server-side domain validation regardless of
whether the guide was read, with actionable field/node findings for malformed
criteria, missing image references and invalid placement. A non-writing
validation request is a possible convenience, not a new rendering/print engine.
Keep authoritative guide instructions separate from user-authored exam content.

### Authoring and image workflow

An AI must be able to create the exam shell and then author a complete Aufgaben
tree, ordered materials, and closing text without supplying EH. Prefer a bulk
initial-content request, optionally included in `create_exam`, followed by
focused domain edits. Avoid both dozens of compulsory leaf calls and an
ambiguous replace-everything operation. Creation returns stable IDs and mappings
for request-local node keys so later EH refers to the actual created tasks.
Pupil selection/defaults must be explicit and documented; creating academic
content must never implicitly create pupils.

Image input follows the established client-path model, not a second upload
protocol: local paths resolve 1:1, remote paths use share mappings. Processing,
deduplication, declarations and limits must match the existing Material UI.
Persist image bytes and their material reference together; an abandoned import
must not leave an unreferenced durable blob. The exact insertion/import payload
is still open. The image processor currently lives in the app module, so a
shared backend image-import service is a refactor prerequisite, not an already
available MCP ingestion pipeline.

Image output uses the existing native Base64 `content` image block with the
stored MIME type, not a Base64 string embedded in the exam DTO or a public image
URL. Require the image to be referenced by the requested exam/material rather
than treating its opaque ID as authorization. Retrieve images on demand to
avoid inflating ordinary exam/EH reads. This follows the
[MCP tool-result structure](https://modelcontextprotocol.io/specification/2025-06-18/server/tools#tool-result).

### Shared write safeguards and readiness

- Preserve existing IDs. Array order establishes sibling order on creation;
  later reordering is explicit. Omitted update fields remain unchanged; an
  empty Markdown string clears content. Omitted nodes are not deletions.
- Explicit deletion reports affected descendants, material/image references
  and EH; it must not silently drop unrelated content or pupil results.
- Validate and commit each requested mutation/batch atomically. Use shared
  backend authoring services, also suitable for the UI, rather than duplicate
  rules in tool methods. Typed content commands are not raw SQL, arbitrary
  property paths, or an unrestricted generic database dispatcher.
- Introduce retry-safe creation/mutation keys and optimistic revision checks
  shared across UI and MCP writers. A stale edit returns a conflict rather than
  overwriting concurrent work. These are proposed prerequisites, not claims
  about current implementation.
- Allow incomplete task/EH allocations as saveable authoring stages, while
  rejecting over-allocation according to the operational point rules. The old
  creator's compulsory task/requirement equality must not force EH into the
  initial Aufgaben workflow. Readiness diagnostics identify incomplete budgets,
  grading-scale mismatches, invalid criteria, and missing image references.
- Report token capabilities separately from this exam's editable areas and
  readiness. First results lock Aufgaben, Material, images and the footer;
  EH permits only its established result-preserving corrections. Archived
  records remain historical, and result entry requires a valid assessment
  design. No new stored lifecycle or publish action is implied.

Recommended sequence: discover context/course/options and permitted pupil IDs;
create exam and participant selection; author content/material/footer; import
and place images; add EH against returned task IDs; review readiness; record
results. Full access can separately maintain pupil identities; pseudonymized
access follows the same academic sequence using existing IDs and initials.

Next discussion: settle the concrete command schemas/tool names, including
ordering and image insertion payloads. Exam-first ownership with bulk initial
creation, targeted editing and incremental additions is agreed. Implementation
must wait until explicitly requested; settled behavior can then be promoted to
`doc/ops/` without duplicating the privacy specification here.
