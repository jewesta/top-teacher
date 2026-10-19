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

## Existing alert editing and PDF icons

The shared Markdown editor now detects an alert containing the cursor/selection
using CommonMark source spans. Its dialog preloads the original type and body,
and confirmation replaces the entire alert while preserving surrounding content.
The Aufgabenblatt stylesheet no longer hides alert icons; the existing trusted
SVG embedding pipeline supplies all five vector symbols. See
[Markdown](../doc/ops/Markdown.md#alerts) for the functional contract.

Verification: all 753 Maven tests and `git diff --check` pass. Regression
coverage includes all five alert types, cursor/selection editing, type changes,
cancellation, adjacent content, nested quotes/lists, code examples, numbered
source blocks, Markdown formatting and CRLF preservation. Actual Aufgabenblatt
PDFs contain all six expected vector icons (five test alerts and the existing
demo NOTE); the source NOTE and all five test symbols were visually inspected.

## PDF alert box styling

The Aufgabenblatt alert boxes use a wider (0.8 mm) left border in the matching
icon color and a light gray background with internal padding. Typography stays
unchanged. See [Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md).

Verification: all 753 Maven tests and `git diff --check` pass. The rendered
source alert and all five alert types were visually checked for matching border
and icon colors, a pale gray fill, padding, readable text and preserved symbols.

## Automatic fitting of numbered source text

Numbered PDF blocks start at 9 pt and shrink uniformly only when their longest
rendered line exceeds the available width. Fitting uses the PDF engine's actual
layout, including inline formatting, footnote references, the number gutter
and narrower containers. Authored lines never wrap; other blocks and ordinary
text retain their font sizes. See
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md).

Verification: all 757 Maven tests and `git diff --check` pass. PDF regression
coverage checks the 9 pt ceiling, actual narrow/wide glyph widths, uniform
block sizing, bold/italic text, superscript footnotes, narrower containers and
150 logical lines across page breaks. The demo and synthetic PDFs were
visually checked for intact source lines, margins, numbering and A4 imposition.

## Short footnote divider

The Aufgabenblatt footnote divider follows the width of its “Fußnoten” heading
instead of spanning the page. See
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md).

Verification: all 757 Maven tests and `git diff --check` pass. The rendered PDF
was visually checked; the divider and label widths match (34.5 pt).

## Markdown heading dividers

Removed automatic heading borders from shared Markdown previews/read-only views
and the Aufgabenblatt PDF. A dedicated class preserves dividers on TT's own
Klausurteil headings, including repeated headings before tasks. Explicit
Markdown thematic breaks and the short footnote divider remain available. See
[Markdown](../doc/ops/Markdown.md#headings-and-dividers).

Verification: all 757 Maven tests and `git diff --check` pass. The demo PDF was
visually checked: Markdown source titles have no divider, while Klausurteil
headings retain theirs. The local app was rebuilt with the shared preview CSS;
browser automation was unavailable for a live preview check.

## Compact date and name header

Moved the unlabeled date to the centered top position. The name field begins
just to its right and extends to the right margin; personalized name fitting
uses the narrower field. See
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md#page-format-and-reading-order).

Verification: all 757 Maven tests and `git diff --check` pass, including long
pupil names and matching anonymous/personalized pagination. Both header
variants were visually checked in the rendered PDFs. Local TT was updated
without resetting its review database.

## Single-row course, date and name header

The opening row now places the course on the left, the date in the center and
the name on the right, all at the former course size of 8 pt. Only the name
entry area is underlined; `Name:` remains outside the line. Personalized name
fitting starts at 8 pt. See
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md#page-format-and-reading-order).

Verification: all 757 Maven tests and `git diff --check` pass. Anonymous and
personalized PDFs were visually checked. Course, date, label and name share
one baseline and the same 8 pt size; the underline starts after the label.
Long-name fitting and matching pagination remain covered. Local TT was updated.

## Date beside the course

Combined course and date as `course · date` on the left of the opening row.
The 8 pt size and right-hand name entry remain unchanged. See
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md#page-format-and-reading-order).

Verification: all 757 Maven tests and `git diff --check` pass. The rendered
header was visually checked. Local TT was updated with the existing review
database retained.

## Task points below the task

Removed point budgets from task headings. They now appear after each task's
text on their own right-aligned line, without brackets; the point line avoids
a page break from the preceding task text. See
[Pupil task-sheet PDF export](../doc/ops/ExamTaskSheetPdfExport.md#pupil-facing-content).

Verification: all 757 Maven tests and `git diff --check` pass. Both demo task
pages were visually checked for plain headings and separate, right-aligned
point lines without brackets. Local TT was updated with its review data retained.

## Restore dot-separated heading budgets

Reverted the right-aligned part/category heading budgets after layout review.
These headings again use `title · points`; individual task points remain on
their separate right-aligned line below the task text.

Verification: all 757 Maven tests and `git diff --check` pass. Export template,
stylesheet and functional specification match the previously verified layout
from before the reverted change. Local TT was updated without resetting data.

## Shared download dialogs

Replaced the Aufgabenblatt menu/manual dialog with a direct icon action and
standard dialog offering anonymous, all pupils and a selected pupil. Individual
and combined result sheets share the audience dialog. Auswertungen keeps the
point-table action separate and has one result-sheet entry. All confirmation
actions use `Laden`; saved-state guards and export routes remain in place.
The shared dialog requirement is recorded in `AGENTS.md`. See
[Task-sheet export](../doc/ops/ExamTaskSheetPdfExport.md) and
[Result-sheet export](../doc/ops/LevelOfExpectationsPdfExport.md#download-dialogs).

Verification: all 765 Maven tests and `git diff --check` pass. Coverage includes
radio ordering/defaults, all task-sheet routes, selected-pupil Binder validation,
empty rosters, both result audiences, cancellation, standard dialog styling and
button bars, menu/button wiring and unsaved-work guards. Browser access was
unavailable for visual review. Local TT was rebuilt with its review data retained.

## Task-sheet output labels

The three options now read `Kopiervorlage mit Freifeld für Name`,
`Für alle Schüler:innen` and `Für ausgewählte Schüler:in`, in that order. See
[Task-sheet export](../doc/ops/ExamTaskSheetPdfExport.md).

Verification: all 765 Maven tests and `git diff --check` pass, including the
exact option labels and their order. Local TT was updated.

## Result-sheet version and pupil scope

Moved all-pupil result-sheet export into `Ergebnisbogen laden`. The dialog now
combines `Version` (pupil/teacher) with `Schüler:in` (current pupil, shown as
surname/name, or all pupils), defaulting to the current pupil's pupil version.
Auswertungen now directly downloads only the point table. Existing PDF routes,
file names and unsaved-result guards remain in use. See
[Result-sheet export](../doc/ops/LevelOfExpectationsPdfExport.md#download-dialogs).

Verification: all 767 Maven tests and `git diff --check` pass. Tests cover group
labels/order, current-pupil label/default, both versions with both scopes, all
four emitted PDF URLs/file names, cancellation and the direct point-table URL.
Existing unsaved-work guards remain covered. Browser access was unavailable
for visual review. Local TT was updated with its review data retained.
