# Pupil task-sheet PDF export

The download menu in `Aufgaben` offers `Anonym` and `Personalisiert...`.
Anonymous copies reserve a blank `Name:` line for photocopying. Personalized
output fills that same space with the selected assigned pupil's name, or exports
one copy for every assigned pupil. Both variants retain the same pagination.
Tasks and materials must be saved or discarded before downloading.

## Page format and reading order

Content flows through portrait A5 pages. Two consecutive A5 pages are placed
left to right on landscape A4, using the existing EH imposition. This is ordinary
reading order, not booklet imposition. An odd final A5 page leaves the right half
blank. Combined personalized output imposes each pupil's copy separately before
merging, so two pupils never share a physical sheet.

The opening starts with one 8 pt row: course followed directly by ` · ` and
the date on the left, and the name field on the right. The date has no label. The name underline starts after the label and extends to the right
margin. Anonymous copies leave it blank; personalized names shrink from 8 pt
only when needed to fit without changing pagination. The exam title and regular
maximum for the entire exam follow below. Each part starts on a fresh A5 page
and identifies its title and regular point maximum. Materials precede tasks within their part,
in the authored order. The closing text appears once after the final part. Page
numbers refer to the logical A5 pages within each copy.

## Material pagination

Every material has a persisted `Auf eigener Seite` setting, enabled by default
for new and existing materials. Its action bar contains a page-break icon toggle:
separate-page mode shows two page fragments separated by a dashed horizontal
line; flowing mode shows the matching closed page frame without that line.
Both SVGs share their dimensions, stroke and centered alignment. As with the
category eye toggle, the icon communicates the mode; neither state has a
persistent background highlight. The
German action tooltip is `Im laufenden Text platzieren` when enabled and
`Auf eigener Seite platzieren` when disabled. The accessible pressed state
matches the saved/draft setting. If enabled, the material starts on a fresh page
and the content following it starts on another fresh page. Material can span
several pages; its introduction, source and footnotes stay in the material flow.
If it is the first content in a part, the opening/part heading can share its page.
If disabled, the material flows into the available space, together with adjacent
inline materials or tasks. The setting follows normal save/discard, stale-write
protection and correction-mode locking, and survives exam copies, image imports,
authoring edits and reordering.

No special material type or title-based export logic exists for permitted aids.
The Spanish demo places `Zugelassene Hilfsmittel` first in Part A, with
`Auf eigener Seite` disabled, followed by its source on dedicated pages.

## Pupil-facing content

Visible performance categories print their title, description, tasks, task text
and regular point budgets. Categories with their eye toggle disabled print only
the category name and regular point maximum; their description and tasks remain
absent. All categories still contribute to part and exam totals.

Assessment requirements, criteria, bonus rubric rows, grades and results are not
part of a pupil task sheet. The task budget is authoritative for printed points.
No writing spaces or alternative-task mechanism are introduced.

TT's Klausurteil headings retain their horizontal divider. Authored Markdown
headings have no automatic divider; explicit thematic breaks remain supported.

Material Markdown uses the shared safe renderer, including authored logical
source lines, line markers at 1 and every fifth line, annotations, source links,
tables, local footnotes and managed images. Alert boxes have a wider left border
matching the alert icon's type color and a light gray background, consistent
with the editor preview. Logical line numbering continues
across page breaks. Footnote numbering is local to its material, with sufficient
space for multi-digit markers. The footnote divider spans only the width of
the “Fußnoten” heading. Numbered source text starts at 9 pt. Each block
shrinks uniformly only when its longest rendered line exceeds the available
width, including the line-number gutter and any enclosing container. Inline
formatting and footnote references participate in the measurement. Numbered
lines never wrap; their authored breaks and logical numbering remain stable.
Blocks fit independently, and ordinary text and footnote lists keep their usual
font sizes.

## Export routes

- `/export/exams/{examId}/task-sheet.pdf`: anonymous copy.
- `/export/exams/{examId}/pupils/{pupilId}/task-sheet.pdf`: assigned pupil copy.
- `/export/exams/{examId}/task-sheets.pdf`: all assigned pupils, in roster order.

No grade scale or results are required to print an authored exam. Export reads a
consistent saved design snapshot and never modifies the exam or its roster.
