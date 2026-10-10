package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class OpenHtmlPdfRendererTests {

	private final OpenHtmlPdfRenderer renderer = new OpenHtmlPdfRenderer();

	@Test
	void rendersA5HtmlAndImposesTwoPagesOnA4Landscape() throws IOException {
		final byte[] a5Pdf = renderer.renderA5Pdf("""
			<!doctype html>
			<html>
			<head>
			    <meta charset="utf-8">
			    <style>
			        @page { size: A5 portrait; margin: 10mm; }
			        body { font-family: Arial, sans-serif; }
			        .page { page-break-after: always; }
			    </style>
			</head>
			<body>
			    <section class="page">Seite 1</section>
			    <section class="page">Seite 2</section>
			    <section>Seite 3</section>
			</body>
			</html>
			""");

		assertThat(a5Pdf).startsWith("%PDF".getBytes());
		try (PDDocument document = PDDocument.load(a5Pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(3);
			assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(PDRectangle.A5.getWidth(), offset());
			assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(PDRectangle.A5.getHeight(), offset());
		}

		final byte[] a4Pdf = renderer.imposeA5OnA4Landscape(a5Pdf);

		assertThat(a4Pdf).startsWith("%PDF".getBytes());
		try (PDDocument document = PDDocument.load(a4Pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(2);
			assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(PDRectangle.A4.getHeight(), offset());
			assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(PDRectangle.A4.getWidth(), offset());
		}
	}

	@Test
	void rendersAlertBoxesWithAllLabelsAndWrappedContent() throws IOException {
		final String css;
		try (var stream = getClass().getResourceAsStream("/templates/export/level-of-expectations.css")) {
			css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
		final Sanitizer sanitizer = new Sanitizer();
		final StringBuilder content = new StringBuilder();
		for (final String type : List.of("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION")) {
			content.append(sanitizer.markdownToHtml(
					"> [!" + type + "]\n> **Text** mit mehreren Worten. "
							+ "Dieser Hinweis darf umbrechen. ".repeat(15) + "Ende " + type + ".",
					Sanitizer.MarkdownView.PUPIL).value());
		}
		final byte[] pdf = renderer.renderA5Pdf("<html><head><style>" + css
				+ "</style></head><body>" + content + "</body></html>");
		try (PDDocument document = PDDocument.load(pdf)) {
			assertThat(new PDFTextStripper().getText(document)).contains("Hinweis", "Tipp", "Wichtig", "Warnung",
					"Vorsicht", "Ende NOTE.", "Ende TIP.", "Ende IMPORTANT.", "Ende WARNING.", "Ende CAUTION.")
					.doesNotContain("[!");
			int vectorIcons = 0;
			for (final var page : document.getPages()) {
				for (final var name : page.getResources().getXObjectNames()) {
					final var object = page.getResources().getXObject(name);
					assertThat(object).isInstanceOf(PDFormXObject.class);
					final var form = (PDFormXObject) object;
					assertThat(form.getResources().getXObjectNames()).isEmpty();
					try (var stream = form.getContentStream().createInputStream()) {
						assertThat(stream.readAllBytes()).isNotEmpty();
					}
					vectorIcons++;
				}
			}
			assertThat(vectorIcons).isEqualTo(5);
		}
	}

	@Test
	void mergesPdfDocumentsInSourceOrder() throws IOException {
		final byte[] first = renderer.renderA5Pdf("<html><body>Erstes Dokument</body></html>");
		final byte[] second = renderer.renderA5Pdf("<html><body>Zweites Dokument</body></html>");

		final byte[] merged = renderer.merge(List.of(first, second));

		try (PDDocument document = PDDocument.load(merged)) {
			assertThat(document.getNumberOfPages()).isEqualTo(2);
			assertThat(new PDFTextStripper().getText(document)).containsSubsequence("Erstes Dokument",
					"Zweites Dokument");
		}
	}

	private static org.assertj.core.data.Offset<Float> offset() {
		return org.assertj.core.data.Offset.offset(1f);
	}
}
