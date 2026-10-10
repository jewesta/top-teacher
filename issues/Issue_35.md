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

- Add an `Aufgaben` tab immediately before the `EH` tab.
- Add a dedicated `Material` tab immediately after `Aufgaben` and before `EH`.
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
- After results exist, EH uses its existing safeguards. Only requirement text
  changes that preserve result-bearing semantics, such as criterion identity,
  remain possible; structure, ordering, points, bonus status, and other guarded
  properties remain locked.
- Printing and export are deferred until the `Aufgaben` tab is fully
  functional. No separate teacher version is currently needed.
- Footnotes and line numbering are deferred to a separate issue. They must not
  hold up the initial material-authoring workflow.
- Managed image uploads and other image-storage work are deferred. The first
  version of material authoring does not need to solve them.
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

- The effective result for the exam is the sum of the individually capped task
  results. Bonus points from one task cannot compensate for missing points in
  another task.
- EH shows both the points assigned to the task and the points currently
  distributed across its requirements, including a visible remaining or
  excess amount while the EH is incomplete.
- A temporary mismatch must be allowed while the EH is being authored; an
  aggregate database constraint cannot represent that editing workflow.
- Results must not be recorded until every task's requirement-point
  distribution is valid.
- Once results exist, task points are locked with `Aufgaben`, while requirement
  points remain protected by the existing EH correction-mode safeguards.

Today all higher-level point totals are derived upward from requirements and
`eh_task` has no point column. This issue deliberately introduces a stored task
point budget because task points and requirement points represent two separate
workflow stages. Existing task point values must be backfilled from their
current non-bonus requirement totals.

The existing bonus cap is applied once at exam level. It must move to the task
boundary throughout result entry, grading, evaluation, existing EH/result
exports, and MCP result summaries.

## Initial implications

- `LoeTask` currently contains only its title and sort order. It will need a
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
  need to recognize the additional exam tab.

## Open questions

- Are attachments, page breaks, answer space, and other print-specific
  features in scope, or is the first version limited to Markdown authoring?

## Reimplementation status

The first implementation pass reached the scope described below and is
preserved on `codex/issue35-pre-rebuild` at commit `2d484a2`. On 10 October
2026, `issues/Issue_35` was deliberately restarted from the current `main`
baseline (`9aa2100`) so that Issue 32's half-point model, point aggregation,
and status tray remain authoritative instead of being reconstructed through a
large conflict resolution.

The Issue 35 work will be reapplied in reviewable stages:

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

The preserved first pass demonstrated the intended end state:

- `Aufgaben`, `Material`, and `EH` are separate tabs in workflow order and use
  the same tree-section, designer, collapse, breadcrumb, save/dirty, and lock
  infrastructure.
- `Aufgaben` owns editing for parts, categories, task Markdown, and explicit
  task maximum points.
- `Material` stores ordered title/Markdown entries under exactly one read-only
  exam part.
- EH shows part, category, task Markdown, and task points as read-only context;
  only requirements remain editable there.
- The repository enforces the first-result lock for tasks and material, the EH
  correction safeguards, and valid task/requirement point distribution before
  result entry.
- Result totals, evaluation, exports, and MCP summaries cap bonus points at the
  individual task boundary.
- Existing task point budgets are migrated from their regular requirements,
  while duplication copies task content, points, material, and EH data.
- Locked task, category, and material Markdown is shown as rendered content;
  no Markdown editing controls remain in the component tree.
- The accidental legacy Notes feature has been removed end-to-end, including
  its persistence, export, and MCP surfaces.

Print/export layout, uploads, footnotes, and line numbering remain deferred.
