package de.westarps.vaadin.markdown;

import java.util.Optional;
import java.util.regex.Pattern;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import de.westarps.vaadin.markdown.MarkdownEditor.AlertBlock;
import de.westarps.vaadin.markdown.MarkdownEditor.BlockSelection;

/** Uses Markdown source spans so quoted examples in code are never mistaken for editable alerts. */
final class MarkdownAlertSource {

	private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
	private static final Pattern QUOTE_PREFIX = Pattern.compile("^ {0,3}>[ \t]?");

	private MarkdownAlertSource() {
	}

	static Optional<AlertBlock> find(final String markdown, final BlockSelection selection) {
		final int start = Math.max(0, Math.min(selection.start(), markdown.length()));
		final int end = Math.max(start, Math.min(selection.end(), markdown.length()));
		final AlertBlock[] found = new AlertBlock[1];
		PARSER.parse(markdown).accept(new AbstractVisitor() {
			@Override
			public void visit(final BlockQuote quote) {
				if (quote.getFirstChild() instanceof Paragraph paragraph
						&& paragraph.getFirstChild() instanceof Text marker
						&& (marker.getNext() == null || marker.getNext() instanceof SoftLineBreak
								|| marker.getNext() instanceof HardLineBreak)) {
					/* Numbered source blocks render their rows literally, including nested quote syntax. */
					if (marker.getLiteral().stripTrailing().equals("[!NUMBERED]")) {
						return;
					}
					final MarkdownAlertType type = type(marker.getLiteral().stripTrailing());
					final var spans = quote.getSourceSpans();
					if (type != null && !spans.isEmpty()) {
						final var first = spans.getFirst();
						final var last = spans.getLast();
						final int blockStart = first.getInputIndex();
						final int blockEnd = last.getInputIndex() + last.getLength();
						if (start >= blockStart && end <= blockEnd) {
							final StringBuilder body = new StringBuilder();
							int previousLine = first.getLineIndex() + 1;
							boolean firstBodyLine = true;
							for (final var span : spans.subList(1, spans.size())) {
								if (!firstBodyLine) {
									body.append('\n');
								}
								body.append("\n".repeat(Math.max(0, span.getLineIndex() - previousLine)));
								final String line = markdown.substring(span.getInputIndex(), span.getInputIndex() + span.getLength());
								body.append(QUOTE_PREFIX.matcher(line).replaceFirst(""));
								previousLine = span.getLineIndex() + 1;
								firstBodyLine = false;
							}
							final int lineStart = markdown.lastIndexOf('\n', Math.max(0, blockStart - 1)) + 1;
							/* Retain parent quotes and replace list markers with continuation indentation. */
							final String prefix = markdown.substring(lineStart, blockStart).replaceAll("[^> \t]", " ");
							found[0] = new AlertBlock(new BlockSelection(blockStart, blockEnd), type, body.toString(), prefix);
						}
					}
				}
				visitChildren(quote);
			}
		});
		return Optional.ofNullable(found[0]);
	}

	private static MarkdownAlertType type(final String marker) {
		if (!marker.startsWith("[!") || !marker.endsWith("]")) {
			return null;
		}
		try {
			return MarkdownAlertType.valueOf(marker.substring(2, marker.length() - 1));
		} catch (final IllegalArgumentException exception) {
			return null;
		}
	}
}
