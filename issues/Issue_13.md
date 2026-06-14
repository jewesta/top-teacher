# Issue 13

## Summary

- Reworked demo and starter data for the 1.0 preparation branch.
- Replaced the fixed subject enum with user-managed subjects in settings.
- Added database initialization/reset flows for empty databases and demo data,
  including the first-start choice.
- Centralized display names for enums through a shared display-name contract.
- Updated user-facing wording for gender-neutral German labels.
- Added exam numbering per course, including linked Nachschreibeklausuren shown
  with the original number and `NK`.
- Moved exam participation to per-exam pupil assignments so normal exams and
  Nachschreibeklausuren can use different pupil sets.
- Reused a shared pupil assignment grid component in courses and exams.
- Locked pupil removal when existing references would make removal unsafe:
  course pupils assigned to an exam stay locked in the course roster, and exam
  pupils with entered results stay locked in the exam roster.

## Verification

- Ran `mvn -pl topteacher-app -am test`.
- Ran `git diff --check`.
- Smoke-tested the locked assignment checkboxes in the local Vaadin app.
