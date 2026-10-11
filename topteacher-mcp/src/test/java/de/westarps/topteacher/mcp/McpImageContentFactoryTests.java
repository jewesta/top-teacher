package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.model.loe.LoeMaterialImage;

class McpImageContentFactoryTests {

	@Test
	void createsNativeBase64ImageContentFromStoredImage() throws Exception {
		final byte[] data = "image bytes".getBytes(StandardCharsets.UTF_8);
		final String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		final LoeMaterialImage image = new LoeMaterialImage("4n8k2p7c", hash, "image/png", 10, 20, data.length, data);

		final var content = McpImageContentFactory.from(image);

		assertThat(content.mimeType()).isEqualTo("image/png");
		assertThat(content.data()).isEqualTo("aW1hZ2UgYnl0ZXM=");
	}
}
