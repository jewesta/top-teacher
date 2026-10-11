package de.westarps.topteacher.backend.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Section;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.topteacher.model.loe.LoeMaterialImage;

class MarkdownFragmentHtmlRendererTests {

	private final MarkdownFragmentHtmlRenderer renderer = new MarkdownFragmentHtmlRenderer();

	@Test
	void preservesStandardMarkdownStructureAndEscapesSectionTitles() {
		final var fragment = renderer.render(new RenderRequest(List.of(new Section("<img src='file:/private'>", """
				## Überschrift

				**Fett**, *kursiv*, ~~entfernt~~ und `Code`.

				1. Eintrag
				2. Zweiter Eintrag

				- Ungeordnet

				| Links | Rechts |
				| :--- | ---: |
				| A | B |

				```java
				<script>kein HTML</script>
				```
				""", Variant.STANDARD)), List.of()));
		final Document html = html(fragment);

		assertThat(html.selectFirst(".tt-render-section-title").text()).isEqualTo("<img src='file:/private'>");
		assertThat(html.selectFirst("h2").text()).isEqualTo("Überschrift");
		assertThat(html.selectFirst("strong").text()).isEqualTo("Fett");
		assertThat(html.selectFirst("em").text()).isEqualTo("kursiv");
		assertThat(html.selectFirst("del").text()).isEqualTo("entfernt");
		assertThat(html.select("ol > li")).hasSize(2);
		assertThat(html.select("ul > li")).hasSize(1);
		assertThat(html.select("table th").eachText()).containsExactly("Links", "Rechts");
		assertThat(html.select("table td").eachText()).containsExactly("A", "B");
		assertThat(html.selectFirst("pre code").text()).contains("<script>kein HTML</script>");
		assertThat(html.select("img,script")).isEmpty();
		assertThat(fragment.imageResources()).isEmpty();
	}

	@Test
	void rendersAllAlertTypesWithGermanTitlesAndInlineMarkdown() {
		final String[] types = { "NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION" };
		final String[] labels = { "Hinweis", "Tipp", "Wichtig", "Warnung", "Vorsicht" };
		for (int index = 0; index < types.length; index++) {
			final Document html = html(render("> [!" + types[index] + "]\n> **Text** mit [Link](https://example.com).\n>\n> Absatz.",
					Variant.STANDARD));
			assertThat(html.selectFirst(".ws-markdown-alert-title").text()).endsWith(labels[index]);
			assertThat(html.select(".ws-markdown-alert-icon")).hasSize(1);
			assertThat(html.selectFirst(".ws-markdown-alert strong").text()).isEqualTo("Text");
			assertThat(html.selectFirst(".ws-markdown-alert a").attr("href")).isEqualTo("https://example.com");
			assertThat(html.select(".ws-markdown-alert p")).hasSize(2);
			assertThat(html.select("blockquote")).isEmpty();
			assertThat(html.text()).doesNotContain("[!");
		}
	}

	@Test
	void doesNotInterpretInlineMarkersUnknownMarkersOrCodeExamplesAsAlerts() {
		final Document html = html(render("""
				> [!NOTE] Text auf derselben Zeile.

				> [!UNKNOWN]
				> Unbekannt.

				```markdown
				> [!TIP]
				> [!NUMBERED]
				```
				""", Variant.STANDARD));

		assertThat(html.select("blockquote")).hasSize(2);
		assertThat(html.select(".ws-markdown-alert,.ws-markdown-numbered")).isEmpty();
		assertThat(html.selectFirst("pre code").text()).contains("[!TIP]", "[!NUMBERED]");
	}

	@Test
	void numbersLiteralPhysicalLinesAndKeepsBlankRowsWithoutAdvancingTheCounter() {
		final Document html = html(render("""
				Text vor dem Block.
				> [!NUMBERED]
				> # Erste **Zeile**
				>
				>    
				> - Zweite Zeile
				> 3. Dritte Zeile
				> Vierte
				> Fünfte
				> Sechste
				> Siebte
				> Achte
				> Neunte
				> Zehnte
				""", Variant.MATERIAL));

		assertThat(html.select(".ws-markdown-numbered-line")).hasSize(12);
		assertThat(html.select(".ws-markdown-line-number").stream().map(element -> element.text()).toList())
				.containsExactly("1", "", "", "", "", "", "5", "", "", "", "", "10");
		assertThat(html.select(".ws-markdown-line-text[data-line-number]").eachAttr("data-line-number"))
				.containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
		assertThat(html.selectFirst(".ws-markdown-line-text").text()).isEqualTo("# Erste Zeile");
		assertThat(html.select(".ws-markdown-line-text").get(3).text()).isEqualTo("- Zweite Zeile");
		assertThat(html.select(".ws-markdown-line-text strong")).hasSize(1);
		assertThat(html.select(".ws-markdown-numbered h1,.ws-markdown-numbered ol,.ws-markdown-numbered ul")).isEmpty();
		assertThat(html.text()).doesNotContain("[!NUMBERED]");
	}

	@Test
	void resolvesNumberedLineLinksAndFootnotesUsingTheWholeSectionDefinitions() {
		final Document html = html(render("""
				Einleitung[^intro].

				> [!NUMBERED]
				> Erste Zeile mit [Verweis][ziel] und Fußnote[^row].
				> Zweite Zeile mit erneutem Hinweis[^intro].

				[ziel]: https://example.com/info
				[^row]: Erläuterung zur Zeile mit **Fettdruck**.
				[^intro]: Erläuterung zur Einleitung.
				[^unused]: Dieser Text wurde nicht referenziert.
				""", Variant.MATERIAL));

		assertThat(html.selectFirst(".ws-markdown-numbered a").attr("href")).isEqualTo("https://example.com/info");
		assertThat(html.select("sup.footnote-ref").eachText()).containsExactly("1", "2", "1");
		assertThat(html.selectFirst("section.footnotes h4").text()).isEqualTo("Fußnoten");
		assertThat(html.select("section.footnotes ol > li").eachText())
				.containsExactly("Erläuterung zur Einleitung.", "Erläuterung zur Zeile mit Fettdruck.");
		assertThat(html.select("section.footnotes strong")).hasSize(1);
		assertThat(html.text()).doesNotContain("Dieser Text wurde nicht referenziert", "↩");
		assertThat(html.select("sup a")).isEmpty();
	}

	@Test
	void keepsNestedNumberedBlocksOrdinaryWhileRenderingNestedAlerts() {
		final Document html = html(render("""
				> [!NOTE]
				> Text.
				>
				> > [!TIP]
				> > Innen.
				>
				> > [!NUMBERED]
				> > Keine Nummerierung.
				""", Variant.MATERIAL));

		assertThat(html.select(".ws-markdown-alert-note .ws-markdown-alert-tip")).hasSize(1);
		assertThat(html.select(".ws-markdown-numbered")).isEmpty();
		assertThat(html.select("blockquote").text()).contains("[!NUMBERED]", "Keine Nummerierung");
	}

	@Test
	void resolvesOnlyReferencedManagedImagesInMaterialSections() {
		final LoeMaterialImage referenced = image("abc123xy");
		final LoeMaterialImage unused = image("unused01");
		final var fragment = renderer.render(new RenderRequest(List.of(new Section("Material", """
				![Kartenausschnitt](img:abc123xy?width=50&align=center "Karte")

				![Rechts](img:abc123xy?align=right)

				![Volle Breite](img:abc123xy)

				![Nicht gefunden](img:missing1)

				![Ungültige Breite](img:abc123xy?width=0)

				![Extern](https://example.com/tracker.png)

				![Datei](file:/private/secrets.png)
				""", Variant.MATERIAL)), List.of(referenced, unused)));
		final Document html = html(fragment);

		assertThat(fragment.imageResources()).containsOnlyKeys("tt-image:abc123xy");
		assertThat(fragment.imageResources().get("tt-image:abc123xy").data()).containsExactly(1, 2, 3);
		assertThat(html.select("img")).hasSize(3);
		assertThat(html.selectFirst("img").attr("src")).isEqualTo("tt-image:abc123xy");
		assertThat(html.selectFirst("img").attr("style")).isEqualTo("width:50%;height:auto");
		assertThat(html.selectFirst("img").attr("width")).isEqualTo("100");
		assertThat(html.selectFirst("img").attr("height")).isEqualTo("50");
		assertThat(html.selectFirst("img").attr("title")).isEqualTo("Karte");
		assertThat(html.selectFirst("img").classNames())
				.containsExactlyInAnyOrder("tt-material-image", "tt-material-image-align-center");
		assertThat(html.select("img").get(1).classNames())
				.containsExactlyInAnyOrder("tt-material-image", "tt-material-image-align-right");
		assertThat(html.select("img").get(2).classNames())
				.containsExactlyInAnyOrder("tt-material-image", "tt-material-image-align-center");
		assertThat(html.select("img").get(1).hasAttr("style")).isFalse();
		assertThat(html.select(".tt-material-image-missing").eachText()).containsExactly(
				"Bild nicht verfügbar: Nicht gefunden", "Bild nicht verfügbar: Ungültige Breite", "Bild nicht verfügbar: Extern",
				"Bild nicht verfügbar: Datei");
		assertThat(fragment.html()).doesNotContain("https://example.com/tracker.png", "file:/private");
		assertThatThrownBy(() -> fragment.imageResources().put("tt-image:unused01", unused))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void doesNotExposeSuppliedMaterialImageResourcesFromOtherVariantsOrCode() {
		for (Variant variant : Variant.values()) {
			final var fragment = renderer.render(new RenderRequest(List.of(new Section("", """
					`![Inline](img:abc123xy)`

					```
					![Block](img:abc123xy)
					```
					""", variant)), List.of(image("abc123xy"))));
			assertThat(fragment.imageResources()).isEmpty();
			assertThat(html(fragment).select("img")).isEmpty();
		}
		for (Variant variant : List.of(Variant.STANDARD, Variant.REQUIREMENT)) {
			final var fragment = renderer.render(new RenderRequest(
					List.of(new Section("", "![Nur Material](img:abc123xy)", variant)), List.of(image("abc123xy"))));
			assertThat(fragment.imageResources()).isEmpty();
			assertThat(html(fragment).select(".tt-material-image-missing")).hasSize(1);
		}
	}

	@Test
	void highlightsRequirementCriteriaWithDeclaredMaximumPointsWithoutResultControls() {
		final Document html = html(render("""
				[**Beleg**](crit:proof?points=2.5) [Ohne Angabe](crit:default)
				[Klein](crit:small?points=0%2C5) [Maximal](crit:max?points=999)
				[Ungültig](crit:invalid?points=1000) [Falsch](crit:bad?other=4)
				[Mehrere Parameter](crit:bad?points=1&extra=2)
				`[Code](crit:code?points=5)`
				""", Variant.REQUIREMENT));

		assertThat(html.select(".tt-criterion-badge").eachText()).containsExactly("2,5", "1", "0,5", "999", "?", "?", "?");
		assertThat(html.selectFirst("mark strong").text()).isEqualTo("Beleg");
		assertThat(html.select("mark > .tt-criterion-badge")).hasSize(7);
		assertThat(html.select("input,button,a[href^=crit]")).isEmpty();
		assertThat(html.selectFirst("code").text()).isEqualTo("[Code](crit:code?points=5)");
	}

	@Test
	void doesNotIntroduceCriterionMarksOutsideRequirementVariant() {
		for (Variant variant : List.of(Variant.STANDARD, Variant.MATERIAL)) {
			final Document html = html(render("[**Text**](crit:only?points=3)", variant));
			assertThat(html.select("mark,.tt-criterion,a")).isEmpty();
			assertThat(html.selectFirst("strong").text()).isEqualTo("Text");
		}
	}

	@Test
	void keepsFootnotesLocalToEachSelectedSection() {
		final Document html = html(renderer.render(new RenderRequest(List.of(
				new Section("Erster Abschnitt", "Text[^1].\n\n[^1]: Erster Hinweis.", Variant.MATERIAL),
				new Section("Zweiter Abschnitt", "Text[^1].\n\n[^1]: Zweiter Hinweis.", Variant.MATERIAL)), List.of())));

		assertThat(html.select("sup.footnote-ref").eachText()).containsExactly("1", "1");
		assertThat(html.select("section.footnotes li").eachText()).containsExactly("Erster Hinweis.", "Zweiter Hinweis.");
		assertThat(html.select(".tt-render-section")).hasSize(2);
	}

	@Test
	void producesNoExecutableHtmlOrUntrustedResourceUris() {
		final Document html = html(render("""
				<script>alert('bad')</script>

				<img src="file:/private/secret" onerror="bad()" style="background:url(https://example.com)">

				<svg><image href="https://example.com/evil.svg"></image></svg>

				[Unsicher](javascript:alert%281%29) [Datei](file:/private/secret)
				![Daten](data:image/png;base64,AAAA)
				""", Variant.MATERIAL));

		assertThat(html.select("script,img,svg,iframe,object,embed,style,link")).isEmpty();
		assertThat(html.select("[onerror],[onclick],[style]")).isEmpty();
		assertThat(html.select("a[href]")).isEmpty();
		assertThat(html.select(".tt-material-image-missing").text()).isEqualTo("Bild nicht verfügbar: Daten");
	}

	@Test
	void rejectsAmbiguousImageSnapshots() {
		assertThatThrownBy(() -> renderer.render(new RenderRequest(List.of(new Section("", "", Variant.MATERIAL)),
				List.of(image("abc123xy"), image("abc123xy")))))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate material image identifier.");
	}

	private MarkdownFragmentHtmlRenderer.HtmlFragment render(final String markdown, final Variant variant) {
		return renderer.render(new RenderRequest(List.of(new Section("", markdown, variant)), List.of()));
	}

	private static Document html(final MarkdownFragmentHtmlRenderer.HtmlFragment fragment) {
		return Jsoup.parseBodyFragment(fragment.html());
	}

	private static LoeMaterialImage image(final String id) {
		return new LoeMaterialImage(id, "a".repeat(64), "image/png", 100, 50, 3, new byte[] { 1, 2, 3 });
	}
}
