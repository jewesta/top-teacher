package de.westarps.topteacher.backend.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.springframework.stereotype.Component;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@Component
public class OpenHtmlPdfRenderer implements PdfRenderer {

	private static final PDRectangle A4_LANDSCAPE =
			new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

	@Override
	public byte[] renderA5Pdf(final String html) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			final PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withHtmlContent(toXhtml(html), null);
			builder.toStream(output);
			builder.run();
			return output.toByteArray();
		} catch (final IOException | RuntimeException exception) {
			throw new IllegalStateException("PDF konnte nicht erzeugt werden.", exception);
		}
	}

	@Override
	public byte[] imposeA5OnA4Landscape(final byte[] a5Pdf) {
		try (PDDocument source = PDDocument.load(a5Pdf);
				PDDocument target = new PDDocument();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			final LayerUtility layerUtility = new LayerUtility(target);
			for (int sourcePageIndex = 0; sourcePageIndex < source.getNumberOfPages(); sourcePageIndex += 2) {
				final PDPage targetPage = new PDPage(A4_LANDSCAPE);
				target.addPage(targetPage);
				drawSourcePage(source, target, sourcePageIndex, targetPage, layerUtility, 0);
				if (sourcePageIndex + 1 < source.getNumberOfPages()) {
					drawSourcePage(source, target, sourcePageIndex + 1, targetPage, layerUtility, 1);
				}
			}
			target.save(output);
			return output.toByteArray();
		} catch (final IOException exception) {
			throw new IllegalStateException("PDF-Seiten konnten nicht auf A4 quer gesetzt werden.", exception);
		}
	}

	private static String toXhtml(final String html) {
		final Document document = Jsoup.parse(html == null ? "" : html);
		document.outputSettings()
				.syntax(Syntax.xml)
				.escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
				.charset(StandardCharsets.UTF_8)
				.prettyPrint(false);
		return document.html();
	}

	private static void drawSourcePage(final PDDocument source, final PDDocument target, final int sourcePageIndex,
			final PDPage targetPage, final LayerUtility layerUtility, final int slotIndex) throws IOException {
		final PDPage sourcePage = source.getPage(sourcePageIndex);
		final PDRectangle sourceBox = sourcePage.getCropBox();
		final PDFormXObject sourceForm = layerUtility.importPageAsForm(source, sourcePageIndex);

		final float targetWidth = targetPage.getMediaBox().getWidth();
		final float targetHeight = targetPage.getMediaBox().getHeight();
		final float slotWidth = targetWidth / 2;
		final float scale = Math.min(slotWidth / sourceBox.getWidth(), targetHeight / sourceBox.getHeight());
		final float x = slotIndex * slotWidth + (slotWidth - sourceBox.getWidth() * scale) / 2;
		final float y = (targetHeight - sourceBox.getHeight() * scale) / 2;

		try (PDPageContentStream contentStream =
				new PDPageContentStream(target, targetPage, AppendMode.APPEND, true, true)) {
			contentStream.transform(Matrix.getTranslateInstance(x, y));
			contentStream.transform(Matrix.getScaleInstance(scale, scale));
			contentStream.drawForm(sourceForm);
		}
	}
}
