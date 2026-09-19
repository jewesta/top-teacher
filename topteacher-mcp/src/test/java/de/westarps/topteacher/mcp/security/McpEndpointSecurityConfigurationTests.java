package de.westarps.topteacher.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class McpEndpointSecurityConfigurationTests {

	private static final String TOKEN = "mcp-token-with-at-least-thirty-two-characters";

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withUserConfiguration(McpEndpointSecurityConfiguration.class);

	@TempDir
	Path temporaryDirectory;

	@Test
	void protectsOnlyTheMcpRouteWhenEnabledWithATokenFile() throws IOException {
		final Path credential = temporaryDirectory.resolve("mcp-token");
		Files.writeString(credential, TOKEN, StandardCharsets.UTF_8);

		contextRunner.withPropertyValues("tt.mcp.enabled=true", "tt.mcp.token-file=" + credential).run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(McpEndpointSecurityProperties.class)
					.hasSingleBean(FilterRegistrationBean.class);
			final FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
			assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder("/mcp", "/mcp/*");
		});
	}

	@Test
	void failsClosedWhenEnabledWithoutATokenCredential() {
		contextRunner.withPropertyValues("tt.mcp.enabled=true").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
					.hasRootCauseMessage("tt.mcp.token-file must be configured when MCP is enabled.");
		});
	}

	@Test
	void registersNoSecurityBeansWhenMcpIsDisabled() {
		contextRunner.withPropertyValues("tt.mcp.enabled=false").run(context -> assertThat(context).hasNotFailed()
				.doesNotHaveBean(McpEndpointSecurityProperties.class).doesNotHaveBean(FilterRegistrationBean.class));
	}
}
