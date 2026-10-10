# Markdown Extensions

`westarps-vaadin-markdown` owns the reusable editor and viewer. TopTeacher owns
the criterion feature, including the `P` toolbar command, point selector,
`crit:` tag parsing and preview, pills, and Results checkboxes. TopTeacher also
owns Material's `img:` command, upload workflow, managed-image resolution, and
preview rendering. The reusable module does not import or encode any of those
concepts.

Criterion URLs use `crit:<key>[?points=<value>]`. The small
`tt-criterion-reference.ts` helper handles query decoding and source ranges for
the editor, including custom-value selection; the domain parser validates the
same parameter rules. The URL payload is consumed before HTML sanitization,
not enabled as a browser protocol. Material image URLs use
`img:<id>[?width=<percentage>]` and are likewise consumed by the
`tt-material-image` extension before browser rendering. The extension resolves
them to session-scoped resources supplied through extension state; neither
`crit:` nor `img:` is enabled as a browser protocol.

An extension is a TypeScript module registered by ID. It may contribute toolbar
commands and preview processing (remark/rehype plugins, sanitization additions,
and rendered components). The editor and viewer apply an ordered list of
extension IDs; the order matters when multiple extensions contribute commands
or preview transforms. Their Java components carry those IDs and an optional
extension state map. Extension events travel through the generic
`markdown-extension-event` bridge.

TopTeacher activates `tt-criterion` through `CriterionMarkdownEditor` and
`CriterionMarkdownViewer`; their `@JsModule` imports register the TypeScript
module. The criterion extension and its CSS live under
`topteacher-app/src/main/frontend/`. No Java configuration assembles the
criterion popup or supplies its icon.

TopTeacher activates `tt-material-image` only through
`MaterialMarkdownEditor` and `MaterialMarkdownViewer`. Image bytes remain in
the backend; the extension receives only session-resource URLs and pixel
dimensions for referenced images. The reusable editor intercepts file drops
and reports them through the same generic event bridge. TopTeacher decides
whether a drop opens the Material workflow or produces the explanatory
notification used by all other Markdown surfaces.

This is deliberately a small composition seam, not a general plugin framework.
A future footnote extension can register independently and be included beside
`tt-criterion` where needed. It must not be added as a special case inside the
criterion implementation or the westarps editor.

## Local frontend builds

Vaadin may copy a reusable module's frontend files from the version installed
in the local Maven repository, even during a reactor build. After changing
`westarps-vaadin-markdown`, install that module locally before preparing or
building the app frontend. Otherwise a successful app bundle can silently use
an older editor implementation. The TypeScript check should run only after
Vaadin has refreshed `generated/jar-resources`.
