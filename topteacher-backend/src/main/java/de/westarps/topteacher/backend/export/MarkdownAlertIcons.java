package de.westarps.topteacher.backend.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

final class MarkdownAlertIcons {

	private static final Map<String, Element> ICONS = List.of("note", "tip", "important", "warning", "caution")
			.stream().collect(Collectors.toUnmodifiableMap(type -> "ws-markdown-alert-" + type,
					MarkdownAlertIcons::load));

	private MarkdownAlertIcons() {
	}

	/* Only bundled, trusted SVGs are inserted, after sanitizing user Markdown. */
	static void embedIn(final Document document) {
		for (final Element placeholder : document.select(
				".ws-markdown-alert > .ws-markdown-alert-title > span.ws-markdown-alert-icon")) {
			final Element alert = placeholder.parent().parent();
			for (final var entry : ICONS.entrySet()) {
				if (alert.hasClass(entry.getKey())) {
					placeholder.empty().attr("aria-hidden", "true").appendChild(entry.getValue().clone());
					break;
				}
			}
		}
	}

	private static Element load(final String type) {
		final String path = "/templates/export/markdown-alerts/" + type + ".svg";
		try (var stream = Objects.requireNonNull(MarkdownAlertIcons.class.getResourceAsStream(path), path)) {
			final String svg = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			return Objects.requireNonNull(Jsoup.parse(svg, "", Parser.xmlParser()).selectFirst("svg"), path);
		} catch (final IOException exception) {
			throw new IllegalStateException("Cannot load Markdown alert icon " + path, exception);
		}
	}
}
