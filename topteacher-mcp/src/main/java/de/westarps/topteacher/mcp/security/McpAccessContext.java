package de.westarps.topteacher.mcp.security;

import java.util.Objects;

import de.westarps.topteacher.model.McpAccessLevel;

/**
 * Permission context for the synchronous servlet MCP exchange. Missing context
 * fails closed; scopes always restore the previous thread state.
 */
public final class McpAccessContext {

	private static final ThreadLocal<McpAccessLevel> CURRENT = new ThreadLocal<>();

	private McpAccessContext() {
	}

	public static McpAccessLevel accessLevel() {
		final McpAccessLevel level = CURRENT.get();
		if (level == null) {
			throw new SecurityException("An authenticated MCP access context is required.");
		}
		return level;
	}

	public static Scope open(final McpAccessLevel level) {
		final McpAccessLevel previous = CURRENT.get();
		CURRENT.set(Objects.requireNonNull(level, "level"));
		return new Scope(previous);
	}

	public static final class Scope implements AutoCloseable {

		private final McpAccessLevel previous;
		private boolean closed;

		private Scope(final McpAccessLevel previous) {
			this.previous = previous;
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				if (previous == null) {
					CURRENT.remove();
				} else {
					CURRENT.set(previous);
				}
			}
		}
	}
}
