package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class MarkdownAlertIconsTests {

	@Test
	void embedsFiveDistinctBundledSvgsWithoutExternalResources() {
		final var document = Jsoup.parse(List.of("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION").stream()
				.map(type -> new Sanitizer().markdownToHtml(
						"> [!" + type + "]\n> Text", Sanitizer.MarkdownView.PUPIL).value())
				.reduce("", String::concat));

		MarkdownAlertIcons.embedIn(document);

		final var icons = document.select(".ws-markdown-alert-icon svg");
		assertThat(icons).hasSize(5);
		assertThat(icons.eachAttr("fill")).containsExactly("#0969da", "#1a7f37", "#8250df", "#9a6700", "#cf222e");
		assertThat(icons.select("path").eachAttr("d")).doesNotHaveDuplicates();
		assertThat(document.select(".ws-markdown-alert-icon").eachAttr("aria-hidden")).containsOnly("true");
		for (final Element icon : icons) {
			assertThat(icon.attr("xmlns")).isEqualTo("http://www.w3.org/2000/svg");
			assertThat(icon.attributes().hasKey("viewBox")).isTrue();
			assertThat(icon.attr("viewBox")).isEqualTo("0 0 16 16");
			assertThat(icon.select("script, image, foreignObject, [href], [xlink\\:href]")).isEmpty();
		}
	}

	@Test
	void clonesResourcesAndIgnoresUnknownTypesOrUnrelatedPlaceholders() {
		final String html = "<div class='ws-markdown-alert ws-markdown-alert-tip'>"
				+ "<div class='ws-markdown-alert-title'><span class='ws-markdown-alert-icon'></span></div></div>";
		final var document = Jsoup.parse(html + html
				+ html.replace("ws-markdown-alert-tip", "ws-markdown-alert-unknown")
				+ "<span class='ws-markdown-alert-icon'>Unrelated</span>");

		MarkdownAlertIcons.embedIn(document);

		final var icons = document.select("svg");
		assertThat(icons).hasSize(2);
		icons.getFirst().selectFirst("path").attr("d", "changed");
		assertThat(icons.get(1).selectFirst("path").attr("d")).isNotEqualTo("changed");
		assertThat(document.select(".ws-markdown-alert-unknown svg")).isEmpty();
		assertThat(document.selectFirst("body > span").text()).isEqualTo("Unrelated");

		final var next = Jsoup.parse(html);
		MarkdownAlertIcons.embedIn(next);
		assertThat(next.selectFirst("path").attr("d")).isEqualTo(icons.get(1).selectFirst("path").attr("d"));
	}
}
