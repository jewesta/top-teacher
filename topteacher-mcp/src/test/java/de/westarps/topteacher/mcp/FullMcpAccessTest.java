package de.westarps.topteacher.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.McpAccessLevel;

/** Direct tool tests explicitly authenticate, just as HTTP requests must. */
abstract class FullMcpAccessTest {

	private McpAccessContext.Scope access;

	@BeforeEach
	void authenticateFullMcpAccess() {
		access = McpAccessContext.open(McpAccessLevel.FULL);
	}

	@AfterEach
	void closeMcpAccess() {
		access.close();
	}
}
