package de.westarps.topteacher.backend.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
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
import com.openhtmltopdf.util.XRLog;

@Component
public class OpenHtmlPdfRenderer implements PdfRenderer {

	private static final PDRectangle A4_LANDSCAPE = new PDRectangle(PDRectangle.A4.getHeight(),
			PDRectangle.A4.getWidth());
	private static final String TEMPLATE_BASE_URI = OpenHtmlPdfRenderer.class.getClassLoader()
			.getResource("templates/export/").toExternalForm();

	// OpenHTMLToPDF writes its own JUL-style messages directly to the console
	// unless disabled.
	static {
		XRLog.setLoggingEnabled(false);
	}

	@Override
	public byte[] renderA5Pdf(final String html) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			final PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withHtmlContent(toXhtml(html), TEMPLATE_BASE_URI);
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

	@Override
	public byte[] merge(final List<byte[]> pdfs) {
		final List<byte[]> sourcePdfs = List.copyOf(Objects.requireNonNull(pdfs, "pdfs must not be null"));
		if (sourcePdfs.isEmpty()) {
			throw new IllegalArgumentException("pdfs must not be empty");
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			final PDFMergerUtility merger = new PDFMergerUtility();
			sourcePdfs.forEach(pdf -> merger.addSource(new ByteArrayInputStream(pdf)));
			merger.setDestinationStream(output);
			merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
			return output.toByteArray();
		} catch (final IOException exception) {
			throw new IllegalStateException("PDF-Dokumente konnten nicht zusammengeführt werden.", exception);
		}
	}

	private static String toXhtml(final String html) {
		final Document document = Jsoup.parse(html == null ? "" : html);
		document.outputSettings().syntax(Syntax.xml).escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
				.charset(StandardCharsets.UTF_8).prettyPrint(false);
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

		try (PDPageContentStream contentStream = new PDPageContentStream(target, targetPage, AppendMode.APPEND, true,
				true)) {
			contentStream.transform(Matrix.getTranslateInstance(x, y));
			contentStream.transform(Matrix.getScaleInstance(scale, scale));
			contentStream.drawForm(sourceForm);
		}
	}
}
