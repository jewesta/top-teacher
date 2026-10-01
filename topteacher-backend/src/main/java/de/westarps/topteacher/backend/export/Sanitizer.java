package de.westarps.topteacher.backend.export;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.model.loe.LoeCriterionParser;
import de.westarps.topteacher.model.loe.LoePointUnits;

@Component
public class Sanitizer {

	private static final String CRITERION_DESTINATION_PREFIX = LoeCriterionParser.TAG_NAMESPACE + ":";
	private static final Safelist SAFE_HTML = Safelist.basic()
			.addTags("h1", "h2", "h3", "h4", "h5", "h6", "mark", "span").addAttributes("mark", "class")
			.addAttributes("span", "class");
	private static final OutputSettings OUTPUT_SETTINGS = new OutputSettings().prettyPrint(false);

	private final Parser parser = Parser.builder().build();
	private final org.commonmark.renderer.html.HtmlRenderer markdownRenderer = org.commonmark.renderer.html.HtmlRenderer
			.builder().build();

	public SafeHtml markdownToHtml(final String markdown, final MarkdownView view) {
		return markdownToHtml(markdown, view, ignored -> CriterionMark.NONE);
	}

	public SafeHtml markdownToHtml(final String markdown, final MarkdownView view,
			final Function<String, CriterionMark> criterionMarkByKey) {
		final Node document = parser.parse(markdown == null ? "" : markdown);
		transformCriterionLinks(document, view == null ? MarkdownView.PUPIL : view,
				criterionMarkByKey == null ? ignored -> CriterionMark.NONE : criterionMarkByKey);
		return new SafeHtml(Jsoup.clean(markdownRenderer.render(document), "", SAFE_HTML, OUTPUT_SETTINGS));
	}

	private void transformCriterionLinks(final Node document, final MarkdownView view,
			final Function<String, CriterionMark> criterionMarkByKey) {
		final List<Link> criterionLinks = new ArrayList<>();
		document.accept(new AbstractVisitor() {

			@Override
			public void visit(final Link link) {
				if (isCriterionLink(link)) {
					criterionLinks.add(link);
				}
				visitChildren(link);
			}
		});
		criterionLinks.forEach(link -> transformCriterionLink(link, view, criterionMarkByKey));
	}

	private void transformCriterionLink(final Link link, final MarkdownView view,
			final Function<String, CriterionMark> criterionMarkByKey) {
		if (view == MarkdownView.TEACHER) {
			wrapCriterionLink(link, criterionMarkByKey);
		} else {
			unwrapLink(link);
		}
	}

	private static boolean isCriterionLink(final Link link) {
		return link.getDestination() != null && link.getDestination().startsWith(CRITERION_DESTINATION_PREFIX);
	}

	private static void unwrapLink(final Link link) {
		moveChildrenBefore(link);
		link.unlink();
	}

	private static void wrapCriterionLink(final Link link, final Function<String, CriterionMark> criterionMarkByKey) {
		final String criterionTarget = link.getDestination().substring(CRITERION_DESTINATION_PREFIX.length()).trim();
		final String criterionKey = criterionKey(criterionTarget);
		final CriterionMark providedMark = criterionMarkByKey.apply(criterionKey);
		final CriterionMark mark = providedMark == null ? CriterionMark.NONE : providedMark;
		link.insertBefore(html("<span class=\"tt-criterion\"><mark class=\"tt-criterion-highlight\">"));
		moveChildrenBefore(link);
		link.insertBefore(html("</mark><span class=\"tt-criterion-badge\">"
				+ escapeHtml(LoePointUnits.formatGerman(mark.awardedPointUnits())) + "</span>"
				+ criterionMarker(mark.state()) + "</span>"));
		link.unlink();
	}

	private static String criterionKey(final String criterionTarget) {
		final int valueSeparator = criterionTarget.indexOf('/');
		return valueSeparator < 0 ? criterionTarget : criterionTarget.substring(0, valueSeparator);
	}

	private static String criterionMarker(final CriterionState state) {
		return switch (state) {
			case FULL -> "<span class=\"tt-criterion-marker tt-criterion-marker-full\">&nbsp;</span>";
			case PARTIAL -> "<span class=\"tt-criterion-marker tt-criterion-marker-partial\">&nbsp;</span>";
			case NONE -> """
				<span class="tt-criterion-marker tt-criterion-marker-none">\
				<span class="tt-criterion-marker-line tt-criterion-marker-line-a">&nbsp;</span>\
				<span class="tt-criterion-marker-line tt-criterion-marker-line-b">&nbsp;</span>\
				</span>\
				""";
		};
	}

	private static HtmlInline html(final String literal) {
		final HtmlInline html = new HtmlInline();
		html.setLiteral(literal);
		return html;
	}

	private static void moveChildrenBefore(final Node parent) {
		Node child = parent.getFirstChild();
		while (child != null) {
			final Node next = child.getNext();
			child.unlink();
			parent.insertBefore(child);
			child = next;
		}
	}

	private static String escapeHtml(final String value) {
		return value == null ? ""
				: value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
						.replace("'", "&#39;");
	}

	public record CriterionMark(int awardedPointUnits, int availablePointUnits) {

		private static final CriterionMark NONE = new CriterionMark(0, 0);

		public CriterionMark {
			if (awardedPointUnits < 0) {
				throw new IllegalArgumentException("awardedPointUnits must not be negative");
			}
			if (availablePointUnits < 0) {
				throw new IllegalArgumentException("availablePointUnits must not be negative");
			}
			if (awardedPointUnits > availablePointUnits) {
				throw new IllegalArgumentException("awardedPointUnits must not exceed availablePointUnits");
			}
		}

		private CriterionState state() {
			if (awardedPointUnits == 0) {
				return CriterionState.NONE;
			}
			return awardedPointUnits == availablePointUnits ? CriterionState.FULL : CriterionState.PARTIAL;
		}
	}

	private enum CriterionState {
		NONE,
		PARTIAL,
		FULL
	}

	public enum MarkdownView {
		PUPIL,
		TEACHER
	}
}
