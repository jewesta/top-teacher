# Issue 15

## Voneinander abhängige Eingabefelder bei Bedarf aktualisieren

This issue introduces groundwork for systematic refresh handling between
dependent exam context tabs.

## Groundwork

- Added an exam-level correction mode for the level of expectations once
  results exist.
- Kept wording corrections possible in correction mode.
- Locked LOE structure changes, point changes, bonus changes, and criterion
  key/order changes after results exist.
- Added a lock indicator to the `EH` tab while correction mode is active.
- Added a discard action for unsaved LOE edits so blocked correction-mode
  changes do not trap the user in an unsaveable dirty state.

## Verification

- `mvn -pl topteacher-app -am test`
- `git diff --check`
- Browser smoke with demo data on `/top-teacher/exams`: verified the locked `EH`
  tab label, disabled LOE structure/point actions, editable wording fields, and
  no browser console errors.
