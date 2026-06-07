# Spreadsheet Grid

`SpreadsheetGrid` is a reusable Vaadin `Grid` variant for spreadsheet-like overview tables. It was introduced for the exam evaluation view, where pupils are listed vertically and many compact aggregation columns are shown horizontally.

The important UX goal is this:

- first columns can stay frozen for identity and summary data
- data columns stay narrow
- headers are tilted so long labels remain readable without consuming too much width
- the table can scroll horizontally like a spreadsheet

## Component

Java component:

`topteacher-app/src/main/java/de/westarps/topteacher/ui/component/SpreadsheetGrid.java`

Theme styles:

- `topteacher-app/src/main/frontend/themes/topteacher/styles.css`
- `topteacher-app/src/main/frontend/themes/topteacher/components/vaadin-grid.css`

Use `addSpreadsheetColumn(...)` instead of plain `addColumn(...)` when a column should receive the tilted header treatment.

```java
private final SpreadsheetGrid<MyRow> grid = new SpreadsheetGrid<>(MyRow.class, false);

grid.addSpreadsheetColumn(MyRow::name, "Schüler")
		.setFrozen(true)
		.setAutoWidth(true)
		.setFlexGrow(0);

grid.addSpreadsheetColumn(MyRow::total, "Gesamt")
		.setFrozen(true)
		.setWidth("5.5rem")
		.setFlexGrow(0);

grid.addSpacerColumn("5.5rem");
```

## Header Layout

The header text is rendered as a small component:

- outer `Div`: `tt-spreadsheet-header`
- inner `Span`: `tt-spreadsheet-header-label`
- header cell part name: `tt-spreadsheet-tilted-header-cell`

The label is rotated by CSS. It is anchored near the lower center of the header cell, but its transform origin is `left bottom`. This keeps the tilted label visually centered while avoiding overlap with table body rows.

Long labels are shortened in Java before rendering. The native `title` attribute keeps the full text available on hover.

The default maximum label length is intentionally conservative. Very long labels otherwise disappear behind the top edge of the grid, and making the header tall enough for every possible label would waste too much space.

## Frozen Columns

Frozen columns must stay visually opaque. Otherwise tilted scrolled headers remain visible underneath the frozen cells while horizontal scrolling.

The current CSS deliberately gives frozen cells a solid background and a higher stacking order:

```css
.tt-spreadsheet-grid::part(first-column-cell),
.tt-spreadsheet-grid::part(frozen-cell) {
	background: var(--lumo-base-color);
	z-index: 3;
}
```

For the evaluation view, the useful frozen set is:

- `Schüler`
- `Gesamt`
- `Note`

This keeps the row identity, total score, and grade visible while scrolling through the aggregation columns.

## Right Spacer Column

The rightmost tilted header needs empty space to lean into. Without it, the final label is clipped or visually cramped because there is no column to its right.

`SpreadsheetGrid.addSpacerColumn(width)` adds a narrow empty column with the same header behavior. It is not data, just reserved visual space.

## Vaadin Grid CSS Gotchas

Vaadin grid header content crosses several styling boundaries:

- `::part(...)` styles the internal grid cell parts
- component theme CSS under `components/vaadin-grid.css` reaches grid internals
- some rendered `vaadin-grid-cell-content` elements still need light-DOM styling from `styles.css`

The expensive bit was the header cell content. Styling only the grid part was not enough; the slotted `vaadin-grid-cell-content` still clipped or painted over rotated labels.

The current selector is intentionally specific:

```css
.tt-spreadsheet-grid > vaadin-grid-cell-content:has(.tt-spreadsheet-header) {
	background: transparent !important;
	overflow: visible !important;
	text-overflow: clip !important;
}
```

This is why the generic class names matter. The rule should affect only spreadsheet headers, not normal Vaadin grids.

## Alignment Lessons

The rotated label should not be centered with a normal flex layout. That centers the text box, but the rotated text then protrudes downward into the table body.

The stable arrangement is:

- fixed tall header cells
- header wrapper with `position: relative`
- label with `position: absolute`
- label placed at the bottom
- rotation around `left bottom`

This gives the label enough room above the data rows and makes the column header feel spreadsheet-like.

## Testing

Unit tests cover the Java-side contract:

`topteacher-app/src/test/java/de/westarps/topteacher/ui/component/SpreadsheetGridTests.java`

The tests verify:

- the grid receives the spreadsheet CSS class
- spreadsheet columns receive the tilted-header part name
- spacer columns are non-flex fixed-width columns
- invalid header abbreviation settings are rejected

CSS behavior still needs browser verification when changed. The component is especially sensitive to Vaadin internals, clipping, overflow, stacking order, and frozen columns.
