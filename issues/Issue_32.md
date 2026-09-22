# Issue 32: Support half points

## Goal

Support criterion values in half-point increments while keeping requirements
as the rounding boundary. A requirement may also be defined without criteria
and marked holistically.

Compatibility with existing level-of-expectations data is not required because
no production data has been created yet.

## Authoritative requirements

- [Level of Expectations](../doc/ops/LevelOfExpectations.md)
- [Level of Expectations Results](../doc/ops/LevelOfExpectationsResults.md)
- [Level of Expectations PDF Export](../doc/ops/LevelOfExpectationsPdfExport.md)

These operational documents are the authoritative functional specification.
This issue note records the implementation sequence and current status only.

## Core decisions

- Point values are represented internally as integer half-point units.
- Criterion definitions use `eh:<key>[/<points>]`; the point value defaults to
  one and the key remains the stable identity.
- Requirements retain an explicit integer maximum. If criteria exist, their
  configured values must sum exactly to that maximum; zero criteria is valid.
- The grading-scale maximum is authoritative for regular requirement maxima.
- Under-allocation is a saveable incomplete design. Over-allocation may exist
  in pending editor state but prevents saving.
- During marking, criteria guide the marker but do not hard-gate the awarded
  result. A requirement-level adjustment covers valid work outside predefined
  criteria.
- Raw half points are rounded half-up inside each requirement. No half point is
  aggregated beyond the requirement.

## Implementation sequence

1. Introduce reusable validation and tray infrastructure and repair EH
   validation with whole points.
2. Add half-point criterion values and optional criteria to EH design.
3. Add awarded criterion values, adjustment, direct holistic marking, and the
   revised Results interactions.
4. Update PDF and other exports, integrations, fixture data, and tests.

## Completed preparation

- Added the reusable `westarps-validate`, `westarps-vaadin-animate`,
  `westarps-vaadin-badge`, and `westarps-vaadin-tray` modules.
- Added common designer tray plumbing and the validation-aware `StatusTray`.
- Implemented whole-point EH validation against the grading-scale and
  requirement totals.
- Added derived hourglass, tick, and lock tab states; live pending aggregates; concise
  targeted tray messages; and the agreed tray interactions. Status links now
  perform their in-page jump without navigating away from the selected exam.
- Aligned EH and Results aggregate badges and applied the shared pointer-cursor
  behavior to interactive controls.

## Completed EH half-point phase

- Added integer half-point units to the criterion model and persistence schema.
- Extended criterion tags to `eh:<key>[/<points>]`, including comma and dot
  parsing, default one-point values, malformed-tag reporting, and stable keys.
- Added a Markdown extension seam and a TT-owned criterion extension with the
  agreed `P` control, 0,5-through-4 presets, `4+` placeholder, and a maximum
  valid custom value of 999 points. Criterion rendering and Results checkboxes
  also belong to TT, not the reusable Markdown module.
- Changed criterion pills from internal keys to point values.
- Made criterion-free requirements valid while retaining exact allocation when
  at least one criterion exists.
- Enforced save blocking for excess or malformed criterion allocations while
  keeping under-allocation saveable.
- Added a live inline warning or error beside each affected requirement maximum
  from the same criterion validation result shown in the status tray.
- Persisted criterion values, included them in correction-mode locking and MCP
  views, and retained teacher-export criterion identity matching.

## Remaining implementation

- Implement Results criterion awards, adjustment budgeting, holistic marking,
  synchronized controls, and bidirectional criterion highlighting.
- Update PDF and spreadsheet exports and the remaining grading and evaluation
  paths for the Results-side half-point behavior.

## Status

The reusable preparation, EH validation refactor, and EH half-point phase are
complete. Results-side marking behavior and the final export work remain.
