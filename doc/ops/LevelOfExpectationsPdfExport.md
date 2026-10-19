# Level of Expectations PDF Export

The level-of-expectations PDF presents one pupil's completed assessment as a
static, printable document. It is a result document, not the future pupil exam
sheet made from task and material content. It uses the assessment structure
defined in [LevelOfExpectations.md](LevelOfExpectations.md) and the awarded
values defined in
[LevelOfExpectationsResults.md](LevelOfExpectationsResults.md).

## Download dialogs

The result-sheet download icon in `Ergebnisse` opens `Ergebnisbogen laden`
directly. Its shared application dialog contains two radio groups in this order:

- `Version`: `Schüler:innen` (default) and `Lehrer:innen`.
- `Schüler:in`: the current pupil as `Surname, Name (Aktuell ausgewählt)`
  (default), then `Alle Schüler:innen` for all pupils assigned to the exam.

Neither group label has a colon. `Laden` downloads the selected version for
one pupil or as a combined document for the whole exam; `Abbrechen` cancels.
The action remains unavailable without a selected pupil or while results are
unsaved. Changing export scope does not change the pupil selected in the editor.

In `Auswertungen`, the download icon directly downloads `Punktetabelle` without
a menu or dialog. Result-sheet downloads are offered only in `Ergebnisse`.

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
| Grading scale | Yes | Yes |
| Draft or teacher watermark | No | Optional |

## Common structure

The existing compact table structure remains authoritative. Requirements keep
their number, text, maximum, and achieved-result cells. Task summaries retain
their existing position and use the task's explicitly authored maximum. Bonus
points remain visibly separate from regular points, including the existing
parenthetical presentation where applicable.

The PDF must use the same requirement-local rounding as the Results view:

```text
raw requirement result = criterion subtotal + adjustment
exported requirement result = round half-up(raw requirement result)
```

For a criterion-free requirement, the directly awarded value is rounded by the
same rule. Only the rounded requirement result contributes to task, category,
part, exam, grading, and export totals. No half point is aggregated beyond its
requirement.

## Effective totals and grades

All exported totals and grade calculations use the same task boundary as the
Results and Evaluation views.

```text
effective task result = min(task maximum,
                            rounded regular results + rounded bonus results)
effective exam result = sum(effective task results)
```

The export still presents regular and bonus awards separately. The cap affects
the effective task and exam result used for grading; it does not hide the
recorded awards. Bonus points from one task therefore cannot compensate for
missing points in another task.

## Pupil version

The pupil version is a clean assessment document rather than a reproduction of
the marker controls.

- Criterion annotations are removed while their text remains in the document.
- No criterion identity, point pill, checkbox state, or status symbol is shown.
- The existing achieved-result cell contains the rounded requirement result.
- Adjustment details and requirement comments are omitted.

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
  It uses the label `Frei vergebene Punkte`, a leading plus sign, and the
  German comma form for a half point, for example
  `Frei vergebene Punkte: +0,5`.
- The requirement comment remains in the neighboring requirement-text cell.
- The optional watermark remains available.

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

## Pupil task-sheet export

The separate pupil-facing exam sheet generated from `Aufgaben` and `Material`
is specified in [Pupil task-sheet PDF export](ExamTaskSheetPdfExport.md).
It reuses the A5-to-A4 imposition without altering the existing pupil and teacher
EH result PDFs. GitHub-style Markdown alerts retain their shared semantics,
as specified in [Markdown.md](Markdown.md#alerts).
