package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class MaterialImageProcessorTests {

	private final MaterialImageProcessor processor = new MaterialImageProcessor();

	@Test
	void validatesAndNormalizesPngImages() throws Exception {
		final BufferedImage source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
		source.setRGB(0, 0, 0x7fff0000);

		final var image = processor.process("4n8k2p7c", encode(source, "png"));

		assertThat(image.mediaType()).isEqualTo("image/png");
		assertThat(image.pixelWidth()).isEqualTo(2);
		assertThat(image.pixelHeight()).isEqualTo(3);
		assertThat(image.contentHash()).matches("[a-f0-9]{64}");
		assertThat(ImageIO.read(new java.io.ByteArrayInputStream(image.data())).getColorModel().hasAlpha()).isTrue();
	}

	@Test
	void resizesImagesAboveTheLongestSideLimit() throws Exception {
		final BufferedImage source = new BufferedImage(4_000, 2, BufferedImage.TYPE_INT_RGB);

		final var image = processor.process("abcdefgh", encode(source, "jpeg"));

		assertThat(image.pixelWidth()).isEqualTo(3_000);
		assertThat(image.pixelHeight()).isEqualTo(2);
		assertThat(image.mediaType()).isEqualTo("image/jpeg");
	}

	@Test
	void rejectsUnsupportedAndOversizedUploads() {
		assertThatThrownBy(() -> processor.process("abcdefgh", "not an image".getBytes()))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Bitte wähle eine PNG- oder JPEG-Datei.");
		assertThatThrownBy(
				() -> processor.process("abcdefgh", new byte[MaterialImageProcessor.MAX_UPLOAD_BYTES + 1]))
					.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 MiB");
	}

	private static byte[] encode(final BufferedImage image, final String format) throws Exception {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, format, output);
		return output.toByteArray();
	}
}
