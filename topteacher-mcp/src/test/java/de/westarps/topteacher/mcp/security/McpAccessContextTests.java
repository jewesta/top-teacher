package de.westarps.topteacher.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.model.McpAccessLevel;

class McpAccessContextTests {

	@Test
	void failsClosedAndRestoresNestedScopes() {
		assertThatThrownBy(McpAccessContext::accessLevel).isInstanceOf(SecurityException.class);
		try (var full = McpAccessContext.open(McpAccessLevel.FULL)) {
			assertThat(McpAccessContext.accessLevel()).isEqualTo(McpAccessLevel.FULL);
			try (var restricted = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
				assertThat(McpAccessContext.accessLevel()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
			}
			assertThat(McpAccessContext.accessLevel()).isEqualTo(McpAccessLevel.FULL);
		}
		assertThatThrownBy(McpAccessContext::accessLevel).isInstanceOf(SecurityException.class);
	}

	@Test
	void doesNotInheritAnotherRequestsPermissionsAcrossThreads() throws Exception {
		try (var executor = Executors.newSingleThreadExecutor(); var full = McpAccessContext.open(McpAccessLevel.FULL)) {
			executor.submit(() -> {
				assertThatThrownBy(McpAccessContext::accessLevel).isInstanceOf(SecurityException.class);
				try (var restricted = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
					assertThat(McpAccessContext.accessLevel()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
				}
				assertThatThrownBy(McpAccessContext::accessLevel).isInstanceOf(SecurityException.class);
			}).get();
			assertThat(McpAccessContext.accessLevel()).isEqualTo(McpAccessLevel.FULL);
		}
	}
}
