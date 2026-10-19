# Icon toggle buttons

Use `ui.component.IconToggleButton` for toolbar switches whose glyph represents
the current Boolean mode. Category task visibility and material pagination both
use it. Do not implement per-feature icon/label/pressed-state update methods.

Supply the initial value, a `State` for each value (icon component and German
action label), and a value-change callback. Choose glyphs with matching bounds.
The component retains both icon instances, updates the active glyph, text,
accessible label, tooltip and `aria-pressed` together, and reapplies Vaadin's
icon theme after text/icon changes to preserve centering. It uses ordinary
tertiary toolbar styling; do not add selected-state background CSS.

Construction does not notify the owner. A changed value notifies once, while an
unchanged value and a disabled click do not notify. The owner uses that callback
to refresh dirty state, reads `getValue()` when saving, and applies the existing
correction-mode lock. Add the button through the ordinary `ActionToolbar` path
so adaptive labels and spacing remain shared. Functional requirements live in
[Application Actions](../ops/ApplicationActions.md#icon-state-toggles).
