package de.westarps.topteacher.backend.render;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Document;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.InlineParser;
import org.commonmark.parser.Parser;
import org.commonmark.parser.SourceLine;
import org.commonmark.parser.SourceLines;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;

/** Converts a selected Markdown snapshot without export or browser dependencies. */
public final class MarkdownFragmentHtmlRenderer {

	private static final List<Extension> EXTENSIONS = List.of(FootnotesExtension.create(), TablesExtension.create(),
			StrikethroughExtension.create());
	private static final Pattern CRITERION = Pattern.compile("^crit:([^\\s)]+)$");
	private static final Pattern POINT_VALUE = Pattern
			.compile("(?:0[.,]5|(?:[1-9]\\d{0,1}|[1-8]\\d{2}|9(?:[0-8]\\d|9[0-8]))(?:[.,][05])?|999(?:[.,]0)?)");
	private static final Pattern QUOTE_PREFIX = Pattern.compile("^ {0,3}>[ \\t]?");
	private static final Pattern NUMBERED_MARKER = Pattern.compile("^ {0,3}>[ \\t]?\\[!NUMBERED\\][ \\t]*$");
	private static final Safelist SAFE_HTML = Safelist.none()
			.addTags("section", "div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "strong", "em",
					"mark", "del", "a", "code", "pre", "ol", "ul", "li", "sup", "br", "hr", "table", "thead", "tbody", "tr",
					"th", "td", "img")
			.addAttributes(":all", "class").addAttributes("a", "href", "title")
			.addProtocols("a", "href", "http", "https", "mailto", "#")
			.addAttributes("ol", "start").addAttributes("th", "align").addAttributes("td", "align", "data-line-number")
			.addAttributes("img", "src", "alt", "title", "width", "height", "style")
			.addProtocols("img", "src", "tt-image");

	private final Parser parser = Parser.builder().extensions(EXTENSIONS)
			.customBlockParserFactory(new NumberedBlockParser.Factory()).build();

	public HtmlFragment render(final RenderRequest request) {
		Objects.requireNonNull(request, "request");
		final Map<String, LoeMaterialImage> availableImages = new LinkedHashMap<>();
		for (LoeMaterialImage image : request.images()) {
			if (availableImages.putIfAbsent(image.id(), image) != null) {
				throw new IllegalArgumentException("Duplicate material image identifier.");
			}
		}
		final Map<String, LoeMaterialImage> referencedImages = new LinkedHashMap<>();
		final StringBuilder html = new StringBuilder();
		final HtmlWriter writer = new HtmlWriter(html);
		for (var section : request.sections()) {
			final Node document = parser.parse(section.markdown());
			transformAlerts(document);
			final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true)
					.sanitizeUrls(true).nodeRendererFactory(context -> new SnapshotNodeRenderer(context, section.variant(),
							availableImages, referencedImages)).build();
			writer.tag("section", Map.of("class", "tt-render-section"));
			if (!section.title().isBlank()) {
				writer.tag("h3", Map.of("class", "tt-render-section-title"));
				writer.text(section.title());
				writer.tag("/h3");
			}
			writer.tag("div", Map.of("class", "wmde-markdown"));
			writer.raw(sanitize(renderer.render(document)));
			writer.tag("/div");
			writer.tag("/section");
		}
		return new HtmlFragment(html.toString(), referencedImages);
	}

	private static String sanitize(final String html) {
		final org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(html);
		document.select("a[data-footnote-backref]").remove();
		document.select("a[data-footnote-ref]").forEach(reference -> reference.tagName("span").clearAttributes());
		document.select("section.footnotes").forEach(footnotes -> footnotes.prependElement("h4").text("Fußnoten"));
		final OutputSettings output = new OutputSettings().prettyPrint(false).syntax(OutputSettings.Syntax.xml)
				.escapeMode(Entities.EscapeMode.xhtml).charset(StandardCharsets.UTF_8);
		return Jsoup.clean(document.body().html(), "", SAFE_HTML, output);
	}

	private static void transformAlerts(final Node parent) {
		Node node = parent.getFirstChild();
		while (node != null) {
			final Node next = node.getNext();
			transformAlerts(node);
			if (node instanceof BlockQuote quote && quote.getFirstChild() instanceof Paragraph paragraph
					&& paragraph.getFirstChild() instanceof Text text) {
				final AlertType type = AlertType.fromMarker(text.getLiteral().stripTrailing());
				final Node afterMarker = text.getNext();
				if (type != null && (afterMarker == null || afterMarker instanceof SoftLineBreak
						|| afterMarker instanceof HardLineBreak)) {
					text.unlink();
					if (afterMarker != null) {
						afterMarker.unlink();
					}
					if (paragraph.getFirstChild() == null) {
						paragraph.unlink();
					}
					final AlertBlock alert = new AlertBlock(type);
					quote.insertBefore(alert);
					while (quote.getFirstChild() != null) {
						alert.appendChild(quote.getFirstChild());
					}
					quote.unlink();
				}
			}
			node = next;
		}
	}

	public record HtmlFragment(String html, Map<String, LoeMaterialImage> imageResources) {
		public HtmlFragment {
			html = Objects.requireNonNull(html, "html");
			imageResources = Map.copyOf(imageResources);
		}
	}

	private enum AlertType {
		NOTE("Hinweis", "i"), TIP("Tipp", "◇"), IMPORTANT("Wichtig", "!"), WARNING("Warnung", "!"), CAUTION("Vorsicht", "!");

		private final String label;
		private final String icon;

		AlertType(final String label, final String icon) {
			this.label = label;
			this.icon = icon;
		}

		private static AlertType fromMarker(final String marker) {
			for (AlertType type : values()) {
				if (marker.equals("[!" + type.name() + "]")) {
					return type;
				}
			}
			return null;
		}
	}

	private static final class AlertBlock extends CustomBlock {
		private final AlertType type;

		private AlertBlock(final AlertType type) {
			this.type = type;
		}
	}

	private static final class NumberedBlock extends CustomBlock {
	}

	private static final class NumberedLine extends CustomNode {
		private final int logicalNumber;

		private NumberedLine(final int logicalNumber) {
			this.logicalNumber = logicalNumber;
		}
	}

	private static final class NumberedBlockParser extends AbstractBlockParser {
		private final NumberedBlock block = new NumberedBlock();
		private final List<SourceLine> lines = new ArrayList<>();
		private boolean markerLine = true;

		@Override
		public Block getBlock() {
			return block;
		}

		@Override
		public BlockContinue tryContinue(final ParserState state) {
			final var prefix = QUOTE_PREFIX.matcher(state.getLine().getContent());
			return state.getIndex() == 0 && prefix.find() ? BlockContinue.atIndex(prefix.end()) : BlockContinue.none();
		}

		@Override
		public void addLine(final SourceLine line) {
			if (markerLine) {
				markerLine = false;
			} else {
				lines.add(line);
			}
		}

		@Override
		public void parseInlines(final InlineParser inlineParser) {
			int number = 0;
			for (SourceLine source : lines) {
				final NumberedLine line = new NumberedLine(source.getContent().toString().isBlank() ? 0 : ++number);
				inlineParser.parse(SourceLines.of(source), line);
				block.appendChild(line);
			}
		}

		private static final class Factory extends AbstractBlockParserFactory {
			@Override
			public BlockStart tryStart(final ParserState state, final MatchedBlockParser matchedBlockParser) {
				final Block matched = matchedBlockParser.getMatchedBlockParser().getBlock();
				final boolean topLevel = matched instanceof Document
						|| matched instanceof Paragraph && matched.getParent() instanceof Document;
				final CharSequence content = state.getLine().getContent();
				return topLevel && state.getIndex() == 0 && NUMBERED_MARKER.matcher(content).matches()
						? BlockStart.of(new NumberedBlockParser()).atIndex(content.length())
						: BlockStart.none();
			}
		}
	}

	private static final class SnapshotNodeRenderer implements NodeRenderer {
		private final HtmlNodeRendererContext context;
		private final HtmlWriter html;
		private final Variant variant;
		private final Map<String, LoeMaterialImage> availableImages;
		private final Map<String, LoeMaterialImage> referencedImages;

		private SnapshotNodeRenderer(final HtmlNodeRendererContext context, final Variant variant,
				final Map<String, LoeMaterialImage> availableImages, final Map<String, LoeMaterialImage> referencedImages) {
			this.context = context;
			this.html = context.getWriter();
			this.variant = variant;
			this.availableImages = availableImages;
			this.referencedImages = referencedImages;
		}

		@Override
		public Set<Class<? extends Node>> getNodeTypes() {
			return Set.of(AlertBlock.class, NumberedBlock.class, NumberedLine.class, Image.class, Link.class);
		}

		@Override
		public void render(final Node node) {
			if (node instanceof AlertBlock alert) {
				html.tag("div", Map.of("class", "ws-markdown-alert ws-markdown-alert-"
						+ alert.type.name().toLowerCase(Locale.ROOT)));
				html.tag("div", Map.of("class", "ws-markdown-alert-title"));
				html.tag("span", Map.of("class", "ws-markdown-alert-icon"));
				html.text(alert.type.icon);
				html.tag("/span");
				html.text(" " + alert.type.label);
				html.tag("/div");
				renderChildren(alert);
				html.tag("/div");
			} else if (node instanceof NumberedBlock) {
				html.tag("table", Map.of("class", "ws-markdown-numbered"));
				html.tag("tbody");
				renderChildren(node);
				html.tag("/tbody");
				html.tag("/table");
			} else if (node instanceof NumberedLine line) {
				renderNumberedLine(line);
			} else if (node instanceof Image image) {
				renderImage(image);
			} else if (node instanceof Link link) {
				renderLink(link);
			}
		}

		private void renderNumberedLine(final NumberedLine line) {
			html.tag("tr", Map.of("class", "ws-markdown-numbered-line"));
			html.tag("td", Map.of("class", "ws-markdown-line-number"));
			if (line.logicalNumber == 1 || line.logicalNumber > 0 && line.logicalNumber % 5 == 0) {
				html.text(Integer.toString(line.logicalNumber));
			}
			html.tag("/td");
			final Map<String, String> attributes = new LinkedHashMap<>();
			attributes.put("class", "ws-markdown-line-text");
			if (line.logicalNumber > 0) {
				attributes.put("data-line-number", Integer.toString(line.logicalNumber));
			}
			html.tag("td", attributes);
			if (line.logicalNumber == 0) {
				html.text("\u00a0");
			} else {
				renderChildren(line);
			}
			html.tag("/td");
			html.tag("/tr");
		}

		private void renderImage(final Image image) {
			final String alt = imageText(image);
			final var target = LoeMaterialImageReferences.target(image.getDestination());
			final LoeMaterialImage managed = variant == Variant.MATERIAL && target.isPresent()
					? availableImages.get(target.orElseThrow().id()) : null;
			if (managed == null) {
				html.tag("span", Map.of("class", "tt-material-image-missing"));
				html.text("Bild nicht verfügbar: " + (alt.isBlank() ? "ohne Beschreibung" : alt));
				html.tag("/span");
				return;
			}
			final String uri = "tt-image:" + managed.id();
			referencedImages.putIfAbsent(uri, managed);
			final Map<String, String> attributes = new LinkedHashMap<>();
			final LoeMaterialImageReferences.Target parsed = target.orElseThrow();
			attributes.put("class", "tt-material-image tt-material-image-align-" + parsed.alignment().queryValue());
			attributes.put("src", uri);
			attributes.put("alt", alt);
			attributes.put("width", Integer.toString(managed.pixelWidth()));
			attributes.put("height", Integer.toString(managed.pixelHeight()));
			if (parsed.width() != null) {
				attributes.put("style", "width:" + parsed.width() + "%;height:auto");
			}
			if (image.getTitle() != null) {
				attributes.put("title", image.getTitle());
			}
			html.tag("img", attributes, true);
		}

		private void renderLink(final Link link) {
			final var criterion = CRITERION.matcher(link.getDestination().trim());
			if (criterion.matches()) {
				if (variant == Variant.REQUIREMENT) {
					html.tag("span", Map.of("class", "tt-criterion"));
					html.tag("mark", Map.of("class", "tt-criterion-highlight"));
					renderChildren(link);
					html.tag("span", Map.of("class", "tt-criterion-badge"));
					html.text(declaredPoints(criterion.group(1)));
					html.tag("/span");
					html.tag("/mark");
					html.tag("/span");
				} else {
					renderChildren(link);
				}
				return;
			}
			final Map<String, String> attributes = new LinkedHashMap<>();
			attributes.put("href", context.encodeUrl(context.urlSanitizer().sanitizeLinkUrl(link.getDestination())));
			if (link.getTitle() != null) {
				attributes.put("title", link.getTitle());
			}
			html.tag("a", attributes);
			renderChildren(link);
			html.tag("/a");
		}

		private void renderChildren(final Node node) {
			Node child = node.getFirstChild();
			while (child != null) {
				final Node next = child.getNext();
				context.render(child);
				child = next;
			}
		}
	}

	private static String declaredPoints(final String target) {
		final int queryStart = target.indexOf('?');
		if (queryStart < 0) {
			return "1";
		}
		final String query = target.substring(queryStart + 1);
		final int separator = query.indexOf('=');
		if (separator < 0 || query.contains("&") || query.contains("#")) {
			return "?";
		}
		try {
			final String name = URLDecoder.decode(query.substring(0, separator), StandardCharsets.UTF_8);
			final String value = URLDecoder.decode(query.substring(separator + 1), StandardCharsets.UTF_8).trim();
			return name.equals("points") && POINT_VALUE.matcher(value).matches() ? value.replace('.', ',') : "?";
		} catch (IllegalArgumentException exception) {
			return "?";
		}
	}

	private static String imageText(final Image image) {
		final StringBuilder text = new StringBuilder();
		image.accept(new AbstractVisitor() {
			@Override
			public void visit(final Text node) {
				text.append(node.getLiteral());
			}

			@Override
			public void visit(final Code node) {
				text.append(node.getLiteral());
			}

			@Override
			public void visit(final SoftLineBreak node) {
				text.append(' ');
			}
		});
		return text.toString();
	}
}
