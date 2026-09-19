# Issue 22

## Intent

- Avoid presenting the total number of achievable bonus points in pupil-facing
  level-of-expectations exports because the explicit total can prompt
  unnecessary discussions.
- Treat this as a presentation decision rather than a correction to the point
  calculation.

## Design

- Show only regular maximum points in pupil-facing task sums, part totals, and
  exam totals.
- Show awarded regular and bonus points as one combined value in pupil-facing
  task sums, part totals, and exam totals.
- Keep individual bonus requirements and their parenthesized maximum points
  and awarded points visible.
- Continue aggregating achieved bonus points and using them for grade
  calculation.
- Preserve the existing teacher-facing export, including aggregated achievable
  bonus points.

## Progress

- Added an audience-specific display policy for aggregate maximum points.
- Updated the shared export fragments to apply that policy at every aggregate
  level.
- Updated pupil-facing aggregate results to add awarded bonus points directly
  to the regular result instead of showing a separate parenthesized value.
- Added model and rendered-HTML coverage for the pupil and teacher variants.
- Removed unused and test-only overloads discovered while reviewing the export
  change, keeping tests on production entry points.

## Verification

- `mvn -pl topteacher-app -am test`
- DevTools canonical formatter assertion for the branch
- `git diff --check`
- Local browser test with a fresh demo database and the Spanish demo exam
  (150 regular points and 4 achievable bonus points):
  - The pupil PDF shows 150, 120, and the regular task totals without the
    achievable bonus totals.
  - Individual bonus requirements still show their parenthesized maximum.
  - Awarded bonus points remain aggregated in the pupil PDF; 2 awarded points
    are shown as `2` in the task, part, and exam totals.
  - The individual awarded bonus points remain parenthesized as `(2)`.
  - The teacher PDF continues to show the achievable bonus totals, including
    `150 (+ 4)` for the exam total.
