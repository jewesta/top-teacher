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
`img:<id>[?width=<percentage>&align=<left|center|right>]`; either optional
parameter may also appear alone. They are consumed by the
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

When an extension adds allowed CSS classes for an element, it merges them into
the element's existing `className` sanitizer definition. Adding a second
definition is ineffective because `hast-util-sanitize` uses the first matching
property definition; this would retain the element while silently stripping
the later extension's classes.

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
Standard footnotes, GitHub-style alerts, and literal numbered blocks belong to
the reusable Markdown module rather than to either application extension.
Optional toolbar commands can be enabled independently. TopTeacher owns their
dialogs and decides which editors offer each command.

Existing alerts are located in the shared Java editor using CommonMark block
source spans. This gives cursor/selection containment without matching fenced
code examples, handles lazy quote continuation, and retains outer quote/list
prefixes. The application alert dialog uses that context for its initial type
and body; confirming calls the same insertion API, which replaces a containing
alert instead of inserting another. Keep the full block range and preserve the
surrounding source, including CRLF line endings.

`ws-markdown-blocks.ts` transforms recognized blockquote markers before HTML
sanitization. The preview's `pluginsFilter` moves this transform before the
library's built-in alert parser so it cannot consume the markers first.
Literal numbered rows are parsed individually with a neutral
text prefix so block syntax cannot introduce extra rows; document-level link
and footnote definitions are included for inline reference resolution. Source
positions preserve explicitly empty quoted lines as spacing, but only
non-whitespace source lines advance the counter. Each counted text span carries
its logical number in `data-line-number`, including lines without a visible
gutter label; clipping diagnostics use this value rather than the physical row
index. The shared schema permits the required presentation classes and numbering
metadata. Numbered rows clip excess text and observe their available width to
report clipping in the preview.

The backend's CommonMark renderer has matching managed-image sizing and
alignment plus an alert node renderer and export CSS. The canonical GitHub Octicons and MIT licence live in
`assets/markdown-alerts/`. Both modules copy these resources during Maven's
resource phase, avoiding separate SVG path copies or a backend dependency on
Vaadin. The shared preview uses the SVGs as CSS masks, inheriting the title
color and hiding decorative icon spans from assistive technology.

The backend renderer emits empty icon placeholders. `MarkdownAlertIcons`
clones only the five known bundled SVGs into these placeholders after Markdown
sanitization and after parsing the final export HTML. XML parsing preserves
the SVG namespace and case-sensitive `viewBox` attribute. User-provided SVGs
remain disallowed by the Markdown sanitizer. `OpenHtmlPdfRenderer` registers
OpenHTMLToPDF's SVG support with the default `BatikSVGDrawer`, whose scripts
and external-resource requests are disabled. SVGs become vector form objects
in the PDF, not raster images or font-dependent symbols. Tests cover bundled
resources, sanitization, and vector objects in actual PDF output.

Pupil task-sheet composition is implemented separately; see
[Pupil task-sheet PDF export](../ops/ExamTaskSheetPdfExport.md). Its alert icon
placeholders use this same trusted SVG embedding pipeline and must remain
visible in the task-sheet stylesheet.

## MCP faithful rendering

The optional PNG output of focused MCP reads is a confined fragment renderer
for one task, material, requirement set or exam closing text. It uses
OpenHTMLToPDF's `openhtmltopdf-java2d` module, version `1.0.10`, to paint HTML
directly to an in-memory Java2D image and encode PNG bytes. There is no
intermediate PDF, browser, JavaScript runtime or browser installation step.
The existing EH PDF export pipeline and the exam editors' layout remain
unchanged.

The isolated CommonMark fragment processor supports TopTeacher's Markdown
extensions, including managed material images, requirement criteria, alerts,
footnotes and literal numbered source lines. It reuses bundled print fonts and
related assets without modifying the existing EH Markdown/export classes.
The fragment's print-like typography is
independent of the Vaadin viewer's layout. Line wrapping at its fixed image
width need not match an existing EH PDF or pupil exam sheet; do not describe
this preview as a pixel-identical export.

Keep literal `[!NUMBERED]` rows non-wrapping, including their original source
whitespace. Check the laid-out numbered table's width before allocating the
PNG canvas; an over-wide row must produce the ordinary text fallback.
Paragraphs and code may wrap long tokens within the fragment width.

The MCP boundary captures the requested text, metadata and referenced images
under the existing exam-design read guard, then releases it before render
work. The immutable snapshot supplies all requested content; rendering does
not read further exam state or alter the user's live UI. Source images and PNG
bytes stay in memory, with no image or PNG scratch files or durable exports.

Keep one active render with no waiting queue; reject additional requests while
the worker is occupied. Check input size and the
aggregate decoded-image pixel budget before loading images, apply a separate
12000000-pixel budget to scaled images before layout, counting every image
occurrence conservatively, and constrain the
Java2D canvas before allocating it. Continuous Java2D output derives its
height from the laid-out content, so a page-size declaration alone is not a
height bound. The image is limited to 960 pixels in width, 12000 pixels in
height and 8 MiB of PNG output. Reject over-limit content cleanly instead of
clipping or downscaling it silently. Only an explicit
`includeFaithfulRender=true` invokes the renderer; ordinary focused reads
retain their structured response with no render metadata or image bytes.

The request deadline is configured by `tt.mcp.render.timeout-seconds`,
defaulting to `30` and bounded from `5` to `60`. A timeout returns the ordinary
text fallback, but the worker remains occupied until that job exits. This
prevents two renders from allocating bitmaps at the same time even if a
library operation takes time to respond to interruption. Use the existing
bundled Frugal Sans fonts under
`templates/frugal-sans` rather than relying on machine-specific system fonts.

The fragment installs a snapshot-only image decoder on its own renderer
context. It reads approved managed-image bytes through
`MemoryCacheImageInputStream`; PNG encoding uses a bounded memory output
stream. Do not change ImageIO's global cache setting, which could affect the
existing EH export or other image consumers. URI resolution rejects resources
outside the selected snapshot; bundled alert SVGs are inserted directly.
Keep intrinsic image dimensions and relative width rules for layout rather
than fixing width and height against the document's outer width. Narrower
containers must retain the image's aspect ratio; count their possible scaled
rasters before rendering as well.

Keep internal render errors out of user-visible messages and preserve the
original Markdown when a preview cannot be produced. The functional response
contract belongs in
[Mcp.md](../ops/Mcp.md#optional-faithful-png-output).

## Local frontend builds

Vaadin may copy a reusable module's frontend files from the version installed
in the local Maven repository, even during a reactor build. After changing
`westarps-vaadin-markdown`, install that module locally before preparing or
building the app frontend. Otherwise a successful app bundle can silently use
an older editor implementation. The TypeScript check should run only after
Vaadin has refreshed `generated/jar-resources`.
