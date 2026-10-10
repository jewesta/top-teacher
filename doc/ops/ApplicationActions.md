# Application Actions

## Action bars

- List, form-editor, designer, and nested exam-tree actions use the shared action
  bar. Form-editor actions appear at the top of the tab, before descriptions
  and fields, rather than below the form. This includes single-record editors,
  bulk status changes,
  grading-scale editing, and backup settings.
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
- `Zurücksetzen` is the explicit tab-level exception: its destructive action
  keeps its label and remains below the explanatory text. Confirmation dialogs
  remain unchanged.

## Grading-scale lock

Selecting a grading scale already used by an exam shows a lock instead of the
edit pencil on the `Notenschlüssel` editor tab. Its tooltip and accessible name
explain the reason:

`Dieser Notenschlüssel wird bereits von Klausuren verwendet und kann nicht mehr geändert werden.`

This explanation is not repeated as a message in the form. Name, maximum
points, lifecycle, and grade ranges remain read-only; save and archive remain
visible but disabled. Archive is still hidden for a new or already archived
scale. Selecting an unused scale restores the edit status and enables archive;
creating a new scale restores the new-record status and the lone labeled
`Anlegen` action.
