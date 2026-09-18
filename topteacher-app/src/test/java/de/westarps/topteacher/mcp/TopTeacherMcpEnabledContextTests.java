package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

@SpringBootTest(properties = "tt.mcp.enabled=true")
class TopTeacherMcpEnabledContextTests {

	private static final Path TOKEN_FILE = tokenFile();

	@Autowired
	private FilterRegistrationBean<?> mcpBearerTokenFilter;

	@Autowired
	private McpSyncServer mcpServer;

	@DynamicPropertySource
	static void mcpProperties(final DynamicPropertyRegistry registry) {
		registry.add("tt.mcp.token-file", TOKEN_FILE::toString);
	}

	@Test
	void registersAllToolsAndTheSecurityFilterInTheEnabledApplication() {
		final List<String> toolNames = mcpServer.listTools().stream().map(McpSchema.Tool::name).sorted().toList();

		assertThat(toolNames).containsExactly("assign_pupils_to_course", "create_course_with_pupils",
				"create_level_of_expectations", "create_pupils", "get_course_creation_options",
				"get_level_of_expectations", "get_pupil_result", "list_course_pupils", "list_courses",
				"list_exam_pupils", "list_exams", "list_pupils", "remove_pupils_from_course", "update_pupil");
		assertThat(mcpBearerTokenFilter.getUrlPatterns()).containsExactlyInAnyOrder("/mcp", "/mcp/*");
	}

	private static Path tokenFile() {
		try {
			final Path path = Files.createTempFile("topteacher-mcp-token-", ".txt");
			Files.writeString(path, "mcp-token-with-at-least-thirty-two-characters", StandardCharsets.UTF_8);
			path.toFile().deleteOnExit();
			return path;
		} catch (final IOException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}
}
