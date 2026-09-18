package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;

class TopTeacherMcpSchemaTests {

	private static final List<Class<?>> TOOL_GROUPS = List.of(CourseMcpTools.class, ExamMcpTools.class,
			CourseRosterMcpTools.class, LevelOfExpectationsMcpTools.class, PupilResultMcpTools.class);

	@Test
	void exposesTheEightTopTeacherOperationsAcrossFiveFocusedToolGroups() {
		final List<String> tools = TOOL_GROUPS.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).map(McpTool::name).sorted()
				.toList();

		assertThat(tools).containsExactly("create_course_with_pupils", "create_level_of_expectations",
				"get_course_creation_options", "get_level_of_expectations", "get_pupil_result", "list_courses",
				"list_exam_pupils", "list_exams");
	}

	@Test
	void retrievalIsReadOnlyAndCreationIsNonDestructive() throws Exception {
		assertThat(tool(CourseMcpTools.class, "listCourses", Boolean.class).annotations().readOnlyHint()).isTrue();
		assertThat(tool(LevelOfExpectationsMcpTools.class, "getLevelOfExpectations", int.class).annotations()
				.readOnlyHint()).isTrue();
		final McpTool creation = tool(LevelOfExpectationsMcpTools.class, "createLevelOfExpectations", int.class,
				List.class, List.class);
		assertThat(creation.annotations().readOnlyHint()).isFalse();
		assertThat(creation.annotations().destructiveHint()).isFalse();
		assertThat(creation.annotations().openWorldHint()).isFalse();
		final McpTool courseCreation = Arrays.stream(CourseRosterMcpTools.class.getDeclaredMethods())
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).findFirst().orElseThrow();
		assertThat(courseCreation.annotations().readOnlyHint()).isFalse();
		assertThat(courseCreation.annotations().destructiveHint()).isFalse();
	}

	@Test
	void generatedSchemasHaveObjectRootsAndStableParameterNames() {
		final List<SyncToolSpecification> specifications = new SyncMcpToolProvider(toolGroups())
				.getToolSpecifications();

		assertThat(specifications).hasSize(8).allSatisfy(specification -> {
			assertThat(specification.tool().inputSchema()).containsEntry("type", "object");
			assertThat(specification.tool().outputSchema()).containsEntry("type", "object");
		});

		final Map<String, Object> createProperties = properties(specifications, "create_level_of_expectations");
		assertThat(createProperties).containsKeys("examId", "parts", "noteSections");
		assertThat(map(createProperties.get("parts"))).containsEntry("type", "array");

		final Map<String, Object> createCourseProperties = properties(specifications, "create_course_with_pupils");
		assertThat(createCourseProperties).containsKeys("course", "roster", "resolutions").doesNotContainKey("context");
		assertThat(map(createCourseProperties.get("roster"))).containsEntry("type", "array");
	}

	private static Map<String, Object> properties(final List<SyncToolSpecification> specifications,
			final String toolName) {
		final Map<String, Object> schema = specifications.stream()
				.filter(specification -> specification.tool().name().equals(toolName)).findFirst().orElseThrow().tool()
				.inputSchema();
		return map(schema.get("properties"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(final Object value) {
		assertThat(value).isInstanceOf(Map.class);
		return (Map<String, Object>) value;
	}

	private static List<Object> toolGroups() {
		final CourseRepository courses = mock(CourseRepository.class);
		final ExamRepository exams = mock(ExamRepository.class);
		final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
		final LevelOfExpectationsRepository levelOfExpectations = mock(LevelOfExpectationsRepository.class);
		final PupilRepository pupils = mock(PupilRepository.class);
		final SubjectRepository subjects = mock(SubjectRepository.class);
		final CourseRosterWriter writer = mock(CourseRosterWriter.class);
		final PupilRosterConflictResolver conflictResolver = mock(PupilRosterConflictResolver.class);
		return List.of(new CourseMcpTools(courses, subjects, gradingScales),
				new ExamMcpTools(courses, exams, levelOfExpectations),
				new CourseRosterMcpTools(courses, subjects, gradingScales, conflictResolver, writer),
				new LevelOfExpectationsMcpTools(courses, exams, gradingScales, levelOfExpectations),
				new PupilResultMcpTools(exams, gradingScales, levelOfExpectations, pupils));
	}

	private static McpTool tool(final Class<?> type, final String name, final Class<?>... parameters)
			throws NoSuchMethodException {
		final Method method = type.getMethod(name, parameters);
		return method.getAnnotation(McpTool.class);
	}
}
