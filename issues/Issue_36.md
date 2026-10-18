# Issue 36: Printable exam tasks

## Status

Exam-sheet printing and its prerequisite error-handling fixes are implemented:

- Empty criterion editor text no longer reaches the Markdown library's word
  lookup. The selector remains usable after text is entered.
- The application router catches unexpected display errors with a German error
  page and an explicit reload action. Exception details stay out of the page.

The functional contracts are documented in [Markdown](../doc/ops/Markdown.md#empty-criterion-editors)
and [Application Actions](../doc/ops/ApplicationActions.md#unexpected-display-errors).

## Category print setting

Implemented the category-level eye toggle in `Aufgaben`, with the complete
category action bar moved above its description. The setting uses normal
save/discard and correction-mode locking, defaults to printing on database
upgrade, and survives copies and unrelated authoring operations. Editors and
assessment totals retain all tasks in both modes. Pupil PDF generation is implemented below.

The agreed behavior, including the summary line for implicit assessment
categories, is specified in
[Level of Expectations](../doc/ops/LevelOfExpectations.md#category-task-visibility-on-the-exam-sheet).

## Previous error-handling verification

- `mvn -pl topteacher-app -am test`: 716 tests passed.
- All 25 frontend regression tests passed, including empty/whitespace criterion
  text, valid word/selection recovery, error routing under a context path,
  hidden exception details, and the explicit reload action.
- TypeScript checking and the production package build passed.
- `git diff --check` passed.

The eye button explicitly retains Vaadin's icon theme after label/glyph updates,
so toggling keeps it centered in the hover highlight without layout CSS changes.

## Category setting verification

- `mvn -pl topteacher-app -am test`: 723 tests passed.
- Regression coverage checks action-bar placement, both eye states, save/discard,
  stale and failed saves, correction-mode locking, unchanged task budgets,
  database upgrades/reinitialization, exam copies, and authoring edits/reordering.
- `git diff --check` passed.

## Replacement Spanish demo

Replaced the former Spanish migration/listening demo seed with the supplied
2026 exam, retaining the English demo. The Spanish course and exam use the
existing 160-point grading scale. Both source texts, footnotes, task wording,
complete EH, category eye settings, and bonus requirements are included.
Alternative task 3.2 and demo source images remain excluded.

Part B preserves the source's group-level assessment budgets. Reapplying seeds
no longer rewrites existing task text or budgets. See
[Demo Data](../doc/ops/DemoData.md) for the authoritative demo contract.

Verification: all 725 Maven tests passed, including full Spanish demo validation
against the 160-point scale, category/part budgets, source/footnote coverage and
seed reapplication without overwriting edited tasks. `git diff --check` passed.

## Footnote marker alignment

The shared Markdown preview/viewer replaces the library's fixed footnote-list
inset with a font-relative marker width derived from the number of footnotes.
This prevents clipping at 10, 100, and subsequent digit boundaries. The functional
contract is in [Markdown](../doc/ops/Markdown.md#footnote-list-numbering).

Verification: 725 Maven tests and all 31 frontend tests passed. Rendering tests
cover 9, 10, 27, 99 and 100 footnotes plus ordinary-section preservation.
TypeScript and `git diff --check` passed. The local app's generated shared
resources match the updated module, and its development bundle was rebuilt.
Browser automation was unavailable, so visual verification remains manual.

## Original EH wording

Restored the Spanish demo's original sentence continuations and rubric examples
instead of paraphrased criterion headings. The existing export heading is
`Anforderungen: Der/die Schüler:in...`; it remains unchanged. Points, criteria
identities, category settings, and assessment grouping are preserved. See
[Demo Data](../doc/ops/DemoData.md#spanish-exam).

Verification: all 725 Maven tests passed. Regression assertions cover the
original bonus sentence, verb-led requirement openings and Part B rubric rows.
The local review database was updated only where the prior seeded text still
matched; criterion IDs and points were retained. Its task total remains 160.
`git diff --check` passed.

## Pupil task-sheet export

Implemented anonymous and personalized downloads in Aufgaben, portrait A5 flow
imposed two-up on landscape A4 using the EH renderer. Personalized copies can
cover one assigned pupil or the full exam roster. Material pagination uses the
persisted `Auf eigener Seite` setting, defaulting to true. Hidden categories
print only their title and regular maximum. Hilfsmittel is an inline material at
the start of Part A in the Spanish demo.

The authoritative behavior is in
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md).

Verification: `mvn -pl topteacher-app -am test` passes all 734 tests.
Regression coverage includes existing-database defaults, setting persistence and
copies, authoring edits/reordering, dirty and locked material UI, download choices,
pupil-only content, page breaks, long pupil names and separate imposition of
personalized copies. All seven demo A5 pages and four imposed A4 pages were
visually reviewed, including source continuation and multi-digit footnotes.
The local review database retains its existing data; unchanged seeded Hilfsmittel
was moved to Part A after creating a backup. `git diff --check` passes.

## Release version

Raised the reactor, module parent references, and packaging/production launcher
defaults to `1.4.0`. Historical issue notes retain their original release versions.

## Material page-break toggle

Replaced the material checkbox with a custom SVG toggle. Separate-page mode
uses the page-break glyph; flowing mode uses a closed page frame without the
dotted line. Both modes retain the icon theme and centered alignment, with
German action tooltips. Save/discard, live editor preservation and correction
locking follow the existing material setting. See
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md#material-pagination).

Verification: all 735 Maven tests and `git diff --check` pass. Toggle coverage
checks the state-specific source and icon theme in both states, accessible
pressed state and German action labels, save/discard and correction locking.
Browser automation was unavailable for visual hover verification.

## Shared icon toggles

Removed the material-only selected background and replaced the two independent
implementations with `IconToggleButton`. It owns state, icon/action-label changes,
accessible pressed state and icon-theme retention for both eye and page toggles.
Each editor retains its existing save/discard and correction locking. See
[Application Actions](../doc/ops/ApplicationActions.md#icon-state-toggles).

Verification: all 740 Maven tests and `git diff --check` pass. Shared-control
tests exercise both native Vaadin and custom SVG icons, both initial modes,
repeated changes, action-bar integration, neutral styling, accessible labels and
pressed state, notification behavior and disabled clicks. Both editor suites
verify use of the shared control and retain save/discard/locking coverage.
