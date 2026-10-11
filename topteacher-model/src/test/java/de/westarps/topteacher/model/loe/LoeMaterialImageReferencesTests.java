package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoeMaterialImageReferencesTests {

	@Test
	void ignoresCaptionExamplesLinksPlainTextAndFencedOrInlineCode() {
		assertThat(LoeMaterialImageReferences.ids("""
			![Caption (img:deadbeef) with [nested text]](img:abcdefgh)
			[Ordinary link](img:deadbeef)
			Plain (img:deadbeef)
			`![Inline example](img:deadbeef)`
			```markdown
			![Fenced example](img:deadbeef)
			```
			![![Nested alt image](img:deadbeef)](img:abcdefgh)
			""")).containsExactly("abcdefgh");
	}

	@Test
	void changesOnlyActualImageDestinationsAndLeavesCaptionAndCodeExamplesUntouched() {
		final String markdown = """
			![Caption (img:4n8k2p7c) with [nested text]](img:4n8k2p7c?width=75)
			[Link](img:4n8k2p7c) plain (img:4n8k2p7c)
			`![Inline example](img:4n8k2p7c)`
			```markdown
			![Fenced example](img:4n8k2p7c)
			```
			![![Nested alt image](img:4n8k2p7c)](img:4n8k2p7c "Title (img:4n8k2p7c)")
			""";
		assertThat(LoeMaterialImageReferences.replaceId(markdown, "4n8k2p7c", "abcdefgh")).isEqualTo("""
			![Caption (img:4n8k2p7c) with [nested text]](img:abcdefgh?width=75)
			[Link](img:4n8k2p7c) plain (img:4n8k2p7c)
			`![Inline example](img:4n8k2p7c)`
			```markdown
			![Fenced example](img:4n8k2p7c)
			```
			![![Nested alt image](img:4n8k2p7c)](img:abcdefgh "Title (img:4n8k2p7c)")
			""");
	}

	@Test
	void supportsRenderedReferenceImagesWithoutChangingUnusedDefinitions() {
		final String markdown = """
			![Chart][Used]
			![Used][]

			[Used]: img:4n8k2p7c?width=50 "Chart"
			[Unused]: img:4n8k2p7c
			""";
		assertThat(LoeMaterialImageReferences.ids(markdown)).containsExactly("4n8k2p7c");
		assertThat(LoeMaterialImageReferences.replaceId(markdown, "4n8k2p7c", "abcdefgh")).isEqualTo("""
			![Chart][Used]
			![Used][]

			[Used]: img:abcdefgh?width=50 "Chart"
			[Unused]: img:4n8k2p7c
			""");
	}

	@Test
	void unescapesOpeningAndClosingBracketsWhenEditingAnImageCaption() {
		final String markdown = "![Chart \\[A\\]](img:abcdefgh)";
		assertThat(LoeMaterialImageReferences.findAt(markdown, 3, 3)).contains(
				new LoeMaterialImageReferences.Occurrence("abcdefgh", "Chart [A]", null, 0, markdown.length()));
	}

	@Test
	void findsManagedImageReferencesAndIgnoresOtherImages() {
		assertThat(LoeMaterialImageReferences.ids("""
			![Diagramm](img:4n8k2p7c?width=75)
			![Foto](img:abcdefgh)
			![Extern](https://example.com/image.png)
			![Ungültig](img:TOO-LONG)
			""")).containsExactlyInAnyOrder("4n8k2p7c", "abcdefgh");
	}

	@Test
	void replacesOnlyTheManagedImageIdentifier() {
		assertThat(LoeMaterialImageReferences.replaceId(
				"![Diagramm](img:4n8k2p7c?width=75) img:4n8k2p7c", "4n8k2p7c", "abcdefgh"))
				.isEqualTo("![Diagramm](img:abcdefgh?width=75) img:4n8k2p7c");
	}

	@Test
	void findsTheManagedImageOccurrenceAtTheCursor() {
		final String markdown = "Vorspann ![Diagramm \\] A](img:4n8k2p7c?width=50) Nachspann";

		assertThat(LoeMaterialImageReferences.findAt(markdown, markdown.indexOf("Diagramm"),
				markdown.indexOf("Diagramm")))
				.contains(new LoeMaterialImageReferences.Occurrence("4n8k2p7c", "Diagramm ] A", 50,
						markdown.indexOf("!["), markdown.indexOf(")") + 1));
	}

	@Test
	void findsAnOccurrenceIntersectedByASelectionAndPreservesAutomaticWidth() {
		final String markdown = "![Foto](img:abcdefgh)";

		assertThat(LoeMaterialImageReferences.findAt(markdown, 2, 6))
				.contains(new LoeMaterialImageReferences.Occurrence("abcdefgh", "Foto", null, 0, markdown.length()));
	}

	@Test
	void ignoresASelectionOutsideManagedImageDeclarations() {
		final String markdown = "Text ![Extern](https://example.com/image.png)";

		assertThat(LoeMaterialImageReferences.findAt(markdown, 0, 4)).isEmpty();
	}
}
