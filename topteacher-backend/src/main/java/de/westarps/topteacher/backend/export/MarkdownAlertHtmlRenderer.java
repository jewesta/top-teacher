package de.westarps.topteacher.backend.export;

import java.util.Map;
import java.util.Set;

import org.commonmark.node.BlockQuote;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

final class MarkdownAlertHtmlRenderer implements NodeRenderer {

	private final HtmlNodeRendererContext context;
	private final HtmlWriter html;

	MarkdownAlertHtmlRenderer(final HtmlNodeRendererContext context) {
		this.context = context;
		this.html = context.getWriter();
	}

	@Override
	public Set<Class<? extends Node>> getNodeTypes() {
		return Set.of(BlockQuote.class);
	}

	@Override
	public void render(final Node node) {
		final Alert alert = alert(node);
		if (alert == null) {
			html.tag("blockquote");
			renderChildren(node.getFirstChild());
			html.tag("/blockquote");
			return;
		}
		html.tag("div", Map.of("class", "ws-markdown-alert ws-markdown-alert-" + alert.type()));
		html.tag("div", Map.of("class", "ws-markdown-alert-title"));
		html.tag("span", Map.of("class", "ws-markdown-alert-icon"));
		html.tag("/span");
		html.text(" " + alert.label());
		html.tag("/div");
		final Node paragraph = node.getFirstChild();
		Node content = paragraph.getFirstChild().getNext();
		if (content instanceof SoftLineBreak || content instanceof HardLineBreak) {
			content = content.getNext();
		}
		if (content != null) {
			html.tag("p");
			renderChildren(content);
			html.tag("/p");
		}
		renderChildren(paragraph.getNext());
		html.tag("/div");
	}

	private void renderChildren(final Node first) {
		for (Node child = first; child != null; child = child.getNext()) {
			context.render(child);
		}
	}

	private static Alert alert(final Node quote) {
		if (!(quote.getFirstChild() instanceof Paragraph paragraph)
				|| !(paragraph.getFirstChild() instanceof Text text)) {
			return null;
		}
		if (text.getNext() != null && !(text.getNext() instanceof SoftLineBreak)
				&& !(text.getNext() instanceof HardLineBreak)) {
			return null;
		}
		return switch (text.getLiteral().strip()) {
			case "[!NOTE]" -> new Alert("note", "Hinweis");
			case "[!TIP]" -> new Alert("tip", "Tipp");
			case "[!IMPORTANT]" -> new Alert("important", "Wichtig");
			case "[!WARNING]" -> new Alert("warning", "Warnung");
			case "[!CAUTION]" -> new Alert("caution", "Vorsicht");
			default -> null;
		};
	}

	private record Alert(String type, String label) {
	}
}
