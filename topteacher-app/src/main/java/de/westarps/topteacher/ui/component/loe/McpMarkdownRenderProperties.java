package de.westarps.topteacher.ui.component.loe;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tt.mcp.render")
public record McpMarkdownRenderProperties(@DefaultValue("") String browserExecutable,
		@DefaultValue("chrome") String browserChannel, @DefaultValue("30") int timeoutSeconds) {

	public McpMarkdownRenderProperties {
		browserExecutable = browserExecutable == null ? "" : browserExecutable.trim();
		browserChannel = browserChannel == null || browserChannel.isBlank() ? "chrome" : browserChannel.trim();
		if (!browserExecutable.isEmpty() && !Path.of(browserExecutable).isAbsolute()) {
			throw new IllegalArgumentException("tt.mcp.render.browser-executable must be an absolute path.");
		}
		if (!browserChannel.matches("chrome|chrome-beta|chrome-dev|chrome-canary|msedge|msedge-beta|msedge-dev|msedge-canary|chromium")) {
			throw new IllegalArgumentException("tt.mcp.render.browser-channel must identify a supported Chromium channel.");
		}
		if (timeoutSeconds < 5 || timeoutSeconds > 60) {
			throw new IllegalArgumentException("tt.mcp.render.timeout-seconds must be between 5 and 60.");
		}
	}
}
