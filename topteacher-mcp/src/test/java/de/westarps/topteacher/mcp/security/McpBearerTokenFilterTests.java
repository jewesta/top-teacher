package de.westarps.topteacher.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import de.westarps.topteacher.backend.mcp.McpAccessTokenService;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.McpAccessToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

class McpBearerTokenFilterTests {

	private static final String TOKEN = "mcp-token-with-at-least-thirty-two-characters";
	private final McpAccessTokenService tokens = mock(McpAccessTokenService.class);
	private final McpBearerTokenFilter filter = new McpBearerTokenFilter(tokens);

	@BeforeEach
	void configureToken() {
		when(tokens.authenticate(TOKEN)).thenReturn(Optional.of(token("token-id", McpAccessLevel.PSEUDONYMIZED)));
	}

	@Test
	void setsTheDatabasePermissionsOnlyForTheAuthorizedExchange() throws Exception {
		final MockHttpServletRequest request = requestWithAuthorization("Bearer " + TOKEN);
		final MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (incoming, outgoing) -> {
			assertThat(McpAccessContext.accessLevel()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
			((HttpServletResponse) outgoing).setHeader("Cache-Control", "no-cache");
		});

		assertThatThrownBy(McpAccessContext::accessLevel).isInstanceOf(SecurityException.class);
		assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
	}

	@Test
	void rejectsMissingOrInvalidCredentialsWithoutCallingTheEndpoint() throws Exception {
		for (final String authorization : new String[] { null, "Basic " + TOKEN, "Bearer wrong-token" }) {
			final MockHttpServletRequest request = requestWithAuthorization(authorization);
			final MockHttpServletResponse response = new MockHttpServletResponse();
			final FilterChain chain = mock(FilterChain.class);

			filter.doFilter(request, response, chain);

			assertThat(response.getStatus()).isEqualTo(401);
			assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer realm=\"topteacher-mcp\"");
			assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
			assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"error\":\"unauthorized\"}");
			verify(chain, never()).doFilter(request, response);
		}
	}

	@Test
	void clearsPermissionsEvenWhenAToolFails() {
		assertThatThrownBy(() -> filter.doFilter(requestWithAuthorization("Bearer " + TOKEN),
				new MockHttpServletResponse(), (request, response) -> { throw new ServletException("failed"); }))
				.isInstanceOf(ServletException.class);
		assertThatThrownBy(McpAccessContext::accessLevel).isInstanceOf(SecurityException.class);
	}

	@Test
	void bindsSessionsToTokenAndPermissionLevelIncludingReplayAndDeleteRequests() throws Exception {
		filter.doFilter(requestWithAuthorization("Bearer " + TOKEN), new MockHttpServletResponse(),
				(request, response) -> ((HttpServletResponse) response).setHeader("Mcp-Session-Id", "session-1"));

		final MockHttpServletRequest sameToken = requestWithAuthorization("Bearer " + TOKEN);
		sameToken.addHeader("Mcp-Session-Id", "session-1");
		final FilterChain allowed = mock(FilterChain.class);
		filter.doFilter(sameToken, new MockHttpServletResponse(), allowed);
		verify(allowed).doFilter(org.mockito.ArgumentMatchers.eq(sameToken), org.mockito.ArgumentMatchers.any());

		when(tokens.authenticate("other-token")).thenReturn(Optional.of(token("other-id", McpAccessLevel.FULL)));
		final MockHttpServletRequest otherToken = requestWithAuthorization("Bearer other-token");
		otherToken.setMethod("GET");
		otherToken.addHeader("Mcp-Session-Id", "session-1");
		otherToken.addHeader("Last-Event-ID", "old-event");
		final MockHttpServletResponse denied = new MockHttpServletResponse();
		final FilterChain endpoint = mock(FilterChain.class);
		filter.doFilter(otherToken, denied, endpoint);
		assertThat(denied.getStatus()).isEqualTo(404);
		verify(endpoint, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

		when(tokens.authenticate(TOKEN)).thenReturn(Optional.of(token("token-id", McpAccessLevel.FULL)));
		final MockHttpServletResponse changedLevel = new MockHttpServletResponse();
		filter.doFilter(sameToken, changedLevel, endpoint);
		assertThat(changedLevel.getStatus()).isEqualTo(404);

		when(tokens.authenticate(TOKEN)).thenReturn(Optional.of(token("token-id", McpAccessLevel.PSEUDONYMIZED)));
		sameToken.setMethod("DELETE");
		filter.doFilter(sameToken, new MockHttpServletResponse(), (request, response) -> { });
		final MockHttpServletResponse deleted = new MockHttpServletResponse();
		filter.doFilter(sameToken, deleted, endpoint);
		assertThat(deleted.getStatus()).isEqualTo(404);
	}

	private static McpAccessToken token(final String id, final McpAccessLevel level) {
		return new McpAccessToken(id, "Test client", level, Instant.now(), null, null, null);
	}

	private static MockHttpServletRequest requestWithAuthorization(final String authorization) {
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
		if (authorization != null) {
			request.addHeader("Authorization", authorization);
		}
		return request;
	}
}
