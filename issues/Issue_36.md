# Issue 36: Printable exam tasks

## Status

Exam-sheet printing has not started. Two prerequisite UI fixes are implemented:

- Empty criterion editor text no longer reaches the Markdown library's word
  lookup. The selector remains usable after text is entered.
- The application router catches unexpected display errors with a German error
  page and an explicit reload action. Exception details stay out of the page.

The functional contracts are documented in [Markdown](../doc/ops/Markdown.md#empty-criterion-editors)
and [Application Actions](../doc/ops/ApplicationActions.md#unexpected-display-errors).

## Verification

- `mvn -pl topteacher-app -am test`: 716 tests passed.
- All 25 frontend regression tests passed, including empty/whitespace criterion
  text, valid word/selection recovery, error routing under a context path,
  hidden exception details, and the explicit reload action.
- TypeScript checking and the production package build passed.
- `git diff --check` passed.
