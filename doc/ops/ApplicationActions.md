# Application Actions

## Action bars

- List, form-editor, designer, and nested exam-tree actions use the shared action
  bar. Form-editor actions appear at the top of the tab, before descriptions
  and fields, rather than below the form. This includes single-record editors,
  bulk status changes,
  grading-scale editing, backup settings, and file-access mappings.
  Task and requirement reorder/delete bars appear above their Markdown content
  within the section, below its header. The `Aufgaben` category action bar also
  appears below its header and before `Beschreibung`; its eye toggle controls
  future task-sheet details for that category.
- Action bars adapt to their visible elements. A lone action shows its icon
  and German label as a neutral secondary button. With two or more visible
  elements, actions show only their icons, without a persistent button
  background. Filters and selectors count as elements, as do disabled actions;
  hidden elements and attached dialog hosts do not. A lone filter or selector
  remains unchanged.
  Adding, removing, hiding, or showing elements changes the presentation in
  place without rebuilding the bar or disturbing selection or dirty state.
- Action buttons retain German tooltips and accessible names in both modes.
  No action, including save, is highlighted as primary. Existing validation,
  dirty-state, and read-only safeguards still apply.
- A button that opens a dialog or confirmation ends its label or icon tooltip
  with an ellipsis (`...`). Direct actions, inline form navigation, menu
  triggers, and final dialog confirmation buttons have no ellipsis unless they
  open another dialog. This includes discard and delete actions in the shared
  exam tree and results editor.
- All action bars use the compact horizontal spacing of the exam-tree action
  rows. Icon-only actions have no extra horizontal padding. This includes form
  bars, list bars with `Schnellfilter`, assignment bars, designer bars, and the
  add/reorder/delete rows within Aufgaben, Material, and EH. Filters do not
  increase the gap between neighboring actions. Form and tree content spacing
  and dialog button bars remain unchanged.
- Action bars use normal-sized controls, not the compact variant. This applies
  to buttons (including labeled additions), filters, selectors, and download
  menus and the nested exam-tree action rows. Point steppers, section-header
  toggles, and the Markdown editor's own formatting controls are unaffected.
- `Neu` opens a new-record form and `Anlegen` submits that form. Both follow
  the adaptive rule. The label, tooltip, and icon of `Anlegen` switch to
  `Speichern` and a check when editing an existing record.
- Contextual tree additions such as `Klausurteil hinzufügen` retain their plus
  icon, label, and neutral secondary background regardless of element count,
  and precede the other actions in their action bar. A plus icon alone does
  not imply a permanently labeled add action.
- Where present, `Schnellfilter` always comes first, before action buttons.
  List filters and selectors precede the `Neu` action, which comes last. In
  Klausuren, the order is `Schnellfilter`, `Kurs`, then `Neu`.
- Every `Schnellfilter` uses the shared placeholder `Suchbegriff`, including
  pupil assignments and subject settings. It does not repeat context-specific
  names or require a wider field to accommodate them. The label remains
  `Schnellfilter`.
- Download menu triggers follow the same adaptive rule and match the width and
  height of other icon actions in a multi-element bar. Menu entries and dialog
  buttons keep their labels. Selector navigation buttons remain icon-only.
  Field-level controls and contextual tree controls remain beside the content
  they affect.
- `Zurücksetzen` is the explicit system-settings-section exception: its
  destructive action keeps its label and remains below the explanatory text.
  Confirmation dialogs remain unchanged.

See [System Settings](SystemSettings.md) for the fixed navigation and contextual
action placement within `Systemeinstellungen`.

## Selection indicators

Dropdowns use a dot to mark selected options, including single- and
multi-select combo boxes. Their selection highlight, spacing, keyboard
navigation, and accessible selected state remain unchanged. Checkmarks used
for completion and actual checkbox controls are unaffected.

## Editor-tab indicators

Normal editing of an existing record is the unmarked default: the editor tab
shows only its label, without a pencil. New-record forms retain their plus
icon. Locked records retain their lock icon and explanatory tooltip. Multiple
selected records show their selection count without a pencil.

The tab's accessible name continues to describe its mode, even when no icon
is displayed. Selection counts retain their accessible description and tooltip.
EH completion and lock indicators remain unchanged.

## Grading-scale lock

Selecting a grading scale already used by an exam shows a lock on the
`Notenschlüssel` editor tab. Its tooltip and accessible name
explain the reason:

`Dieser Notenschlüssel wird bereits von Klausuren verwendet und kann nicht mehr geändert werden.`

This explanation is not repeated as a message in the form. Name, maximum
points, lifecycle, and grade ranges remain read-only; save and archive remain
visible but disabled. Archive is still hidden for a new or already archived
scale. Selecting an unused scale removes the lock and enables archive;
creating a new scale restores the new-record plus and the lone labeled
`Anlegen` action.

## Unexpected display errors

Unexpected errors while rendering a page show a German application error page
instead of framework instructions, stack traces, or exception details. Technical
diagnostics remain available in the browser console.

The page says `Das hat leider nicht geklappt` and explains that an error occurred
while displaying the page. `Seite neu laden` reloads the current URL, including
when the application is deployed under a context path. Before reloading, the page
warns that unsaved input may be lost. Reloading is always an explicit user action.

## Icon state toggles

The category eye toggle and material page-mode toggle use the same icon-toggle
component. Each mode has its own glyph and German action tooltip. Both modes
retain the same centered icon-only button layout and normal toolbar styling,
without a persistent selected-state background. Ordinary hover feedback stays
available. The accessible `aria-pressed` state follows the current draft value;
changes use the owning editor's normal save/discard and locking behavior.

## Export dialogs

PDF options are selected in shared application dialogs with the standard
`Abbrechen`/primary `Laden` button bar. The Aufgabenblatt and individual
Ergebnisbogen download icons open their dialogs directly. The Ergebnisbogen
dialog offers version and current-pupil/all-pupils choices. The Auswertungen
download icon directly loads Punktetabelle without a menu. See
[Pupil task-sheet PDF export](ExamTaskSheetPdfExport.md) and
[Level of expectations PDF export](LevelOfExpectationsPdfExport.md#download-dialogs).
