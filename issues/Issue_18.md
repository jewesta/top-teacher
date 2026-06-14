# Issue 18

## Summary

- Replaced the generated geography and chemistry demo exams with real-world
  English and Spanish level-of-expectations examples.
- Kept the existing demo pupils and enrolled active pupils in both new courses.
- Seeded Q-phase English and Spanish courses, 150-point exams, source-style
  parts, categories, tasks, requirements, criteria, and bonus rows.
- Limited demo course rosters to 5 English pupils and 7 Spanish pupils to keep
  the UI readable.
- Removed fabricated demo correction results so the supplied LOE stay
  source-faithful.

## Verification

- Ran `mvn -pl topteacher-app -am test`.
- Ran `git diff --check`.
