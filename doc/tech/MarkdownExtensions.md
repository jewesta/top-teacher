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
Standard footnotes, GitHub-style alerts, and literal numbered blocks belong to
the reusable Markdown module rather than to either application extension.
Optional toolbar commands can be enabled independently. TopTeacher owns their
dialogs and decides which editors offer each command.

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

The backend's CommonMark renderer has a matching alert node renderer and
export CSS. The canonical GitHub Octicons and MIT licence live in
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

This does not implement
the deferred pupil exam-sheet layout or its numbered-text print rendering.

## MCP faithful rendering

The optional PNG output of focused MCP reads renders the same Vaadin
`MarkdownViewer` components used by TopTeacher. Material uses
`MaterialMarkdownViewer` and the existing `tt-material-image` extension;
requirements use `MarkdownViewer` with the shared `tt-criterion` extension in
`DEFAULT` mode, without result-entry controls. Common Markdown,
alerts, footnotes and literal numbered lines continue through the reusable
viewer. Do not add a separate server-side Markdown parser or reproduce the
viewer CSS for this path. The exam editors and their established layout remain
unchanged.

The MCP boundary captures the requested text, metadata and referenced images
under the existing exam-design read guard, then releases it before browser
work. An internal ephemeral-ticket view assembles the selected viewer with
that immutable snapshot. The view is separate from the user's live exam UI;
rendering neither navigates that UI nor changes its editor state. The ticket
and snapshot are removed when the render finishes, including failure paths.

Java Playwright controls one lazy, reusable browser on a single worker, with a
fresh browser context for each request. The browser visits the ticket view, waits
for viewer content and images, and captures its content as PNG bytes. The
fresh context prevents session state from carrying into later renders. The
application retains PNG bytes in memory only; browser profile scratch files
may use the operating system's temporary directory. No exam content is stored
as a durable render artifact.

The browser must be installed separately. Use installed Chrome or an explicitly
configured supported executable/channel; automatic browser downloads are
disabled. The browser initializes only for `includeFaithfulRender=true`, so
ordinary focused reads do not require it. A missing browser or failed
initialization produces unavailable render metadata alongside the original
Markdown. Keep browser failures and installation paths out of user-visible
messages.

| Property | Default | Meaning |
| --- | --- | --- |
| `tt.mcp.render.browser-executable` | Unset | Absolute path to an existing installed browser executable. |
| `tt.mcp.render.browser-channel` | `chrome` | Installed browser channel used for launch. |
| `tt.mcp.render.timeout-seconds` | `30` | Request deadline in seconds, bounded from 5 to 60. |

The render worker has a bounded queue and a request deadline. Capture is
limited to 960 pixels in width, 12000 pixels in height and 8 MiB of PNG output;
an over-limit render must fail cleanly instead of clipping or downscaling the
content silently. Close each request's context and release its ticket on
success, error or timeout; shut down the browser and worker with the
application. The functional response contract belongs in
[Mcp.md](../ops/Mcp.md#optional-faithful-png-output).

## Local frontend builds

Vaadin may copy a reusable module's frontend files from the version installed
in the local Maven repository, even during a reactor build. After changing
`westarps-vaadin-markdown`, install that module locally before preparing or
building the app frontend. Otherwise a successful app bundle can silently use
an older editor implementation. The TypeScript check should run only after
Vaadin has refreshed `generated/jar-resources`.
