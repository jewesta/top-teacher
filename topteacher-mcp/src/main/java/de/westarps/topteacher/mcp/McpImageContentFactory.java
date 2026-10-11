package de.westarps.topteacher.mcp;

import java.util.Base64;
import java.util.Objects;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;

/** Converts stored exam images into native MCP image content. */
public final class McpImageContentFactory {

	private McpImageContentFactory() {
	}

	public static ImageContent from(final LoeMaterialImage image) {
		Objects.requireNonNull(image, "image must not be null");
		return ImageContent.builder(Base64.getEncoder().encodeToString(image.data()), image.mediaType()).build();
	}
}
