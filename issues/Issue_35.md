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
- Footnotes and line numbering are deferred to a separate issue. They must not
  hold up the initial material-authoring workflow.
- Material supports managed PNG/JPEG images through the image workflow recorded
  below and specified in the
  [operational documentation](../doc/ops/LevelOfExpectations.md#managed-images).
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
- The future pupil printout is organized by part. Within each part, all tasks
  are printed first, followed by all materials assigned to that part:

  ```text
  Klausurteil A
  Aufgabe 1
  Aufgabe n
  Quelle A
  Quelle n

  Klausurteil B
  Aufgabe 1
  Aufgabe n
  Quelle A
  Quelle n
  ```

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
- Footnotes and source-text line numbering are deferred.

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
committed through `8b70361`. The stages were reapplied together because their
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

The criterion change, Material footnotes, and managed-image follow-up are
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

The settled processing policy is a 10 MiB upload limit, a 40-megapixel decoded
limit, proportional resizing above 3,000 pixels on the longest side, JPEG
orientation correction, metadata removal through re-encoding, PNG transparency
preservation, and 90% JPEG quality. Public image endpoints are deliberately not
used; previews use active-session resources. See the
[operational specification](../doc/ops/LevelOfExpectations.md#managed-images)
for the authoritative behavior. Cross-editor footnote collection, line
numbering, and exam-sheet print/export layout remain separate follow-up work.
