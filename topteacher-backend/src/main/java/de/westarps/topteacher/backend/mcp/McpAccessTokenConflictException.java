package de.westarps.topteacher.backend.mcp;

/** A token's persisted configuration changed after its editor was loaded. */
public final class McpAccessTokenConflictException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public McpAccessTokenConflictException() {
		super("The MCP token changed or was revoked. Reload its current settings before saving.");
	}
}
