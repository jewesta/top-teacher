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
	void rendersAlertsForPdfWithInlineContentAndWithoutTheMarker() {
		final String[] types = { "NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION" };
		final String[] labels = { "Hinweis", "Tipp", "Wichtig", "Warnung", "Vorsicht" };
		for (int index = 0; index < types.length; index++) {
			final SafeHtml html = sanitizer.markdownToHtml(
					"> [!" + types[index] + "]\n> **Text** mit [Link](https://example.com).\n>\n> Weiterer Absatz.",
					Sanitizer.MarkdownView.PUPIL);
			assertThat(html.value()).contains("ws-markdown-alert-" + types[index].toLowerCase(), labels[index],
					"<strong>Text</strong>", "https://example.com", "Weiterer Absatz.")
					.doesNotContain("[!", "<blockquote>");
		}
	}

	@Test
	void leavesOrdinaryQuotesAndUnknownMarkersAlone() {
		final SafeHtml html = sanitizer.markdownToHtml(
				"> Normaler Text\n\n> [!UNKNOWN]\n> Text\n\n```\n> [!TIP]\n```", Sanitizer.MarkdownView.PUPIL);
		assertThat(html.value()).contains("<blockquote>", "[!UNKNOWN]", "[!TIP]")
				.doesNotContain("ws-markdown-alert");
	}

	@Test
	void retainsCriterionMarksAndSanitizesHtmlInsideAlerts() {
		final SafeHtml html = sanitizer.markdownToHtml(
				"> [!IMPORTANT]\n> [Beleg](crit:1) <script>bad()</script>", Sanitizer.MarkdownView.TEACHER);
		assertThat(html.value()).contains("ws-markdown-alert-important", "tt-criterion", "Beleg")
				.doesNotContain("<script", "bad()", "crit:");
	}

	@Test
	void sanitizesUnsafeHtml() {
		final SafeHtml html = sanitizer.markdownToHtml("Guter Text<script>alert('x')</script>",
				Sanitizer.MarkdownView.PUPIL);

		assertThat(html.value()).contains("Guter Text");
		assertThat(html.value()).doesNotContain("<script", "alert");
	}

	@Test
	void doesNotAllowUserProvidedSvgOrImageResources() {
		final SafeHtml html = sanitizer.markdownToHtml(
				"> [!NOTE]\n> Text <svg onload=\"bad()\"><image href=\"https://example.com/remote.svg\" />"
						+ "<path d=\"M0 0\" /></svg><img src=\"data:image/svg+xml,bad\" onerror=\"bad()\">",
				Sanitizer.MarkdownView.PUPIL);
		assertThat(html.value()).contains("ws-markdown-alert-icon", "Hinweis", "Text")
				.doesNotContain("<svg", "<path", "<image", "<img", "onload", "onerror", "bad()", "remote.svg");
	}
}
