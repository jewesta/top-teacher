package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"tt.mcp.enabled=true", "server.servlet.context-path=/top-teacher"
})
class TopTeacherMcpHttpTests {

	private static final String TOKEN = "mcp-token-with-at-least-thirty-two-characters";
	private static final String INITIALIZE = """
		{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"topteacher-test","version":"1"}}}
		""";
	private static final Path TOKEN_FILE = tokenFile();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void mcpProperties(final DynamicPropertyRegistry registry) {
		registry.add("tt.mcp.token-file", TOKEN_FILE::toString);
	}

	@Test
	void protectsTheEndpointAndCompletesAStreamableHttpHandshake() throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			final HttpResponse<String> unauthorized = client.send(request(INITIALIZE, null, null),
					HttpResponse.BodyHandlers.ofString());

			assertThat(unauthorized.statusCode()).isEqualTo(401);

			final HttpResponse<String> initialized = client.send(request(INITIALIZE, TOKEN, null),
					HttpResponse.BodyHandlers.ofString());
			final String sessionId = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();

			assertThat(initialized.statusCode()).isEqualTo(200);
			assertThat(initialized.body()).contains("\"protocolVersion\":\"2025-06-18\"");

			final HttpResponse<String> notification = client.send(
					request("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", TOKEN, sessionId),
					HttpResponse.BodyHandlers.ofString());

			assertThat(notification.statusCode()).isIn(200, 202);

			final HttpResponse<String> tools = client.send(
					request("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}", TOKEN, sessionId),
					HttpResponse.BodyHandlers.ofString());

			assertThat(tools.statusCode()).isEqualTo(200);
			assertThat(tools.body()).contains("create_level_of_expectations", "get_level_of_expectations",
					"get_pupil_result", "list_courses", "list_exam_pupils", "list_exams");
		}
	}

	private HttpRequest request(final String body, final String token, final String sessionId) {
		final HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + port + "/top-teacher/mcp"))
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (sessionId != null) {
			request.header("Mcp-Session-Id", sessionId);
		}
		return request.build();
	}

	private static Path tokenFile() {
		try {
			final Path path = Files.createTempFile("topteacher-mcp-http-token-", ".txt");
			Files.writeString(path, TOKEN, StandardCharsets.UTF_8);
			path.toFile().deleteOnExit();
			return path;
		} catch (final IOException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}
}
