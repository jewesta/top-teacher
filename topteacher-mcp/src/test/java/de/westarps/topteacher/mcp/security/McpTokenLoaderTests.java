package de.westarps.topteacher.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpTokenLoaderTests {

	private static final String TOKEN = "mcp-token-with-at-least-thirty-two-characters";

	@TempDir
	Path temporaryDirectory;

	@Test
	void readsTheCredentialAndRemovesItsFinalTerminator() throws IOException {
		final Path credential = credential("token", TOKEN + "\n");

		assertThat(new McpTokenLoader(configuration(credential)).load()).isEqualTo(TOKEN);
	}

	@Test
	void rejectsWeakOrMalformedCredentialsWithoutExposingThem() throws IOException {
		for (final String invalid : new String[] {
				"too-short", " " + TOKEN, TOKEN + " ", TOKEN + "\nsecond-line\n"
		}) {
			final Path credential = credential("invalid", invalid);

			assertThatThrownBy(() -> new McpTokenLoader(configuration(credential)).load())
					.isInstanceOf(IllegalStateException.class).hasMessageNotContaining(invalid);
		}
	}

	@Test
	void requiresAnExplicitCredentialFile() {
		assertThatThrownBy(() -> new McpTokenLoader(new McpEndpointSecurityProperties("")).load())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("tt.mcp.token-file must be configured");
	}

	private Path credential(final String name, final String value) throws IOException {
		final Path credential = temporaryDirectory.resolve(name);
		Files.writeString(credential, value, StandardCharsets.UTF_8);
		return credential;
	}

	private static McpEndpointSecurityProperties configuration(final Path path) {
		return new McpEndpointSecurityProperties(path.toString());
	}
}
