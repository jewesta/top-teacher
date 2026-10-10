package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SanitizerTests {

	private final Sanitizer sanitizer = new Sanitizer();

	@Test
	void removesCriterionLinksForPupilFacingHtml() {
		final SafeHtml html = sanitizer.markdownToHtml(
				"Der/die Schüler:in nutzt die **[korrekte Zeitform](crit:1)** und `präzise Begriffe`.",
				Sanitizer.MarkdownView.PUPIL);

		assertThat(html.value()).contains("<strong>korrekte Zeitform</strong>");
		assertThat(html.value()).contains("<code>präzise Begriffe</code>");
		assertThat(html.value()).doesNotContain("crit:1", "tt-criterion", "mark");
	}

	@Test
	void rendersCriterionLinksForTeacherFacingHtml() {
		final SafeHtml html = sanitizer.markdownToHtml(
				"[korrekte Zeitform](crit:stable-a?points=0,5) [Wortwahl](crit:stable-b) [Beleg](crit:stable-c)",
				Sanitizer.MarkdownView.TEACHER, key -> switch (key) {
					case "stable-a" -> new Sanitizer.CriterionMark(1, 1);
					case "stable-b" -> new Sanitizer.CriterionMark(1, 2);
					default -> new Sanitizer.CriterionMark(0, 2);
				});

		assertThat(html.value()).contains("class=\"tt-criterion\"");
		assertThat(html.value()).contains("class=\"tt-criterion-highlight\"");
		assertThat(html.value()).contains("class=\"tt-criterion-badge\">0,5</span>");
		assertThat(html.value()).contains("class=\"tt-criterion-badge\">0</span>");
		assertThat(html.value()).contains("tt-criterion-marker-full");
		assertThat(html.value()).contains("tt-criterion-marker-partial");
		assertThat(html.value()).contains("tt-criterion-marker-none");
		assertThat(html.value()).doesNotContain("✓", "✗");
		assertThat(html.value()).doesNotContain("crit:", "stable-a", "stable-b", "stable-c");
	}

	@Test
	void sanitizesUnsafeHtml() {
		final SafeHtml html = sanitizer.markdownToHtml("Guter Text<script>alert('x')</script>",
				Sanitizer.MarkdownView.PUPIL);

		assertThat(html.value()).contains("Guter Text");
		assertThat(html.value()).doesNotContain("<script", "alert");
	}
}
