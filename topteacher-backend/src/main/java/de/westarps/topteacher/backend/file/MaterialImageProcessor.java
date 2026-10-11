package de.westarps.topteacher.backend.file;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Service;

import de.westarps.topteacher.model.loe.LoeMaterialImage;

@Service
public final class MaterialImageProcessor {

	public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
	public static final long MAX_DECODED_PIXELS = 40_000_000L;
	public static final int MAX_LONGEST_SIDE = 3_000;
	private static final float JPEG_QUALITY = 0.9f;

	public LoeMaterialImage process(final String id, final byte[] uploadedData) {
		if (uploadedData == null || uploadedData.length == 0) {
			throw new IllegalArgumentException("Bitte wähle eine PNG- oder JPEG-Datei.");
		}
		if (uploadedData.length > MAX_UPLOAD_BYTES) {
			throw new IllegalArgumentException("Das Bild darf höchstens 10 MiB groß sein.");
		}

		try (ImageInputStream input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(uploadedData))) {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw unsupportedImage();
			}
			final ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				final String format = normalizedFormat(reader.getFormatName());
				final int width = reader.getWidth(0);
				final int height = reader.getHeight(0);
				if ((long) width * height > MAX_DECODED_PIXELS) {
					throw new IllegalArgumentException("Das Bild ist zu groß. Es darf höchstens 40 Megapixel haben.");
				}
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw unsupportedImage();
				}
				if ("jpeg".equals(format)) {
					image = orient(image, jpegOrientation(uploadedData));
				}
				image = resize(image);
				final byte[] normalizedData = write(image, format);
				if (normalizedData.length > MAX_UPLOAD_BYTES) {
					throw new IllegalArgumentException("Das aufbereitete Bild ist weiterhin größer als 10 MiB.");
				}
				return new LoeMaterialImage(id, sha256(normalizedData), "jpeg".equals(format) ? "image/jpeg" : "image/png",
						image.getWidth(), image.getHeight(), normalizedData.length, normalizedData);
			} finally {
				reader.dispose();
			}
		} catch (final IOException exception) {
			throw new IllegalArgumentException("Das Bild konnte nicht gelesen werden.", exception);
		}
	}

	private static String normalizedFormat(final String formatName) {
		final String format = formatName == null ? "" : formatName.toLowerCase(java.util.Locale.ROOT);
		if (format.equals("jpg") || format.equals("jpeg")) {
			return "jpeg";
		}
		if (format.equals("png")) {
			return "png";
		}
		throw unsupportedImage();
	}

	private static BufferedImage resize(final BufferedImage source) {
		final int longestSide = Math.max(source.getWidth(), source.getHeight());
		if (longestSide <= MAX_LONGEST_SIDE) {
			return source;
		}
		final double scale = (double) MAX_LONGEST_SIDE / longestSide;
		final int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		final int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		final BufferedImage resized = new BufferedImage(width, height,
				source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = resized.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return resized;
	}

	private static BufferedImage orient(final BufferedImage source, final int orientation) {
		if (orientation <= 1 || orientation > 8) {
			return source;
		}
		final boolean swapsDimensions = orientation >= 5;
		final BufferedImage oriented = new BufferedImage(swapsDimensions ? source.getHeight() : source.getWidth(),
				swapsDimensions ? source.getWidth() : source.getHeight(),
				source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				final int[] target = orientedCoordinates(orientation, x, y, source.getWidth(), source.getHeight());
				oriented.setRGB(target[0], target[1], source.getRGB(x, y));
			}
		}
		return oriented;
	}

	private static int[] orientedCoordinates(final int orientation, final int x, final int y, final int width,
			final int height) {
		return switch (orientation) {
			case 2 -> new int[] {
					width - 1 - x, y
			};
			case 3 -> new int[] {
					width - 1 - x, height - 1 - y
			};
			case 4 -> new int[] {
					x, height - 1 - y
			};
			case 5 -> new int[] {
					y, x
			};
			case 6 -> new int[] {
					height - 1 - y, x
			};
			case 7 -> new int[] {
					height - 1 - y, width - 1 - x
			};
			case 8 -> new int[] {
					y, width - 1 - x
			};
			default -> new int[] {
					x, y
			};
		};
	}

	private static byte[] write(final BufferedImage image, final String format) throws IOException {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		if ("png".equals(format)) {
			if (!ImageIO.write(image, "png", output)) {
				throw unsupportedImage();
			}
			return output.toByteArray();
		}
		final BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = rgb.createGraphics();
		try {
			graphics.setColor(java.awt.Color.WHITE);
			graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
			graphics.drawImage(image, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
			writer.setOutput(imageOutput);
			final ImageWriteParam parameters = writer.getDefaultWriteParam();
			parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parameters.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(rgb, null, null), parameters);
		} finally {
			writer.dispose();
		}
		return output.toByteArray();
	}

	private static String sha256(final byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (final NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static int jpegOrientation(final byte[] data) {
		if (data.length < 4 || unsigned(data[0]) != 0xff || unsigned(data[1]) != 0xd8) {
			return 1;
		}
		int offset = 2;
		while (offset + 4 <= data.length && unsigned(data[offset]) == 0xff) {
			final int marker = unsigned(data[offset + 1]);
			if (marker == 0xd9 || marker == 0xda) {
				break;
			}
			final int length = (unsigned(data[offset + 2]) << 8) | unsigned(data[offset + 3]);
			if (length < 2 || offset + 2 + length > data.length) {
				break;
			}
			if (marker == 0xe1 && length >= 14 && matchesExifHeader(data, offset + 4)) {
				return tiffOrientation(data, offset + 10, length - 8);
			}
			offset += 2 + length;
		}
		return 1;
	}

	private static boolean matchesExifHeader(final byte[] data, final int offset) {
		return offset + 6 <= data.length && data[offset] == 'E' && data[offset + 1] == 'x' && data[offset + 2] == 'i'
				&& data[offset + 3] == 'f' && data[offset + 4] == 0 && data[offset + 5] == 0;
	}

	private static int tiffOrientation(final byte[] data, final int start, final int available) {
		if (available < 8 || start + available > data.length) {
			return 1;
		}
		final ByteOrder order;
		if (data[start] == 'I' && data[start + 1] == 'I') {
			order = ByteOrder.LITTLE_ENDIAN;
		} else if (data[start] == 'M' && data[start + 1] == 'M') {
			order = ByteOrder.BIG_ENDIAN;
		} else {
			return 1;
		}
		final ByteBuffer buffer = ByteBuffer.wrap(data, start, available).slice().order(order);
		if (Short.toUnsignedInt(buffer.getShort(2)) != 42) {
			return 1;
		}
		final long ifdOffset = Integer.toUnsignedLong(buffer.getInt(4));
		if (ifdOffset + 2 > available) {
			return 1;
		}
		final int count = Short.toUnsignedInt(buffer.getShort((int) ifdOffset));
		for (int index = 0; index < count; index++) {
			final int entry = (int) ifdOffset + 2 + index * 12;
			if (entry + 12 > available) {
				return 1;
			}
			if (Short.toUnsignedInt(buffer.getShort(entry)) == 0x0112
					&& Short.toUnsignedInt(buffer.getShort(entry + 2)) == 3 && buffer.getInt(entry + 4) == 1) {
				return Short.toUnsignedInt(buffer.getShort(entry + 8));
			}
		}
		return 1;
	}

	private static int unsigned(final byte value) {
		return Byte.toUnsignedInt(value);
	}

	private static IllegalArgumentException unsupportedImage() {
		return new IllegalArgumentException("Bitte wähle eine PNG- oder JPEG-Datei.");
	}
}
