package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SanitizerTests {

	private final Sanitizer sanitizer = new Sanitizer();

	@Test
	void removesCriterionLinksForPupilFacingHtml() {
		final SafeHtml html = sanitizer.markdownToHtml(
				"Die Schülerin nutzt die **[korrekte Zeitform](eh:1)** und `präzise Begriffe`.");

		assertThat(html.value()).contains("<strong>korrekte Zeitform</strong>");
		assertThat(html.value()).contains("<code>präzise Begriffe</code>");
		assertThat(html.value()).doesNotContain("eh:1", "tt-criterion", "mark");
	}

	@Test
	void rendersCriterionLinksForTeacherFacingHtml() {
		final SafeHtml html = sanitizer.markdownToHtml("[korrekte Zeitform](eh:1)",
				Sanitizer.MarkdownView.TEACHER);

		assertThat(html.value()).contains("class=\"tt-criterion\"");
		assertThat(html.value()).contains("class=\"tt-criterion-highlight\"");
		assertThat(html.value()).contains("class=\"tt-criterion-badge\">1</span>");
		assertThat(html.value()).doesNotContain("eh:1");
	}

	@Test
	void sanitizesUnsafeHtml() {
		final SafeHtml html = sanitizer.markdownToHtml("Guter Text<script>alert('x')</script>");

		assertThat(html.value()).contains("Guter Text");
		assertThat(html.value()).doesNotContain("<script", "alert");
	}
}
