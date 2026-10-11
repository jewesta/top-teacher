package de.westarps.topteacher.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import de.westarps.topteacher.backend.mcp.McpAccessTokenService;

class McpEndpointSecurityConfigurationTests {

	private static final String TOKEN = "mcp-token-with-at-least-thirty-two-characters";
	private final McpAccessTokenService tokens = mock(McpAccessTokenService.class);
	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withBean(McpAccessTokenService.class, () -> tokens)
			.withUserConfiguration(McpEndpointSecurityConfiguration.class);

	@TempDir
	Path temporaryDirectory;

	@Test
	void protectsOnlyTheMcpRouteAndImportsTheLegacyFile() throws IOException {
		final Path credential = temporaryDirectory.resolve("mcp-token");
		Files.writeString(credential, TOKEN, StandardCharsets.UTF_8);

		contextRunner.withPropertyValues("tt.mcp.enabled=true", "tt.mcp.token-file=" + credential).run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(McpEndpointSecurityProperties.class)
					.hasSingleBean(FilterRegistrationBean.class);
			final FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
			assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder("/mcp", "/mcp/*");
			verify(tokens).importLegacyToken(TOKEN);
		});
	}

	@Test
	void startsWithoutAFileAndStillRequiresDatabaseCredentials() {
		contextRunner.withPropertyValues("tt.mcp.enabled=true").run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(FilterRegistrationBean.class);
			verify(tokens).importLegacyToken(null);
		});
	}

	@Test
	void doesNotReadAnObsoleteFileAfterMigration() {
		when(tokens.legacyImportComplete()).thenReturn(true);
		contextRunner.withPropertyValues("tt.mcp.enabled=true", "tt.mcp.token-file=/nonexistent/obsolete-file")
				.run(context -> assertThat(context).hasNotFailed().hasSingleBean(FilterRegistrationBean.class));
		verify(tokens, never()).importLegacyToken(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void registersNoSecurityBeansWhenMcpIsDisabled() {
		contextRunner.withPropertyValues("tt.mcp.enabled=false").run(context -> assertThat(context).hasNotFailed()
				.doesNotHaveBean(McpEndpointSecurityProperties.class).doesNotHaveBean(FilterRegistrationBean.class));
	}
}
