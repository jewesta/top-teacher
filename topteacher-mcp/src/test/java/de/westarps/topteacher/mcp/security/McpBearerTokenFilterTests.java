package de.westarps.topteacher.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class McpBearerTokenFilterTests {

	private static final String TOKEN = "mcp-token-with-at-least-thirty-two-characters";

	private final McpBearerTokenFilter filter = new McpBearerTokenFilter(TOKEN);

	@Test
	void acceptsTheConfiguredBearerToken() throws Exception {
		final MockHttpServletRequest request = requestWithAuthorization("Bearer " + TOKEN);
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	@Test
	void rejectsMissingOrInvalidCredentialsWithoutCallingTheEndpoint() throws Exception {
		for (final String authorization : new String[] {
				null, "Basic " + TOKEN, "Bearer wrong-token"
		}) {
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

	private static MockHttpServletRequest requestWithAuthorization(final String authorization) {
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
		if (authorization != null) {
			request.addHeader("Authorization", authorization);
		}
		return request;
	}
}
