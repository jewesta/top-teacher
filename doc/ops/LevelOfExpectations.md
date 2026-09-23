# Level of Expectations

The level of expectations is the app's English domain name for the German
school concept "Erwartungshorizont". It belongs to exactly one exam, defines
that exam's assessment structure, and provides the basis for marking pupil
results.

Result-entry behavior is specified in
[LevelOfExpectationsResults.md](LevelOfExpectationsResults.md). PDF behavior is
specified in
[LevelOfExpectationsPdfExport.md](LevelOfExpectationsPdfExport.md).

## Structure

The structure is hierarchical:

1. Part
2. Performance category
3. Task
4. Requirement

### Part

A part is the top-level subject-specific section inside an exam, for example
"Klausurteil A: Schreiben mit Leseverstehen". It has a title and sort order.
Its regular and bonus points are aggregated from all requirements below it.

### Performance category

A performance category groups tasks by assessment area, for example content or
language performance. It has a title, optional markdown description, and sort
order. Its regular and bonus points are aggregated from all requirements below
it.

### Task

A task groups concrete requirements. It has a title and sort order. Its regular
and bonus points are aggregated from its requirements.

### Requirement

A requirement is the deepest structural level and the boundary at which
half-point results are rounded. It has:

- a markdown description;
- an explicitly assigned integer maximum;
- a bonus flag;
- a sort order;
- zero or more optional criteria embedded in its description.

The editor may temporarily contain a zero-point requirement while the design is
incomplete. A completed requirement must have a meaningful positive maximum.

## Authoritative totals

The grading scale defines the authoritative regular maximum for the exam. A
complete level of expectations must assign exactly that number of regular
requirement points.

- Too few regular requirement points make the design incomplete but remain
  saveable as an intermediate state.
- Too many regular requirement points are an error. The editor may temporarily
  display the pending over-allocation, but it must not save it.
- Bonus requirements do not count toward the grading-scale maximum.
- Regular and bonus totals are displayed separately, for example
  `Summe: 98 (+ 2)`.
- EH and Results use the same aggregate-points badge. Its label is left-aligned.
  Regular points are right-aligned to a fixed axis, while bonus points are
  left-aligned beyond it. Fixed-width number slots keep changing values from
  shifting the badge layout.
- The EH percentage chip matches the aggregate-points badge height.

Changes to pending requirement maxima and bonus flags must update requirement,
task, category, part, and exam totals immediately.

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
- Criterion values have no artificial upper limit. Values through four points
  are convenient presets; larger values remain available as an explicit custom
  entry.
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

## Derived design state

The level of expectations has no separately persisted lifecycle state. Its
state is derived from its contents and existing results:

- **Incomplete and editable:** one or more required point allocations are
  missing or otherwise invalid.
- **Complete and editable:** grading-scale and criterion allocations match and
  no pupil results exist.
- **Complete and locked:** pupil results exist, so structure, point maxima,
  bonus status, and criterion identities may no longer change.

The EH tab represents these states with an hourglass, tick, and lock respectively.

## Validation presentation

Validation issues are presented in the status tray at the bottom of the EH
designer.

- The tray peeks into view whenever the EH uses validation.
- Its badge shows the number of messages and the most severe message level.
- Overall allocation messages are not links because they have no single edit
  target.
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

- Regular requirements count toward the grading-scale total.
- Bonus requirements are shown in parentheses.
- Awarded bonus points may raise the effective exam result only up to the
  grading-scale maximum.
- The UI continues to show entered regular and bonus points separately even
  when the effective total is capped.

For example, with a grading-scale maximum of 100, 99 regular points and four
bonus points remain visible as `99 (+ 4)`, while the effective result is 100.

## Notes

An exam may contain several free-form note sections. Each section has a title,
markdown description, and sort order. Notes are not fixed assessment categories
and do not contribute points.
