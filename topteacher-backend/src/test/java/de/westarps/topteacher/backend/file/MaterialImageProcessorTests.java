package de.westarps.topteacher.backend.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class MaterialImageProcessorTests {

	private final MaterialImageProcessor processor = new MaterialImageProcessor();

	@Test
	void normalizesPngWithAlphaAndDeterministicContentHash() throws Exception {
		final BufferedImage source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
		source.setRGB(0, 0, 0x7fff0000);
		final byte[] bytes = encode(source, "png");

		final var first = processor.process("image001", bytes);
		final var second = processor.process("image002", bytes);

		assertThat(first.mediaType()).isEqualTo("image/png");
		assertThat(first.contentHash()).isEqualTo(second.contentHash()).matches("[a-f0-9]{64}");
		assertThat(ImageIO.read(new ByteArrayInputStream(first.data())).getRGB(0, 0)).isEqualTo(0x7fff0000);
	}

	@Test
	void limitsNormalizedImagesToThreeThousandPixels() throws Exception {
		final BufferedImage source = new BufferedImage(4_000, 2, BufferedImage.TYPE_INT_RGB);

		final var image = processor.process("image001", encode(source, "jpeg"));

		assertThat(image.pixelWidth()).isEqualTo(3_000);
		assertThat(image.pixelHeight()).isEqualTo(2);
		assertThat(image.mediaType()).isEqualTo("image/jpeg");
	}

	@Test
	void rejectsNonImagesGifAndOversizedInput() throws Exception {
		assertThatThrownBy(() -> processor.process("image001", "not an image".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PNG- oder JPEG");
		final byte[] gif = encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "gif");
		assertThatThrownBy(() -> processor.process("image001", gif))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PNG- oder JPEG");
		assertThatThrownBy(() -> processor.process("image001", new byte[MaterialImageProcessor.MAX_UPLOAD_BYTES + 1]))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 MiB");
	}

	@Test
	void rejectsAnImageOverFortyMegapixelsBeforeDecodingItsPixels() throws Exception {
		final byte[] png = encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png");
		final ByteBuffer header = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
		header.putInt(16, 10_000);
		header.putInt(20, 5_000);
		final CRC32 checksum = new CRC32();
		checksum.update(png, 12, 17);
		header.putInt(29, (int) checksum.getValue());

		assertThatThrownBy(() -> processor.process("image001", png))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("40 Megapixel");
	}

	@Test
	void appliesJpegOrientationAndRemovesExifMetadata() throws Exception {
		final byte[] jpeg = encode(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "jpeg");
		final ByteBuffer exif = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
		exif.put("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
		exif.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
		exif.putShort((short) 1).putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) 6)
				.putShort((short) 0).putInt(0);
		final ByteArrayOutputStream oriented = new ByteArrayOutputStream();
		oriented.write(jpeg, 0, 2);
		oriented.write(new byte[] { (byte) 0xff, (byte) 0xe1, 0, 34 });
		oriented.write(exif.array());
		oriented.write(jpeg, 2, jpeg.length - 2);

		final var image = processor.process("image001", oriented.toByteArray());

		assertThat(image.pixelWidth()).isEqualTo(3);
		assertThat(image.pixelHeight()).isEqualTo(2);
		assertThat(new String(image.data(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	private static byte[] encode(final BufferedImage image, final String format) throws Exception {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, format, output);
		return output.toByteArray();
	}
}
