# Level of Expectations

The level of expectations is the app's English domain name for the German
school concept "Erwartungshorizont". It belongs to exactly one exam, describes
the grading structure of that exam, and acts as the template for entering pupil
results.

The persisted markdown tag namespace remains `eh:` for now because it is short,
already part of stored data, and mirrors the German UI tab label.

## Structure

The structure is hierarchical:

1. Part
2. Performance category
3. Task
4. Requirement

### Part

A part is the top-level subject-specific section inside an exam, for example
"Klausurteil A: Schreiben mit Leseverstehen". It has:

- title
- sort order
- aggregated points from all requirements below it

### Performance Category

A performance category groups tasks by assessment area, for example content or
language performance. It has:

- title
- optional markdown description
- sort order
- aggregated points from all requirements below it

### Task

A task groups concrete requirements. It has:

- title
- sort order
- aggregated points from its requirements

### Requirement

A requirement is the deepest structural level. It is the unit for manual point
entry during result entry. It has:

- markdown description
- maximum points
- bonus flag
- sort order

A requirement does not make sense without points. The database technically
allows `0` so new requirements can be created incrementally, but before result
entry a real requirement should have a positive point value.

## Criteria

Inside a requirement's markdown description, small criteria can be marked for
the teacher. Criteria are checkable helper markers and do not calculate points.

Syntax:

```markdown
[correct tense](eh:1)
```

`eh` is the tag namespace and `1` is the criterion key inside the requirement.
The teacher-facing renderer highlights the criterion and shows the key as a
small badge. During result entry each criterion can be checked per pupil.

Important rules:

- Criteria are synchronized from requirement markdown links.
- The `criterion_key` is unique per requirement.
- Removed criteria are deactivated instead of hard-deleted.
- Criteria are teacher-facing, not pupil-facing.
- Pupil PDFs remove criteria markers and highlighting from the markdown.

## Pupil Results

Results are entered for `pupil x requirement` and optionally for
`pupil x criterion`.

### Requirement Result

For each pupil and requirement, the app stores:

- achieved points
- optional note

Points are assigned manually. Criteria can guide the teacher's work, but they
are not the source of point calculation.

### Criterion Result

For each pupil and criterion, the app stores:

- achieved / not achieved

This information is for the teacher's internal workflow.

## Points And Bonus Points

Regular points and bonus points are aggregated separately.

- Regular requirements count toward the regular total.
- Bonus requirements are displayed in parentheses, for example `(2)`.
- Aggregations show bonus points separately, for example `Summe: 98 (+2)`.
- The grading scale defines the fixed regular maximum.
- The regular points of the level of expectations must match the grading scale
  maximum.
- Bonus points can raise the effective total only up to the grading scale
  maximum.

Example with a grading scale maximum of 100 points:

- regular achieved: 99
- bonus achieved: 4
- effective total: 100

The UI still shows regular and bonus points separately so the entered data stays
visible.

## Notes

An exam can have several note sections. Each section has:

- title
- markdown description
- sort order

These sections are free-form and are not modeled as fixed categories.

## Grading Scale

The grading scale belongs to the course, not directly to the exam or level of
expectations. It defines:

- name
- maximum points
- fixed mapping from point ranges to grade points and grade levels

The level of expectations must be compatible with the grading scale maximum.

## PDF Export

The pupil-facing PDF export is based on the level of expectations and one
pupil's results.

Rules:

- Markdown is converted to safe HTML before export.
- Criteria markers are removed from pupil PDFs.
- The table structure follows the requirements.
- Notes and grading scale are rendered as separate sections.
- Bonus points remain visible in parentheses.
