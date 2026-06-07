package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HtmlRendererTests {

	private final HtmlRenderer htmlRenderer = new HtmlRenderer();

	@Test
	void rendersThymeleafHtmlTemplate() {
		final String html = htmlRenderer.render("export-test.thymeleaf.html",
				Map.of("title", "Erwartungshorizont", "description", new SafeHtml("<strong>Inhalt</strong>")));

		assertThat(html).contains("<strong>Inhalt</strong>");
		assertThat(html).contains("<p>Erwartungshorizont</p>");
	}
}
