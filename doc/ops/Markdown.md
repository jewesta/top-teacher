# Markdown

TopTeacher's Markdown editors and read-only views share the following rendering
conventions. Material-specific footnotes and managed images are specified in
[LevelOfExpectations.md](LevelOfExpectations.md#material).

## Empty criterion editors

An empty criterion editor is valid. Opening its point selector must not cause
an error. Point options stay disabled and show `Text markieren` until a word
or suitable text selection is available. Existing word and selection behavior
remains unchanged for non-empty text.

## Alerts

All current Markdown editors provide a separate `Hinweisblock einfügen...`
toolbar command. Its dialog offers the five GitHub alert types with German
labels: `NOTE` (Hinweis), `TIP` (Tipp), `IMPORTANT` (Wichtig), `WARNING`
(Warnung), and `CAUTION` (Vorsicht). The dialog accepts Markdown text and can
prefill selected complete source lines. With the cursor or selection inside an
existing alert, the same command opens `Hinweisblock bearbeiten`, prefilled with
its current type and unquoted Markdown body. `Übernehmen` replaces that complete
alert; it does not insert or nest a second alert. Neighbouring content, paragraph
breaks, formatting, and outer quote/list containers are retained. Cancelling
leaves the source and dirty state unchanged. Ordinary quotes, unknown types and
code examples are not treated as existing alerts. Outside an alert the original
insertion workflow remains available. Cancel stays on the left and insert/apply
on the right, following the shared dialog convention.

```markdown
> [!TIP]
> Markiere zuerst die **Schlüsselbegriffe**.

> [!IMPORTANT]
> Bearbeite beide Aufgaben.
```

The type marker occupies its own first line and is hidden when rendered. Alerts
use a colored border, a German heading, and the matching GitHub Octicon. Their
contents retain ordinary Markdown formatting, paragraph wrapping, links, and footnote
references. Ordinary quotes, unknown alert types, and examples inside code
blocks keep their ordinary Markdown behavior.

Alert rendering is shared by editable previews and read-only views. The
level-of-expectations and pupil Aufgabenblatt PDFs render these alerts as styled
boxes with visible vector icons for all five types. All five SVGs are bundled locally; rendering needs no GitHub
connection. Labels and borders preserve their meaning in monochrome printing. The future
pupil-facing exam-sheet export remains outside this implementation.

## Literal numbered source lines

Material editors additionally provide the separate `Zeilen nummerieren`
command. It wraps selected complete source lines in a top-level numbered block;
without a selection it wraps the current line or inserts a text placeholder.

```markdown
> [!NUMBERED]
> Erste Zeile mit **Hervorhebung**.[^1]
>
> Zweite gezählte Zeile.

[^1]: Ein erläuternder Hinweis.
```

Each source line after the marker is one displayed line. Each block starts at
1 for its first non-empty line. Empty and whitespace-only quoted lines remain
visible as spacing but have no number and do not advance the counter. The
marker and quote prefixes are hidden. The left margin shows only the first
counted line number and multiples of five: 1, 5, 10, 15, and so on. All other
non-empty lines still count, and the number column keeps the same width on
unlabelled and empty lines.
Line numbers use normal text at the source line's font size and baseline, not
superscript styling; footnote references remain superscript.
Inline formatting, links, and footnote references remain supported. Footnote
definitions stay outside the block and appear in the local footnote list.

Numbered lines never wrap. Text that exceeds the rendering area's width is
clipped on the right while the complete source remains stored. The preview
identifies affected counted line numbers, excluding blank lines, so the author
can insert deliberate breaks.
The preview's available width is not a guarantee that a line fits the eventual
printed page. Font, size, and print width will determine how much fits, but
literal numbering must remain stable when exam-sheet export is implemented.

Block formatting such as lists and headings does not create additional lines
inside a numbered block. Numbered blocks cannot be nested. Other Markdown
surfaces can render manually authored numbered blocks; their toolbar command
is currently enabled only for Material.

## Footnote list numbering

Editor previews and read-only views reserve enough font-relative space for the
complete footnote number, including two or more digits. The marker column grows
with the number of footnotes. Numbers must not be clipped or overlap the text;
wrapped footnote text keeps the normal hanging list indentation. Ordinary lists
and section layouts remain unchanged.
