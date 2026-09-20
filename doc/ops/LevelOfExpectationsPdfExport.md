# Level of Expectations PDF Export

The level-of-expectations PDF presents one pupil's assessment as a static,
printable document. It uses the assessment structure defined in
[LevelOfExpectations.md](LevelOfExpectations.md) and the awarded values defined
in [LevelOfExpectationsResults.md](LevelOfExpectationsResults.md).

## Variants

TopTeacher produces a pupil version and a teacher version from the same level
of expectations. Both preserve the part, category, task, and requirement
hierarchy and show the rounded achieved result for every requirement.

| Content | Pupil version | Teacher version |
| --- | --- | --- |
| Requirement text | Yes | Yes |
| Criterion text | Yes, without marking annotations | Yes, with marking annotations |
| Rounded requirement result | Yes | Yes |
| Criterion result indicators | No | Yes |
| Adjustment breakdown | No | Yes, when non-zero |
| Requirement comments | No | Yes |
| Exam notes | No | Yes |
| Grading scale | Yes | Yes |
| Draft or teacher watermark | No | Optional |

## Common structure

The existing compact table structure remains authoritative. Requirements keep
their number, text, maximum, and achieved-result cells. Task summaries retain
their existing position, and bonus points remain visibly separate from regular
points, including the existing parenthetical presentation where applicable.

The PDF must use the same requirement-local rounding as the Results view:

```text
raw requirement result = criterion subtotal + adjustment
exported requirement result = round half-up(raw requirement result)
```

For a criterion-free requirement, the directly awarded value is rounded by the
same rule. Only the rounded requirement result contributes to task, category,
part, exam, grading, and export totals. No half point is aggregated beyond its
requirement.

## Pupil version

The pupil version is a clean assessment document rather than a reproduction of
the marker controls.

- Criterion annotations are removed while their text remains in the document.
- No criterion identity, point pill, checkbox state, or status symbol is shown.
- The existing achieved-result cell contains the rounded requirement result.
- Adjustment details, requirement comments, and teacher notes are omitted.

## Teacher version

The teacher version explains how the recorded result was reached without
introducing another table column.

- Every criterion pill displays the points awarded to that criterion rather
  than its internal key.
- A red tick denotes the criterion's full value.
- A red circle denotes a partial value.
- A red cross denotes zero points.
- The rounded requirement result remains the primary value in the existing
  achieved-result cell.
- A non-zero adjustment is shown as a small secondary line in that same cell.
- The requirement comment remains in the neighboring requirement-text cell.
- Existing teacher-only note sections and the optional watermark remain
  available.

The status symbol and awarded-value pill are explanatory output only. They must
not resemble interactive checkboxes or controls.

## Layout constraints

- Do not add a column for criteria, raw subtotals, or adjustments.
- Preserve the established table proportions and compact printable layout.
- Criterion indicators must work in ordinary HTML and print CSS without
  relying on interactive Vaadin components.
- The three criterion states must remain distinguishable in a normal color
  print. Their symbol shapes also provide the distinction when color fidelity
  is poor.
- Long requirement text, criteria, comments, and adjustment labels must wrap
  within their existing cells rather than overlap neighboring content.
- PDF output must contain no controls, popovers, hover behavior, or internal
  criterion keys.
