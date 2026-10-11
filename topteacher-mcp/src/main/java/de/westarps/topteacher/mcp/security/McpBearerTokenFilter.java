package de.westarps.topteacher.mcp.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.westarps.topteacher.backend.mcp.McpAccessTokenService;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.McpAccessToken;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

final class McpBearerTokenFilter implements Filter {

	private static final String AUTHORIZATION = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String SESSION_ID = "Mcp-Session-Id";
	private static final int MAXIMUM_SESSION_BINDINGS = 4096;

	private final McpAccessTokenService tokens;
	private final Map<String, SessionBinding> sessions = new LinkedHashMap<>(16, 0.75f, true);

	McpBearerTokenFilter(final McpAccessTokenService tokens) {
		this.tokens = Objects.requireNonNull(tokens, "tokens");
	}

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
			throws IOException, ServletException {
		final HttpServletRequest httpRequest = (HttpServletRequest) request;
		final HttpServletResponse httpResponse = (HttpServletResponse) response;
		httpResponse.setHeader("Cache-Control", "no-store");
		final Optional<McpAccessToken> authenticated = authenticate(httpRequest.getHeader(AUTHORIZATION));
		if (authenticated.isEmpty()) {
			httpResponse.setHeader("WWW-Authenticate", "Bearer realm=\"topteacher-mcp\"");
			reject(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
			return;
		}

		final McpAccessToken token = authenticated.orElseThrow();
		final SessionBinding binding = new SessionBinding(token.id(), token.accessLevel());
		final String sessionId = httpRequest.getHeader(SESSION_ID);
		if (sessionId != null && !ownsSession(sessionId, binding)) {
			reject(httpResponse, HttpServletResponse.SC_NOT_FOUND, "session_not_found");
			return;
		}

		/*
		 * Bind before the initialization response is flushed. A session cannot be
		 * reused by another token or replay full-name output after a downgrade.
		 */
		final HttpServletResponseWrapper guardedResponse = new HttpServletResponseWrapper(httpResponse) {
			@Override
			public void setHeader(final String name, final String value) {
				if (SESSION_ID.equalsIgnoreCase(name) && value != null) {
					bindSession(value, binding);
				}
				super.setHeader(name, "Cache-Control".equalsIgnoreCase(name) ? "no-store" : value);
			}

			@Override
			public void addHeader(final String name, final String value) {
				if (SESSION_ID.equalsIgnoreCase(name) && value != null) {
					bindSession(value, binding);
				}
				if ("Cache-Control".equalsIgnoreCase(name)) {
					super.setHeader(name, "no-store");
				} else {
					super.addHeader(name, value);
				}
			}
		};
		try (McpAccessContext.Scope ignored = McpAccessContext.open(token.accessLevel())) {
			chain.doFilter(request, guardedResponse);
		} finally {
			if (sessionId != null && "DELETE".equals(httpRequest.getMethod()) && httpResponse.getStatus() < 400) {
				synchronized (sessions) {
					sessions.remove(sessionId);
				}
			}
		}
	}

	private Optional<McpAccessToken> authenticate(final String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return Optional.empty();
		}
		return tokens.authenticate(authorization.substring(BEARER_PREFIX.length()));
	}

	private boolean ownsSession(final String sessionId, final SessionBinding binding) {
		synchronized (sessions) {
			return binding.equals(sessions.get(sessionId));
		}
	}

	private void bindSession(final String sessionId, final SessionBinding binding) {
		synchronized (sessions) {
			sessions.put(sessionId, binding);
			if (sessions.size() > MAXIMUM_SESSION_BINDINGS) {
				sessions.remove(sessions.keySet().iterator().next());
			}
		}
	}

	private static void reject(final HttpServletResponse response, final int status, final String error)
			throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"" + error + "\"}");
	}

	private record SessionBinding(String tokenId, McpAccessLevel accessLevel) {
	}
}
