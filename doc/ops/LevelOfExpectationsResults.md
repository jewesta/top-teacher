# Level of Expectations Results

The Results view records one pupil's performance against a level of
expectations. Criteria support the marker's judgement, but they are not a hard
gate: freely awarded points can credit valid work that does not match a
predefined criterion, or provide the entire result when no criteria exist.

The underlying assessment structure and criterion syntax are specified in
[LevelOfExpectations.md](LevelOfExpectations.md). PDF presentation is specified
in [LevelOfExpectationsPdfExport.md](LevelOfExpectationsPdfExport.md).

## Requirement result

The requirement is the smallest unit that contributes points to higher-level
totals. Its raw result may contain a half point, but the value is rounded
half-up before it contributes to task, category, part, exam, grading, or export
aggregates. No half point leaves its requirement.

```text
raw requirement result = criterion subtotal + freely awarded points
aggregated result = round half-up(raw requirement result)
```

The raw result may not exceed the requirement maximum. Without criteria, the
criterion subtotal is zero; the marker enters the full result as freely
awarded points in half-point steps.

## Criterion result

Each criterion result is an awarded point value rather than a boolean flag. It
uses integer half-point units internally and is constrained to the criterion's
configured value.

It has three visual states:

- zero points: unchecked;
- the criterion's full value: checked;
- any value between zero and full: indeterminate.

The checkbox remains the fastest marking action. Clicking it toggles between
zero and full marks. Clicking an indeterminate checkbox grants full marks
first; the next click returns it to zero.

## Inline marking in the requirement text

Criteria remain visible in their original position inside the requirement
text.

- The criterion pill displays awarded points rather than the internal
  criterion key.
- It uses the same visual styling as the EH pill and stays adjacent to its
  checkbox. Its width follows its current value.
- Its accessible description includes the criterion identity, awarded value,
  and available value.
- Clicking the pill opens a Vaadin popover below it, with an arrow pointing up
  to the pill.
- The popover contains the familiar compact minus/plus control with half-point
  steps and the criterion value as its upper bound.
- The pill, checkbox, popover control, and quick-marking control always display
  the same pending value.
- The inline highlight spans the criterion text, pill, and checkbox together.

The criterion-status line beneath the text reflects both full and partial
awards. It is absent when there are no criteria. For a single criterion it says
`Kriterium erfüllt`, `Kriterium teilweise erfüllt`, or `Kriterium nicht erfüllt`.
When all of multiple criteria have the same nonzero status, it says
`n Kriterien, alle voll erfüllt.` or `n Kriterien, alle teilweise erfüllt.`
Otherwise it begins `n Kriterien,` and reports the counts that apply:
`m voll erfüllt`, `p teilweise erfüllt`, both joined by `und`, or
`keines erfüllt` when neither count is positive. It updates with pending edits.

## Quick-marking column

The right-hand column provides a compact, top-to-bottom representation of the
same criterion results.

- Its top element is the familiar red aggregate badge rather than an editable
  points field.
- The badge shows the rounded requirement result.
- When rounding changes the raw result, the badge tooltip explains the raw and
  rounded values.
- Every criterion row contains its three-state checkbox followed by an
  always-visible compact minus/plus control.
- Aggregate badges, requirement totals, criterion controls, and freely awarded
  controls share one points-cell footprint and center their numeric value on
  the same vertical axis. Aggregate chips show `∑` or `∑∑` at the left while
  retaining their full labels for accessibility.
- Only aggregate badges and requirement totals have a permanent red-tinted
  background. Point steppers are transparent at rest; hover, keyboard focus,
  or the linked criterion highlight adds the tint without a border.
- The `Frei vergebene Punkte` label uses the same secondary text styling as the
  `n von m Punkten` label. Partial inline checkboxes show a dash.
- Changes made in either the text or quick-marking column update the other
  representation immediately.

### Freely awarded points

A non-negative `Frei vergebene Punkte` control always appears at the bottom of
the quick-marking column, aligned with the requirement comment field. It uses
half-point steps. With criteria, it credits work outside those criteria; without
criteria, it supplies the full requirement result. The rounded requirement
total above it is read-only in both cases.

Criterion awards and freely awarded points share the requirement's unrounded point
budget:

```text
criterion subtotal + freely awarded points <= requirement maximum
```

A deliberate free award reserves its points. Criterion controls cannot consume
that capacity until the free award is reduced. Conversely, fully awarded
criteria leave no capacity for free points. The available upper bounds must
update immediately after either side changes; an existing free award must not
be silently reduced by a later criterion action.

### Criterion-free requirements

A requirement without criteria shows no criterion list. Its freely awarded
points control occupies the same bottom position as for a requirement with
criteria, and can award up to the requirement maximum.

## Visual correspondence

Criterion keys remain the stable internal identity, but markers should not have
to match visible numbers between the requirement text and the quick-marking
column.

- Hovering any control in a quick-marking row highlights the corresponding
  inline criterion text, pill, and checkbox.
- Keyboard focus anywhere within the row keeps the same highlight active while
  focus moves between its controls.
- Hovering or focusing an inline criterion highlights its quick-marking row.
- The highlight must not change layout, scroll the view, or use an attention
  animation.
- Overlapping hover and focus states must not cause flicker or remove a
  highlight while either source remains active.
- Each quick-marking row is labelled accessibly with the actual criterion text
  and point values rather than only its hidden key.

## Comments

Every requirement may have a marker comment. Comments do not affect points.
They remain visually associated with freely awarded points because those
points will usually need an explanation when criteria are defined. The comment
field starts at two lines and grows with its content without a row limit.

## Regular and bonus aggregation

Rounded requirement results are aggregated separately for regular and bonus
requirements.

- Regular results contribute directly to the exam result.
- Bonus results remain visible separately.
- Applicable bonus points are capped so the effective result cannot exceed the
  grading-scale maximum.
- Pending edits update requirement, task, category, part, and exam badges
  immediately.

## Editing and saving

Result changes are collected for the selected pupil and saved together.

- Save is enabled only while the selected pupil has pending valid changes.
- Discard restores criterion values, freely awarded points,
  and comments to their persisted state.
- Switching or refreshing must not silently lose dirty edits.
- Once any result exists for an exam, the corresponding level of expectations
  enters its locked structural state.
