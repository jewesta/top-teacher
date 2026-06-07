package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
			assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(PDRectangle.A5.getWidth(),
					offset());
			assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(PDRectangle.A5.getHeight(),
					offset());
		}

		final byte[] a4Pdf = renderer.imposeA5OnA4Landscape(a5Pdf);

		assertThat(a4Pdf).startsWith("%PDF".getBytes());
		try (PDDocument document = PDDocument.load(a4Pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(2);
			assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(PDRectangle.A4.getHeight(),
					offset());
			assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(PDRectangle.A4.getWidth(),
					offset());
		}
	}

	private static org.assertj.core.data.Offset<Float> offset() {
		return org.assertj.core.data.Offset.offset(1f);
	}
}
