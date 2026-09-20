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
- Added derived pen, tick, and lock tab states; live pending aggregates; concise
  targeted tray messages; and the agreed tray interactions.
- Aligned EH and Results aggregate badges and applied the shared pointer-cursor
  behavior to interactive controls.

## Remaining implementation

- Introduce the half-point model, persistence, parser, and `eh:` syntax.
- Update EH criterion presentation and optional-criteria validation.
- Implement Results criterion awards, adjustment budgeting, holistic marking,
  synchronized controls, and bidirectional criterion highlighting.
- Update PDF and spreadsheet exports, grading and evaluation paths, MCP
  schemas, base/demo data, and automated tests.

## Status

The reusable preparation and whole-point EH validation refactor are complete.
The half-point domain and user-interface behavior have not been implemented.
