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

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer;
import de.westarps.topteacher.backend.render.Java2DFaithfulMarkdownRenderer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

@SpringBootTest(properties = "tt.mcp.enabled=true")
class TopTeacherMcpEnabledContextTests {

	private static final Path TOKEN_FILE = tokenFile();

	@Autowired
	private FilterRegistrationBean<?> mcpBearerTokenFilter;

	@Autowired
	private McpSyncServer mcpServer;

	@Autowired
	private FaithfulMarkdownRenderer renderer;

	@DynamicPropertySource
	static void mcpProperties(final DynamicPropertyRegistry registry) {
		registry.add("tt.mcp.token-file", TOKEN_FILE::toString);
	}

	@Test
	void registersAllToolsAndTheSecurityFilterInTheEnabledApplication() {
		final List<String> toolNames = mcpServer.listTools().stream().map(McpSchema.Tool::name).sorted().toList();

		assertThat(toolNames).containsExactly("apply_exam_content_changes", "apply_level_of_expectations_changes",
				"assign_pupils_to_course", "assign_pupils_to_exam", "create_course_with_pupils", "create_database_backup",
				"create_exam", "create_pupils", "duplicate_exam", "get_course_creation_options", "get_exam", "get_exam_authoring_options",
				"get_exam_footer", "get_exam_structure", "get_level_of_expectations", "get_markdown_guide", "get_material", "get_material_image", "get_mcp_context",
				"get_pupil_result", "get_task", "get_task_requirements", "import_material_image", "list_course_pupils", "list_courses", "list_exam_pupils",
				"list_exams", "list_pupils", "remove_pupils_from_course", "remove_pupils_from_exam", "save_pupil_result", "update_exam", "update_pupil");
		assertThat(mcpBearerTokenFilter.getUrlPatterns()).containsExactlyInAnyOrder("/mcp", "/mcp/*");
		assertThat(renderer).isInstanceOf(Java2DFaithfulMarkdownRenderer.class);
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
