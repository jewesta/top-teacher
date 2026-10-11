package de.westarps.topteacher.backend.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderedPng;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Section;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.topteacher.model.loe.LoeMaterialImage;

class Java2DFaithfulMarkdownRendererTests {

	private static final int IMAGE_COLOR = 0xef2a6b;

	@Test
	void producesReadablePngAndLetsSelectedContentDetermineItsHeight() throws Exception {
		final RenderedPng shortResult = render(new RenderRequest(
				List.of(new Section("Task", "**One selected task.**", Variant.STANDARD)), List.of()));
		final RenderedPng richResult = render(new RenderRequest(List.of(
				new Section("Material", """
				   > [!NOTE]
				   Read the **source** carefully.

				   > [!NUMBERED]
				   > First source line.[^1]
				   > Second source line.
				   > Third source line.
				   > Fourth source line.
				   > Fifth source line.
				   > Sixth source line.

				   [^1]: A local footnote.

				   | Source | Date |
				   | --- | --- |
				   | Letter | 1950 |
				   """, Variant.MATERIAL),
				new Section("Requirement", "[Use evidence](crit:evidence?points=0.5).", Variant.REQUIREMENT)), List.of()));
		final BufferedImage shortImage = decode(shortResult);
		final BufferedImage richImage = decode(richResult);
		try {
			assertThat(shortImage.getWidth()).isEqualTo(960);
			assertThat(shortImage.getHeight()).isBetween(50, 500);
			assertThat(richImage.getWidth()).isEqualTo(960);
			assertThat(richImage.getHeight()).isGreaterThan(shortImage.getHeight()).isLessThan(2_000);
			assertThat(nonWhitePixels(shortImage)).isGreaterThan(100);
			assertThat(pixelsOfColor(richImage, 0x1678be)).isGreaterThan(50);
			assertThat(pixelsOfColor(richImage, 0xd9ebd3)).isGreaterThan(100);
		} finally {
			shortImage.flush();
			richImage.flush();
		}
	}

	@Test
	void paintsRepeatedManagedImagesAtTheirRequestedWidthAndPreservesAspectRatio() throws Exception {
		final byte[] imageBytes = coloredPng(32, 16);
		final LoeMaterialImage image = image("image001", 32, 16, imageBytes);
		final RenderedPng result = render(new RenderRequest(List.of(new Section("", """
			![First copy](img:image001?width=40)

			Zwischentext.

			![Second copy](img:image001?width=40)
			""", Variant.MATERIAL)), List.of(image)));
		final BufferedImage rendered = decode(result);
		try {
			assertThat(rendered.getWidth()).isEqualTo(960);
			final List<ImageBand> bands = imageBands(rendered);
			assertThat(bands).hasSize(2);
			for (final ImageBand band : bands) {
				assertThat(band.width()).isBetween(336, 384);
				assertThat(Math.abs(band.width() - 2 * band.height())).isLessThanOrEqualTo(3);
			}
			assertThat(bands.get(0).width()).isEqualTo(bands.get(1).width());
			assertThat(bands.get(0).height()).isEqualTo(bands.get(1).height());
		} finally {
			rendered.flush();
		}
	}

	@Test
	void alignsManagedImagesWithinTheAvailableContentWidth() throws Exception {
		final LoeMaterialImage image = image("image001", 32, 16, coloredPng(32, 16));
		final BufferedImage rendered = decode(render(new RenderRequest(List.of(new Section("", """
			![Left](img:image001?width=25)

			![Center](img:image001?width=25&align=center)

			![Right](img:image001?width=25&align=right)

			![Automatic center](img:image001?align=center)
			""", Variant.MATERIAL)), List.of(image))));
		try {
			final List<ImageBand> bands = imageBands(rendered);
			assertThat(bands).hasSize(4);
			assertThat(bands.get(0).left()).isLessThan(100);
			assertThat(bands.get(1).left()).isBetween(300, 450);
			assertThat(bands.get(2).left()).isGreaterThan(650);
			assertThat(bands.get(3).left()).isBetween(430, 500);
			assertThat(bands.subList(0, 3).stream().map(ImageBand::width).distinct()).hasSize(1);
			assertThat(bands.get(3).width()).isBetween(30, 34);
		} finally {
			rendered.flush();
		}
	}

	@Test
	void scalesImagesWithinTheirContainingBlockWithoutDistortingThem() throws Exception {
		final byte[] imageBytes = coloredPng(32, 16);
		final RenderedPng result = render(new RenderRequest(List.of(new Section("", """
			![Full content](img:image001?width=50)

			> ![Inside quote](img:image001?width=50)
			""", Variant.MATERIAL)), List.of(image("image001", 32, 16, imageBytes))));
		final BufferedImage rendered = decode(result);
		try {
			final List<ImageBand> bands = imageBands(rendered);
			assertThat(bands).hasSize(2);
			assertThat(bands.get(0).width()).isBetween(454, 458);
			assertThat(bands.get(1).width()).isBetween(443, 449);
			assertThat(bands.get(0).width() - bands.get(1).width()).isBetween(5, 20);
			for (final ImageBand band : bands) {
				assertThat(Math.abs(band.width() - 2 * band.height())).isLessThanOrEqualTo(3);
			}
		} finally {
			rendered.flush();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "paragraph", "code" })
	void wrapsLongUnbrokenTokensInsteadOfClippingTheirContent(final String format) throws Exception {
		final BufferedImage shortImage = decode(render(new RenderRequest(List.of(new Section("",
				formatText("Short source text.", format), Variant.MATERIAL)), List.of())));
		final BufferedImage longImage = decode(render(new RenderRequest(List.of(new Section("",
				formatText("W".repeat(900), format), Variant.MATERIAL)), List.of())));
		try {
			assertThat(longImage.getWidth()).isEqualTo(960);
			assertThat(longImage.getHeight()).isGreaterThan(shortImage.getHeight() + 100);
			assertThat(darkPixels(longImage)).isGreaterThan(darkPixels(shortImage) * 10);
		} finally {
			shortImage.flush();
			longImage.flush();
		}
	}

	@Test
	void rejectsNumberedLinesThatCannotFitWithoutWrapping() {
		assertThatThrownBy(() -> render(new RenderRequest(List.of(new Section("",
				"> [!NUMBERED]\n> " + "W".repeat(900), Variant.MATERIAL)), List.of())))
				.isInstanceOf(IllegalStateException.class).hasMessage("PNG rendering failed.")
				.rootCause().hasMessageContaining("overflow");
	}

	@Test
	void rejectsForgedEncodedDimensionsBeforeDecodingTheLargeRaster() throws Exception {
		final byte[] png = coloredPng(1, 1);
		changePngDimensions(png, 50_000, 50_000);
		final LoeMaterialImage disguised = image("image001", 1, 1, png);
		final var renderer = new Java2DFaithfulMarkdownRenderer(30);
		try {
			assertThatThrownBy(() -> renderer.render(new RenderRequest(
					List.of(new Section("", "![Source](img:image001)", Variant.MATERIAL)), List.of(disguised))))
					.isInstanceOf(IllegalStateException.class)
					.hasRootCauseMessage("Encoded material image dimensions exceed the rendering budget.");
		} finally {
			renderer.close();
		}
	}

	@Test
	void rejectsTheAggregateImagePixelBudgetBeforeRendering() throws Exception {
		final byte[] png = coloredPng(1, 1);
		changePngDimensions(png, 3_000, 3_000);
		final var renderer = new Java2DFaithfulMarkdownRenderer(30);
		try {
			assertThatThrownBy(() -> renderer.render(new RenderRequest(List.of(new Section("",
					"![First](img:image001) ![Second](img:image002)", Variant.MATERIAL)),
					List.of(image("image001", 3_000, 3_000, png), image("image002", 3_000, 3_000, png)))))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Material images exceed");
		} finally {
			renderer.close();
		}
	}

	@Test
	void rejectsOversizedMarkdownRatherThanReturningPartialContent() {
		final var renderer = new Java2DFaithfulMarkdownRenderer(30);
		try {
			assertThatThrownBy(() -> renderer.render(new RenderRequest(
					List.of(new Section("", "x".repeat(200_001), Variant.STANDARD)), List.of())))
					.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Markdown exceeds");
		} finally {
			renderer.close();
		}
	}

	@Test
	void rejectsUnsafeCanvasDimensionsBeforeAllocatingAnImage() {
		try (var pages = new Java2DFaithfulMarkdownRenderer.BoundedPageProcessor()) {
			for (final int[] dimensions : List.of(new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE },
					new int[] { 961, 1 }, new int[] { 960, 12_001 }, new int[] { 0, 1 }, new int[] { 1, 0 })) {
				assertThatThrownBy(() -> pages.createPage(0, dimensions[0], dimensions[1]))
						.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dimensions exceed");
				assertThatThrownBy(pages::image).isInstanceOf(IllegalStateException.class);
			}
			pages.createPage(0, 960, 2);
			assertThat(pages.image().getWidth()).isEqualTo(960);
			assertThat(pages.image().getHeight()).isEqualTo(2);
			assertThat(pages.image().getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
			assertThatThrownBy(() -> pages.createPage(1, 960, 2)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void boundsEncodedOutputBeforeAppendingTheOversizedWrite() throws Exception {
		final var output = new Java2DFaithfulMarkdownRenderer.LimitedOutputStream(4);
		output.write(new byte[] { 1, 2 });
		assertThatThrownBy(() -> output.write(new byte[] { 3, 4, 5 })).isInstanceOf(IOException.class);
		assertThat(output.toByteArray()).containsExactly(1, 2);
		output.write(3);
		output.write(4);
		assertThatThrownBy(() -> output.write(5)).isInstanceOf(IOException.class);
		assertThat(output.toByteArray()).containsExactly(1, 2, 3, 4);
	}

	private static RenderedPng render(final RenderRequest request) {
		final var renderer = new Java2DFaithfulMarkdownRenderer(30);
		try {
			return renderer.render(request);
		} finally {
			renderer.close();
		}
	}

	private static BufferedImage decode(final RenderedPng result) throws IOException {
		final BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.data()));
		assertThat(image).isNotNull();
		assertThat(image.getWidth()).isEqualTo(result.pixelWidth());
		assertThat(image.getHeight()).isEqualTo(result.pixelHeight());
		return image;
	}

	private static byte[] coloredPng(final int width, final int height) throws IOException {
		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(IMAGE_COLOR));
			graphics.fillRect(0, 0, width, height);
			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			assertThat(ImageIO.write(image, "png", output)).isTrue();
			return output.toByteArray();
		} finally {
			graphics.dispose();
			image.flush();
		}
	}

	private static LoeMaterialImage image(final String id, final int width, final int height, final byte[] data) {
		return new LoeMaterialImage(id, "a".repeat(64), "image/png", width, height, data.length, data);
	}

	private static void changePngDimensions(final byte[] png, final int width, final int height) {
		final ByteBuffer header = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
		header.putInt(16, width);
		header.putInt(20, height);
		final CRC32 checksum = new CRC32();
		checksum.update(png, 12, 17);
		header.putInt(29, (int) checksum.getValue());
	}

	private static long nonWhitePixels(final BufferedImage image) {
		return (long) image.getWidth() * image.getHeight() - pixelsOfColor(image, 0xffffff);
	}

	private static String formatText(final String text, final String format) {
		return switch (format) {
			case "code" -> "```\n" + text + "\n```";
			default -> text;
		};
	}

	private static long darkPixels(final BufferedImage image) {
		long count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				final int rgb = image.getRGB(x, y);
				if (((rgb >>> 16) & 255) < 100 && ((rgb >>> 8) & 255) < 100 && (rgb & 255) < 100) {
					count++;
				}
			}
		}
		return count;
	}

	private static long pixelsOfColor(final BufferedImage image, final int rgb) {
		long count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0xffffff) == rgb) {
					count++;
				}
			}
		}
		return count;
	}

	private static List<ImageBand> imageBands(final BufferedImage image) {
		final List<ImageBand> bands = new ArrayList<>();
		int start = -1;
		int left = image.getWidth();
		int width = 0;
		for (int y = 0; y <= image.getHeight(); y++) {
			int rowWidth = 0;
			int rowLeft = image.getWidth();
			if (y < image.getHeight()) {
				for (int x = 0; x < image.getWidth(); x++) {
					if ((image.getRGB(x, y) & 0xffffff) == IMAGE_COLOR) {
						rowLeft = Math.min(rowLeft, x);
						rowWidth++;
					}
				}
			}
			if (rowWidth > 0) {
				if (start < 0) {
					start = y;
					left = rowLeft;
				} else if (rowLeft != left) {
					bands.add(new ImageBand(left, width, y - start));
					start = y;
					left = rowLeft;
					width = 0;
				}
				width = Math.max(width, rowWidth);
			} else if (start >= 0) {
				bands.add(new ImageBand(left, width, y - start));
				start = -1;
				left = image.getWidth();
				width = 0;
			}
		}
		return bands;
	}

	private record ImageBand(int left, int width, int height) {
	}
}
