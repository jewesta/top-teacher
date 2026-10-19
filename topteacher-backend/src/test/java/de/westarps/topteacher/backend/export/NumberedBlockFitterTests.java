package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

class NumberedBlockFitterTests {

	private final OpenHtmlPdfRenderer renderer = new OpenHtmlPdfRenderer();

	@Test
	void shortBlocksStayAtNinePointsAndUseActualGlyphWidths() throws Exception {
		final String narrow = "i".repeat(100);
		final String wide = "W".repeat(100);
		final var glyphs = glyphs(render(block("NARROW" + narrow, "SHORT") + block("WIDE" + wide, "UNIFORM") + "<p>ORDINARY</p>"));
		assertThat(font(glyphs, "NARROW")).isCloseTo(9f, offset(0.001f));
		assertThat(font(glyphs, "SHORT")).isCloseTo(9f, offset(0.001f));
		assertThat(font(glyphs, "WIDE")).isLessThan(9f).isGreaterThan(0f);
		assertThat(font(glyphs, "UNIFORM")).isCloseTo(font(glyphs, "WIDE"), offset(0.001f));
		assertThat(font(glyphs, "ORDINARY")).isCloseTo(9f, offset(0.001f));
		assertWithinPage(glyphs);
	}

	@Test
	void longFormattedLineDoesNotWrapAndSetsOneSizeForTheWholeBlock() throws Exception {
		final String line = "START <strong>" + "WW Wide words ".repeat(18)
				+ "</strong><em>Italic ending</em><sup>27</sup> END";
		final var glyphs = glyphs(render(block(line, "SECOND LINE") + "<p>AFTER</p>"));
		final var start = run(glyphs, "START");
		final var end = run(glyphs, "END");
		assertThat(end.page()).isEqualTo(start.page());
		assertThat(end.text().getYDirAdj()).isCloseTo(start.text().getYDirAdj(), offset(0.01f));
		assertThat(font(glyphs, "SECOND")).isCloseTo(font(glyphs, "START"), offset(0.001f));
		assertThat(font(glyphs, "START")).isLessThan(9f);
		assertThat(font(glyphs, "AFTER")).isCloseTo(9f, offset(0.001f));
		assertWithinPage(glyphs);
	}

	@Test
	void fittingUsesAvailableContainerWidthIncludingPadding() throws Exception {
		final String text = "START " + "Wide words ".repeat(14) + " END";
		final float fullSize = font(glyphs(render(block(text))), "START");
		final var narrow = glyphs(render("<div style='margin-left:12mm; padding:3mm; width:60mm'>" + block(text) + "</div>"));
		assertThat(font(narrow, "START")).isLessThan(fullSize);
		final var end = run(narrow, "END").text();
		assertThat(end.getXDirAdj() + end.getWidthDirAdj()).isLessThanOrEqualTo(83f * 72 / 25.4f + 0.2f);
		assertThat(end.getYDirAdj()).isCloseTo(run(narrow, "START").text().getYDirAdj(), offset(0.01f));
	}

	@Test
	void fittingIsUniformAcrossPageBreaksAndKeepsAllLogicalLines() throws Exception {
		final String[] lines = IntStream.rangeClosed(1, 150)
				.mapToObj(number -> String.format("ROW%03d ", number) + "Wide words ".repeat(12) + "END" + number)
				.toArray(String[]::new);
		final byte[] pdf = render(block(lines));
		final var glyphs = glyphs(pdf);
		assertThat(glyphs.stream().map(Glyph::page).distinct().count()).isGreaterThan(1);
		final float size = font(glyphs, "ROW001");
		assertThat(size).isLessThan(9f);
		for (int row = 1; row <= 150; row++) {
			final var first = run(glyphs, String.format("ROW%03d", row));
			final var last = run(glyphs, "END" + row);
			assertThat(first.text().getFontSizeInPt()).isCloseTo(size, offset(0.001f));
			assertThat(last.page()).isEqualTo(first.page());
			assertThat(last.text().getYDirAdj()).as("Row %d stays on one baseline", row).isCloseTo(first.text().getYDirAdj(), offset(0.01f));
		}
		assertWithinPage(glyphs);
		final String review = System.getProperty("tt.pdfReviewDirectory");
		if (review != null) {
			Files.createDirectories(Path.of(review));
			Files.write(Path.of(review, "numbered-multipage.pdf"), pdf);
		}
	}

	private byte[] render(final String body) {
		return renderer.renderA5Pdf("<html><head><link rel='stylesheet' href='exam-task-sheet.css'/></head><body>" + body + "</body></html>");
	}

	private String block(final String... lines) {
		return "<table class='ws-markdown-numbered'><tbody>" + IntStream.range(0, lines.length)
				.mapToObj(index -> "<tr><td class='ws-markdown-line-number'>" + (index + 1) + "</td><td class='ws-markdown-line-text'>" + lines[index] + "</td></tr>")
				.collect(Collectors.joining()) + "</tbody></table>";
	}

	private static float font(final List<Glyph> glyphs, final String word) {
		return run(glyphs, word).text().getFontSizeInPt();
	}

	private static Glyph run(final List<Glyph> glyphs, final String word) {
		for (int start = 0; start < glyphs.size(); start++) {
			final StringBuilder text = new StringBuilder();
			for (int at = start; at < glyphs.size() && text.length() < word.length(); at++) {
				if (glyphs.get(at).page() != glyphs.get(start).page()
						|| Math.abs(glyphs.get(at).text().getYDirAdj() - glyphs.get(start).text().getYDirAdj()) > 0.05f) {
					break;
				}
				text.append(glyphs.get(at).text().getUnicode());
			}
			if (text.toString().equals(word)) {
				return glyphs.get(start);
			}
		}
		throw new AssertionError("PDF text not found: " + word);
	}

	private static void assertWithinPage(final List<Glyph> glyphs) {
		for (final var glyph : glyphs) {
			assertThat(glyph.text().getXDirAdj()).isGreaterThanOrEqualTo(8f * 72 / 25.4f - 0.2f);
			assertThat(glyph.text().getXDirAdj() + glyph.text().getWidthDirAdj()).isLessThanOrEqualTo(140f * 72 / 25.4f + 0.2f);
		}
	}

	private static List<Glyph> glyphs(final byte[] pdf) throws IOException {
		final List<Glyph> result = new ArrayList<>();
		try (final var document = PDDocument.load(pdf)) {
			new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition position) {
					result.add(new Glyph(getCurrentPageNo(), position));
					super.processTextPosition(position);
				}
			}.getText(document);
		}
		return result;
	}

	private record Glyph(int page, TextPosition text) { }
}
