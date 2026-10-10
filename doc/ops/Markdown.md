# Markdown

TopTeacher's Markdown editors and read-only views share the following rendering
conventions. Material-specific footnotes and managed images are specified in
[LevelOfExpectations.md](LevelOfExpectations.md#material).

## Alerts

All current Markdown editors provide a separate `Hinweisblock einfügen...`
toolbar command. Its dialog offers the five GitHub alert types with German
labels: `NOTE` (Hinweis), `TIP` (Tipp), `IMPORTANT` (Wichtig), `WARNING`
(Warnung), and `CAUTION` (Vorsicht). The dialog accepts Markdown text and can
prefill selected complete source lines. Cancel stays on the left and insert on
the right, following the shared dialog convention.

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
existing level-of-expectations PDFs also render these alerts as styled boxes
with vector icons. All five SVGs are bundled locally; rendering needs no GitHub
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
> Dritte Zeile.

[^1]: Ein erläuternder Hinweis.
```

Each source line after the marker is one displayed line. Each block starts at
1; an explicitly empty quoted line also consumes a number. The marker and
quote prefixes are hidden. The left margin shows only the first line number
and multiples of five: 1, 5, 10, 15, and so on. All intervening lines still
count, and the number column keeps the same width on unlabelled lines.
Inline formatting, links, and footnote references remain supported. Footnote
definitions stay outside the block and appear in the local footnote list.

Numbered lines never wrap. Text that exceeds the rendering area's width is
clipped on the right while the complete source remains stored. The preview
identifies affected line numbers so the author can insert deliberate breaks.
The preview's available width is not a guarantee that a line fits the eventual
printed page. Font, size, and print width will determine how much fits, but
literal numbering must remain stable when exam-sheet export is implemented.

Block formatting such as lists and headings does not create additional lines
inside a numbered block. Numbered blocks cannot be nested. Other Markdown
surfaces can render manually authored numbered blocks; their toolbar command
is currently enabled only for Material.
