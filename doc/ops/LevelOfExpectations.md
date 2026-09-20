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
[supporting detail](eh:3/0.5)
```

The canonical syntax is:

```text
eh:<key>[/<points>]
```

- The key is stable and unique inside its requirement.
- Omitting the point value defaults it to one, so `eh:1` and `eh:1/1` are
  equivalent.
- A criterion value must be positive and use half-point increments.
- The canonical decimal separator in the tag is a dot. The UI displays values
  using the user's locale.
- Malformed definitions and duplicate keys are validation issues; they must not
  silently turn a requirement into a valid criterion-free requirement.

The UI displays a criterion's point value in its pill rather than the internal
key. Accessible text retains the criterion identity as well as its value.

All criterion values are represented internally as integer half-point units.
Floating-point arithmetic must not be used for point calculations.

## Derived design state

The level of expectations has no separately persisted lifecycle state. Its
state is derived from its contents and existing results:

- **Incomplete and editable:** one or more required point allocations are
  missing or otherwise invalid.
- **Complete and editable:** grading-scale and criterion allocations match and
  no pupil results exist.
- **Complete and locked:** pupil results exist, so structure, point maxima,
  bonus status, and criterion identities may no longer change.

The EH tab represents these states with a pen, tick, and lock respectively.

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
