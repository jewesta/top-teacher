package de.westarps.topteacher.mcp.security;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Authentication configuration for the MCP HTTP endpoint. */
@ConfigurationProperties("tt.mcp")
public record McpEndpointSecurityProperties(@DefaultValue("") String tokenFile) {

	public McpEndpointSecurityProperties {
		tokenFile = Objects.requireNonNull(tokenFile, "tokenFile").trim();
	}

	Optional<Path> tokenFilePath() {
		return tokenFile.isEmpty() ? Optional.empty() : Optional.of(Path.of(tokenFile).toAbsolutePath().normalize());
	}
}
