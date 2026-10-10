# Markdown alert icons

GitHub Octicons, copied from the five rendered alert examples in
https://github.com/jewesta/top-teacher/issues/35 on 2026-10-11:

- `note.svg`: `info` (16px)
- `tip.svg`: `light-bulb` (16px)
- `important.svg`: `report` (16px)
- `warning.svg`: `alert` (16px)
- `caution.svg`: `stop` (16px)

The path geometry is unchanged. GitHub-specific DOM attributes were removed;
the SVG namespace and explicit alert colors were added for standalone/PDF use.
Upstream: https://github.com/primer/octicons
Licence: MIT, included in `LICENSE` from
https://raw.githubusercontent.com/primer/octicons/main/LICENSE.

This is the single source for both preview and PDF assets. Maven copies the
icons and licence into the reusable Markdown frontend and the backend's export
resources. Do not maintain separate path copies in Java or TypeScript.
