package de.westarps.topteacher.mcp.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loads and validates the MCP bearer token without accepting it as a direct
 * property value.
 */
final class McpTokenLoader {

	private static final int MAXIMUM_TOKEN_BYTES = 16_384;
	private static final int MINIMUM_TOKEN_LENGTH = 32;

	private final McpEndpointSecurityProperties configuration;

	McpTokenLoader(final McpEndpointSecurityProperties configuration) {
		this.configuration = Objects.requireNonNull(configuration, "configuration");
	}

	String load() {
		final Path path = configuration.tokenFilePath().orElseThrow(
				() -> new IllegalStateException("tt.mcp.token-file must be configured when MCP is enabled."));
		final byte[] encoded;
		try (InputStream input = java.nio.file.Files.newInputStream(path)) {
			encoded = input.readNBytes(MAXIMUM_TOKEN_BYTES + 1);
		} catch (final IOException failure) {
			throw new IllegalStateException("Cannot read the configured MCP token credential.", failure);
		}
		if (encoded.length > MAXIMUM_TOKEN_BYTES) {
			throw new IllegalStateException("The configured MCP token credential is too large.");
		}

		final String token = stripTerminator(decode(encoded));
		if (token.isEmpty()) {
			throw new IllegalStateException("The configured MCP token credential is empty.");
		}
		if (token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
			throw new IllegalStateException("The configured MCP token credential must contain one line.");
		}
		if (!token.equals(token.strip())) {
			throw new IllegalStateException(
					"The configured MCP token credential must not contain surrounding whitespace.");
		}
		if (token.length() < MINIMUM_TOKEN_LENGTH) {
			throw new IllegalStateException("The configured MCP token credential must contain at least 32 characters.");
		}
		return token;
	}

	private static String decode(final byte[] encoded) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(encoded)).toString();
		} catch (final CharacterCodingException failure) {
			throw new IllegalStateException("The configured MCP token credential is not valid UTF-8.", failure);
		}
	}

	private static String stripTerminator(final String value) {
		if (value.endsWith("\r\n")) {
			return value.substring(0, value.length() - 2);
		}
		if (value.endsWith("\n") || value.endsWith("\r")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}
}
