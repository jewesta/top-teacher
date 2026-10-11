package de.westarps.topteacher.mcp;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;

/** Converts stored exam images into native MCP image content. */
public final class McpImageContentFactory {

	private static final byte[] PNG_SIGNATURE = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };

	private McpImageContentFactory() {
	}

	public static ImageContent from(final LoeMaterialImage image) {
		Objects.requireNonNull(image, "image must not be null");
		return ImageContent.builder(Base64.getEncoder().encodeToString(image.data()), image.mediaType()).build();
	}

	public static ImageContent fromPng(final byte[] data) {
		Objects.requireNonNull(data, "PNG data must not be null");
		if (data.length < PNG_SIGNATURE.length
				|| !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(data, PNG_SIGNATURE.length))) {
			throw new IllegalArgumentException("Rendered image must contain PNG data.");
		}
		return ImageContent.builder(Base64.getEncoder().encodeToString(data), "image/png").build();
	}
}
