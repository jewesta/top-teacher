# Level of Expectations

The level of expectations is the app's English domain name for the German
school concept "Erwartungshorizont". It belongs to exactly one exam and adds
the requirements used to mark pupil results to the exam structure authored in
`Aufgaben`. The EH is not the authority for parts, performance categories, or
tasks.

Result-entry behavior is specified in
[LevelOfExpectationsResults.md](LevelOfExpectationsResults.md). PDF behavior is
specified in
[LevelOfExpectationsPdfExport.md](LevelOfExpectationsPdfExport.md).

## Structure

The assessment structure is hierarchical:

1. Part
2. Performance category
3. Task
4. Requirement

### Part

A part is the top-level subject-specific section inside an exam, for example
"Klausurteil A: Schreiben mit Leseverstehen". It has a title and sort order.
Its regular point budget is aggregated from the explicit maxima of its tasks.
The EH additionally shows the regular and bonus requirement points distributed
below it.

### Performance category

A performance category groups tasks by assessment area, for example content or
language performance. It has a title, optional markdown description, and sort
order. Its regular point budget is aggregated from the explicit maxima of its
tasks. The EH additionally shows the regular and bonus requirement points
distributed below it.

### Task

A task represents an actual exam task and groups its later assessment
requirements. It has a title, Markdown content, an explicitly assigned integer
maximum, and a sort order. The task maximum is authored in `Aufgaben`; it is not
derived from the requirements. Non-bonus requirements subsequently distribute
that budget in EH, while bonus requirements remain separate.

### Material

Material is a parallel branch below a part rather than another level between a
task and its requirements. It represents source texts, historical sources,
diagrams, data, and similar material handed out with the exam.

- A material belongs to exactly one part.
- It has a title, Markdown content, and an explicit order among the other
  materials of that part.
- It is authored in the dedicated `Material` tab.
- Tasks refer to material in their wording; they do not own or duplicate it.

### Requirement

A requirement is the deepest structural level and the boundary at which
half-point results are rounded. It has:

- a markdown description;
- an explicitly assigned integer maximum;
- a bonus flag;
- a sort order;
- zero or more optional criteria embedded in its description.

The editor may temporarily contain a zero-point requirement while the design is
incomplete. A completed requirement must have a meaningful positive maximum,
and the regular requirements of its task must together distribute that task's
explicit maximum.

## Authoritative budgets and distributions

The grading scale defines the authoritative regular maximum for the exam. Task
maxima define how that exam budget is divided, and regular requirement maxima
define how each task budget is assessed. A complete design therefore satisfies
both equalities:

```text
sum(task maxima) = grading-scale maximum
sum(regular requirement maxima for a task) = that task's maximum
```

- Too few task points or too few regular requirement points make the design
  incomplete but remain saveable as an intermediate state.
- Too many task points or too many regular requirement points are errors. The
  editor may temporarily display the pending over-allocation, but it must not
  save it.
- Bonus requirements do not count toward either equality.
- Regular and bonus totals are displayed separately, for example
  `Summe: 98 (+ 2)`.
- A task's distribution indicator uses the concise `n / m` relationship,
  supplemented by remaining or excess points where needed. An exact match does
  not add a redundant `passend` label.
- EH and Results use the same aggregate-points badge. Its label is left-aligned.
  Regular points are right-aligned to a fixed axis, while bonus points are
  left-aligned beyond it. Fixed-width number slots keep changing values from
  shifting the badge layout.
- The EH percentage chip matches the aggregate-points badge height.

Changes to pending task maxima, requirement maxima, and bonus flags must update
the affected task, category, part, and exam information immediately.

## Criteria

Criteria identify concrete aspects within a requirement. They help the marker
work quickly and consistently, but the ministry or another authority may define
a requirement too holistically for meaningful criteria.

Consequently:

- a requirement may have no criteria at all;
- a criterion-free requirement is valid and complete;
- once a requirement contains criteria, their configured values must add up
  exactly to the requirement maximum before the requirement is complete.

An under-allocated criterion set is a saveable intermediate state. An
over-allocated set may be displayed while editing but must not be saved.

### Tag syntax

Criteria are declared with markdown links in the `eh:` namespace:

```markdown
[correct tense](eh:1)
[complete explanation](eh:2/2)
[supporting detail](eh:3/0,5)
```

The canonical syntax is:

```text
eh:<key>[/<points>]
```

- The key is stable and unique inside its requirement.
- Omitting the point value defaults it to one, so `eh:1` and `eh:1/1` are
  equivalent.
- A criterion value must be positive and use half-point increments.
- Criterion values are not limited to the convenient presets through four
  points. Larger half-point values up to and including 999 remain available as
  an explicit custom entry.
- Both comma and dot are accepted as decimal separators. The editor generates
  commas and the UI always displays values with the German comma form.
- Malformed definitions and duplicate keys are validation issues; they must not
  silently turn a requirement into a valid criterion-free requirement.

The UI displays a criterion's point value in its pill rather than the internal
key. Accessible text retains the criterion identity as well as its value.
In the Markdown preview, the criterion highlight encloses both the text and
its point pill.

All criterion values are represented internally as integer half-point units.
Floating-point arithmetic must not be used for point calculations.

### Markdown editor interaction

The Markdown toolbar provides a criterion command rather than requiring the
user to construct an `eh:` URL manually. Its icon is a compact pill containing
`P`, representing points without implying either a fixed value or a visible
criterion number. The button is labelled `Kriterium mit Punkten markieren`.

The command opens a compact value selector whose visual arrangement separates
whole- and half-point values:

```text
      | 0,5
  1   | 1,5
  2   | 2,5
  3   | 3,5
  4   | 4+
```

The eight numeric entries are ordinary choices. Selecting one wraps the
selected criterion text with the next stable key and the chosen value. If no
text is selected, the command uses the word under the cursor. The one-point
choice omits the redundant value, so it generates `eh:<key>` rather than
`eh:<key>/1`. Decimal presets use a comma.

`4+` is labelled accessibly as `Andere Punktzahl eingeben`. It is an escape
hatch rather than a point value:

- it inserts `eh:<key>/?`;
- it immediately selects only the `?` and returns focus to the editor so the
  user can replace it by typing;
- the parser accepts a comma or dot in the entered value;
- any positive half-point value up to and including 999 is valid.

If the placeholder remains, the tag is still recognized as a criterion and
its key remains reserved. The preview renders any invalid point value as `?`
using the ordinary point-pill design, and the targeted validation message asks
the user to assign a valid point value. This malformed value is an error and
prevents saving; it must not be mistaken for a criterion-free requirement.

Opening the selector while the cursor or selection is inside an existing
criterion shows its current preset. A valid custom value above four, or an
invalid placeholder, selects `4+`. Choosing another numeric entry changes only
the point value and preserves the criterion text and stable key. Removing the
criterion is a separate, explicit action in the selector rather than an
implicit toggle of the toolbar button.

The criterion command is a TopTeacher-owned Markdown extension, using the
editor library's command child-panel mechanism. The reusable Markdown component
provides extension hooks but must not contain EH point semantics or criterion
presentation rules; no fork of the underlying editor is required.

## Editing ownership and shared tree behavior

The exam tabs follow the authoring workflow `Aufgaben` → `Material` → `EH`.
The former `Notizen` tab and its exam-note persistence are no longer part of
the application.

`Aufgaben`, `Material`, and EH are separate views of the same exam definition
and use the same tree-section rendering and collapse behavior.

- `Aufgaben` is the sole editor for parts, performance categories, task titles,
  task Markdown, task maxima, and their order.
- `Material` edits ordered material below read-only part headings.
- EH renders part, category, and task data as read-only context and edits only
  requirements.
- Each section header has an explicit control for that section and, when the
  section has descendants, a second control for its complete subtree. Clicking
  the rest of the header does not expand or collapse anything.
- A subtree or whole-tree control offers expansion until every section in its
  scope is expanded. It offers collapse only when the complete scope is
  expanded.
- Each tab keeps its own collapse and dirty state. Refreshing or switching tabs
  must not silently discard unsaved work.

After the first result is recorded, `Aufgaben` and `Material` become read-only.
Their Markdown is then rendered as content instead of being left in disabled
editor controls. EH continues to permit only the narrowly safeguarded
requirement corrections described below; its ancestor hierarchy remains
read-only before and after that boundary.

## Derived design state

The level of expectations has no separately persisted lifecycle state. Its
state is derived from its contents and existing results:

- **Incomplete and editable:** one or more task, requirement, or criterion
  allocations are missing or otherwise invalid.
- **Complete and editable:** the grading scale matches the task budgets, each
  task budget matches its regular requirements, all criterion allocations are
  valid, and no pupil results exist.
- **Complete and locked:** pupil results exist, so structure, point maxima,
  bonus status, ordering, and criterion identities may no longer change. Only
  requirement corrections that preserve result-bearing semantics remain
  possible.

The EH tab represents these states with an hourglass, tick, and lock respectively.

## Validation presentation

Validation issues are presented in the status tray at the bottom of the EH
designer.

- The tray peeks into view whenever the EH uses validation.
- Its badge shows the number of messages and the most severe message level.
- Exam-level allocation messages are not links because they have no single edit
  target.
- Task-distribution messages identify and link to the affected task.
- Requirement-specific messages identify their task and requirement. Activating
  one expands, scrolls to, and briefly emphasizes the affected requirement.
- Missing allocations are warnings. Excess allocations and malformed values are
  errors.
- A requirement with criterion validation findings shows the first finding
  inline immediately before its `Max. Punkte` control. This uses the same
  validator result as the status tray, without repeating the requirement label.
  Warnings use yellow and errors use red. The message updates while either the
  criterion definitions or the requirement maximum changes. Criterion-free
  requirements do not show this message.
- An empty tray remains compact when activated.

## Bonus points

Regular and bonus points are aggregated independently.

- Regular requirements distribute their task's maximum.
- Bonus requirements are shown in parentheses.
- After every raw requirement result has been rounded locally, awarded regular
  and bonus points are combined within their task. The effective task result is
  capped at that task's explicit maximum.
- The effective exam result is the sum of those individually capped task
  results. Surplus bonus points from one task cannot compensate for missing
  points in another task.
- The UI continues to show entered regular and bonus points separately even
  when the effective total is capped.

For example, suppose a 100-point exam has a 60-point task and a 40-point task.
If the first task receives 58 regular plus five bonus points, its effective
result is capped at 60. If the second task receives 35 points, the effective
exam result is 95, not 98. The entered regular and bonus values remain visible
for assessment transparency.
