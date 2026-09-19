package de.westarps.topteacher.mcp.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class McpBearerTokenFilter implements Filter {

	private static final String AUTHORIZATION = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
	private static final String CHALLENGE = "Bearer realm=\"topteacher-mcp\"";

	private final byte[] expectedToken;

	McpBearerTokenFilter(final String expectedToken) {
		this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
			throws IOException, ServletException {
		final HttpServletRequest httpRequest = (HttpServletRequest) request;
		final HttpServletResponse httpResponse = (HttpServletResponse) response;
		if (authorized(httpRequest.getHeader(AUTHORIZATION))) {
			chain.doFilter(request, response);
			return;
		}

		httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		httpResponse.setHeader(WWW_AUTHENTICATE, CHALLENGE);
		httpResponse.setHeader("Cache-Control", "no-store");
		httpResponse.setContentType("application/json");
		httpResponse.getWriter().write("{\"error\":\"unauthorized\"}");
	}

	private boolean authorized(final String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return false;
		}
		final byte[] suppliedToken = authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expectedToken, suppliedToken);
	}
}
