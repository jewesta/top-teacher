# Demo Data

Demo initialization creates synthetic pupils, course assignments, and two editable
exam designs without pupil results. It uses the ordinary initialization workflow;
changing the bundled seeds does not migrate or replace exams in existing user
databases.

## Exams

- English: `1. Klausur Shakespeare`, retaining its existing 150-point design.
- Spanish: `Abiturprüfung 2026 Spanisch (Demo)`, replacing the former
  `Examen No 3: Migración` seed. Its course and exam use the existing
  `Qualifikationsphase ab '25` grading scale with a maximum of 160 points.
  No additional grading scale is created.

## Spanish exam

The design adapts the supplied 2026 Spanish task sheet and the levels of
expectations for Parts A and B. It demonstrates the supported authoring model;
it is not a facsimile or an official exam document. Teacher-packet section
numbers and repeated page headers are not pupil tasks.

| Part | Content | Language performance | Total |
| --- | ---: | ---: | ---: |
| A: Schreiben/Leseverstehen integriert | 44 | 66 | 110 |
| B: Sprachmittlung | 20 | 30 | 50 |
| Exam | 64 | 96 | 160 |

Part A contains summary (14), analysis (12), and discussion (18) tasks.
Only the original task 3.1 is included, presented as Aufgabe 3. Task 3.2 and
alternative-task selection remain outside this demo. The two optional
additional criteria from analysis and discussion remain two-point bonus
requirements and do not increase the regular maximum.

Each part has a content category with full task details enabled and a language
performance category with task details disabled for the pupil sheet.
Both language categories retain their complete EH and points. Part A's language
requirements have the individual point allocations from the source. Part B
retains the source's grouped budgets: one content requirement worth 20 points
and three language requirements worth 10 points each. Their individual rubric
rows remain explanatory content within those requirements, without invented
per-row point allocations.

Requirement wording follows the original EH, including its sentence-completing
verbs after the shared pupil heading. It must not be paraphrased into nominal
labels or rewritten examples. Criterion markup may highlight original text but
does not change the displayed words. Shared wording for language correctness is
repeated where necessary to retain its context within TT's existing groups.

Each part includes its source text, attribution and source URL. Original source
lines use the supported numbered Markdown block; vocabulary annotations use
local Markdown footnotes. Part A additionally includes its introductory hints.
Permitted dictionaries appear once in the exam closing text without the teacher
document's section number. No images are included.

## Persistence

New seed records contain explicit task text, budgets, and category print settings.
Reapplying demo inserts preserves existing records' edited text and point
budgets. Demo initialization itself remains the explicit database replacement
operation described by the application. Pupil print generation remains deferred.

The Spanish demo places permitted aids as the first, inline material in Part A.
Its two source materials retain dedicated pages. The exam closing text is empty.
See [Pupil task-sheet PDF export](ExamTaskSheetPdfExport.md).
