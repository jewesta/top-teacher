# Issue 19

## Summary

- Added exam-owned grading scales so existing exams no longer follow later course
  default changes.
- Kept the course grading scale as the default for new exams and added a
  creation-only `Notenschlüssel` selector.
- Added a Settings tab for fixed 16-row grading scales with editable point
  boundaries.
- Reworked the grading-scale settings tab to use the main list/edit split
  layout.
- Moved grading-scale settings onto the shared `SplitListDetailView` and added
  a base hook for hiding the quick filter where it is not useful.
- Applied the fixed top action bar pattern for the long grading-scale detail
  content.
- Reworked the subject settings tab to use the shared split list/detail
  split layout.
- Added a shared list-pane `Neu` action to split list/detail views and kept the
  quick filter first in split toolbar order.
- Removed technical ID columns from UI grids and excluded those IDs from quick
  filter matching.
- Hid lifecycle controls while creating new subjects and grading scales because
  new records default to active.
- Locked grading scales in the backend once any exam references them.
- Seeded `Einführungsphase` and `Qualifikationsphase` as built-in grading
  scales for empty and demo databases.
- Switched result entry, evaluation views, grading-scale display, PDF export, and
  Excel export to use the exam's grading scale.
- Backfilled existing exam rows from their course defaults in the schema and
  updated demo data inserts.

## Verification

- Ran `mvn -pl topteacher-app -am test`.
- Ran `git diff --check`.
