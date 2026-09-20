# Issue 32: Support half points

## Intent

Support levels of expectations in which individual scoring units can be worth
half a point. A half-point unit is either achieved or not achieved, just like a
full-point unit. A full-point unit must not become partially achievable merely
because the application supports half points elsewhere.

## Agreed model

- A requirement retains its explicitly declared integer maximum. Its criteria
  are each worth either one point or half a point.
- Criterion results remain boolean. Achieved criterion values are summed in
  integer half-point units and rounded half-up within their requirement. Only
  integer requirement results are aggregated further.
- Criterion allocation is compared with the requirement maximum; regular
  requirement maxima are compared with the authoritative grading-scale total.
- Under-allocation is a saveable intermediate design. Over-allocation may exist
  in the dirty editor state but prevents the complete pending edit from being
  saved.
- EH state is derived rather than persisted: incomplete and editable, complete
  and editable, or complete and locked because results exist.
- Compatibility with existing EH data is not required because no production EH
  has been created yet.

## Validation presentation

- The EH tab uses a pen, tick, or lock icon for the three derived states.
- Allocation issues will be presented in a reusable bottom tray. The same tray
  component is intended for the result view and other projects.
- The generic tray is a Vaadin `Card` with a configurable notch label, a
  scrollable vertical list, and explicit `HIDE`, `PEEK`, and `SHOW` states. Its
  header toggles between `PEEK` and `SHOW`, and an outside click returns a shown
  tray to `PEEK`.
- `TrayController<I>` provides list-oriented control. Individual and batched
  additions are placed at the top, full replacements retain their supplied
  order, and each concrete tray decides how one item is rendered.
- `StatusTray` specializes the tray for validation summaries. It renders each
  test result as a compact, non-collapsible severity-colored entry without a
  redundant severity icon. Changed non-empty results show the tray, while an
  empty result list returns it to its header-only peeking state.
- `AbstractDesigner` hosts one initially empty `StatusTray` outside its
  rerendered toolbar and content. The tray is fully hidden by default; designers
  that use it explicitly enable its PEPPER-style peeking state. EH and Results
  enable it, while other designer-based tabs remain unaffected.

## Remaining design questions

- Choose the exact `eh:` URL syntax for full- and half-point criteria.
- Design the TopTeacher-specific validation issue components placed inside the
  generic tray.

## Expected impact

The change is cross-cutting and is expected to affect:

- the domain types for EH requirements, results, point summaries, and point
  rules;
- the database schema and migration of existing point values;
- EH design and result-entry controls and validation;
- bonus-point capping and all aggregate displays;
- PDF and spreadsheet exports;
- grading and evaluation;
- MCP input and output schemas;
- base data, demo data, and automated tests.

## Status

The reusable `westarps-vaadin-tray` module, its validation-aware `StatusTray`,
the Java-only `westarps-validate` module, and common designer tray plumbing are
contained preparatory changes. Half-point domain and UI behavior are not
implemented yet.
