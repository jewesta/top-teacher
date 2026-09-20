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
- `westarps-vaadin-animate` packages Animate.css 4.1.1 and reusable Java helpers
  for effects, speeds, repetitions, reliable replay, and automatic one-shot
  cleanup. The tray uses its `HEAD_SHAKE` effect when an empty header is clicked.
- `westarps-vaadin-badge` packages PEPPER's badge, positioned wrapper,
  `Badgeable` handshake, and controller as a reusable Vaadin module without
  PEPPER-specific translation or styling dependencies.
- `TrayController<I>` provides list-oriented control. Individual and batched
  additions are placed at the top, full replacements retain their supplied
  order, and each concrete tray decides how one item is rendered.
- `StatusTray` specializes the tray for validation summaries. It renders each
  test result as a compact, non-collapsible severity-colored entry without a
  redundant severity icon. Validation changes do not open or close the tray.
  A top-right badge shows the message count, using grey, warning, or error color
  according to the most severe message; no badge is shown for an empty result.
- Targeted validation results can opt into an in-page link for selected
  targets. The tray retains ordinary test-result list behavior; target URLs,
  interpretation, and navigation remain the responsibility of the consuming
  designer.
- `AbstractDesigner` hosts one initially empty `StatusTray` outside its
  rerendered toolbar and content. The tray is fully hidden by default; designers
  that use it explicitly enable its PEPPER-style peeking state. EH and Results
  enable it, while other designer-based tabs remain unaffected.

## Remaining design questions

- Choose the exact `eh:` URL syntax for full- and half-point criteria.

## EH validation implementation

- `LoeValidator` compares regular requirement maxima with the grading-scale
  maximum and each requirement's tagged criteria with its own declared maximum.
  Bonus requirements are excluded from the grading-scale total but still
  validate their own criteria.
- Missing allocations are warnings and remain saveable. Excess allocations are
  errors: they may exist in the pending editor state but disable only Save until
  corrected or discarded.
- Validation uses the current unsaved editor values and is rendered in the EH
  status tray. The complete/incomplete state therefore updates while editing.
- Requirement, task, category, part, and overall point badges use the same
  pending values and update immediately when points or bonus status changes.
- Messages are short corrective actions expressed in points so they remain
  valid once half-point criteria are introduced. Requirement messages identify
  their task and requirement. Targeted messages are links, not buttons;
  following one closes the tray, expands and scrolls to the exact requirement,
  and briefly emphasizes it. The global EH allocation message is deliberately
  not clickable because it has no single edit target.
- The tray's peek position exposes only its header, with the centered toggle
  icon above the label. Wrapped status messages keep their natural height inside
  the scrollable list, and the card uses a uniform border on every side. Clicking
  an empty peeking tray keeps it compact and gives it a brief wiggle instead.
- The EH tab shows a pen for an incomplete editable design, a tick for a complete
  editable design, and a lock once results exist. The state remains derived and
  is not persisted.

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

The reusable `westarps-vaadin-animate`, `westarps-vaadin-badge`, and
`westarps-vaadin-tray` modules, the tray's validation-aware `StatusTray`, the
Java-only `westarps-validate` module, common designer tray plumbing, and the
whole-point EH validation refactor are implemented. Half-point domain and UI
behavior are not implemented yet.
