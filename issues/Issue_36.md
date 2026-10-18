# Issue 36: Printable exam tasks

## Status

Exam-sheet printing has not started. The prerequisite error-handling fixes are implemented:

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
assessment totals retain all tasks in both modes. Pupil PDF generation remains
deferred.

The agreed behavior, including the future summary line for implicit assessment
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
