# Issue 32: Support half points

## Intent

Support levels of expectations in which individual scoring units can be worth
half a point. A half-point unit is either achieved or not achieved, just like a
full-point unit. A full-point unit must not become partially achievable merely
because the application supports half points elsewhere.

## Current state

- TopTeacher currently represents maximum points, achieved points, grading
  scale boundaries, aggregations, persistence values, exports, and MCP values
  as integers.
- The EH designer and result editor use integer-only point fields.
- Allowing every result field to use increments of `0.5` would be incorrect: it
  would also allow a declared full point to be awarded as half a point.
- An integer maximum does not necessarily reveal the scoring units from which
  it is composed. A maximum of four points could mean four full-point units,
  eight half-point units, or a mixture of both.

## Design direction

- Store and aggregate exact half-point values without floating-point
  arithmetic. One possible representation is an integer count of half-point
  units, where one unit represents `0.5` points and two units represent one
  point.
- Preserve exact values throughout EH editing, result entry, aggregation, and
  export. Any grading-related rounding should happen once at the grading
  boundary, not in intermediate totals.
- Add an explicit way to declare where half-point results are permitted. The
  precise model is intentionally undecided. Candidates include a per-
  requirement point increment, a count of half-point units, or individually
  weighted scoring items.
- Existing whole-point data must migrate losslessly.

## Open questions

- Can a requirement with a whole-number maximum nevertheless contain one or
  more half-point units?
- Can one requirement mix full-point and half-point units?
- Must TopTeacher remember which individual scoring unit was achieved, or is an
  aggregate result for the requirement sufficient?
- How should an exact half-point total be rounded when applying an integer
  grading scale?
- Should grading scales and their ranges remain integer-based, or must they
  support half-point boundaries as well?

## Expected impact

The change is cross-cutting and is expected to affect:

- the domain types for EH requirements, results, point summaries, and point
  rules;
- the database schema and migration of existing point values;
- EH design and result-entry controls and validation;
- bonus-point capping and all aggregate displays;
- PDF and spreadsheet exports;
- grading and evaluation;
- MCP input and output schemas;
- base data, demo data, and automated tests.

## Status

Design discussion postponed. Implement this as a dedicated issue rather than
as part of Issue 22.
