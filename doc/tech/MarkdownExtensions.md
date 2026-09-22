# Markdown Extensions

`westarps-vaadin-markdown` owns the reusable editor and viewer. TopTeacher owns
the criterion feature, including the `P` toolbar command, point selector,
`eh:` tag parsing and preview, pills, and Results checkboxes. The reusable
module does not import or encode any of those concepts.

An extension is a TypeScript module registered by ID. It may contribute toolbar
commands and preview processing (remark/rehype plugins, sanitization additions,
and rendered components). The editor and viewer apply an ordered list of
extension IDs; the order matters when multiple extensions contribute commands
or preview transforms. Their Java components carry those IDs and an optional
viewer state map. Extension events travel through the generic
`markdown-extension-event` bridge.

TopTeacher activates `tt-criterion` through `CriterionMarkdownEditor` and
`CriterionMarkdownViewer`; their `@JsModule` imports register the TypeScript
module. The criterion extension and its CSS live under
`topteacher-app/src/main/frontend/`. No Java configuration assembles the
criterion popup or supplies its icon.

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
