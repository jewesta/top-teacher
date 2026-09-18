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
			CourseRosterMcpTools.class, CoursePupilAssignmentMcpTools.class, CoursePupilRemovalMcpTools.class,
			LevelOfExpectationsMcpTools.class, PupilMcpTools.class, PupilResultMcpTools.class);

	@Test
	void exposesTheFourteenTopTeacherOperationsAcrossEightFocusedToolGroups() {
		final List<String> tools = TOOL_GROUPS.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).map(McpTool::name).sorted()
				.toList();

		assertThat(tools).containsExactly("assign_pupils_to_course", "create_course_with_pupils",
				"create_level_of_expectations", "create_pupils", "get_course_creation_options",
				"get_level_of_expectations", "get_pupil_result", "list_course_pupils", "list_courses",
				"list_exam_pupils", "list_exams", "list_pupils", "remove_pupils_from_course", "update_pupil");
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
		final McpTool coursePupilRemoval = Arrays.stream(CoursePupilRemovalMcpTools.class.getDeclaredMethods())
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).findFirst().orElseThrow();
		assertThat(coursePupilRemoval.annotations().readOnlyHint()).isFalse();
		assertThat(coursePupilRemoval.annotations().destructiveHint()).isTrue();
		assertThat(coursePupilRemoval.annotations().idempotentHint()).isTrue();
	}

	@Test
	void generatedSchemasHaveObjectRootsAndStableParameterNames() {
		final List<SyncToolSpecification> specifications = new SyncMcpToolProvider(toolGroups())
				.getToolSpecifications();

		assertThat(specifications).hasSize(14).allSatisfy(specification -> {
			assertThat(specification.tool().inputSchema()).containsEntry("type", "object");
			assertThat(specification.tool().outputSchema()).containsEntry("type", "object");
		});

		final Map<String, Object> createProperties = properties(specifications, "create_level_of_expectations");
		assertThat(createProperties).containsKeys("examId", "parts", "noteSections");
		assertThat(map(createProperties.get("parts"))).containsEntry("type", "array");

		final Map<String, Object> createCourseProperties = properties(specifications, "create_course_with_pupils");
		assertThat(createCourseProperties).containsKeys("course", "roster", "resolutions").doesNotContainKey("context");
		assertThat(map(createCourseProperties.get("roster"))).containsEntry("type", "array");

		final Map<String, Object> assignPupilsProperties = properties(specifications, "assign_pupils_to_course");
		assertThat(assignPupilsProperties).containsKeys("courseId", "pupilDrafts", "resolutions")
				.doesNotContainKey("context");
		assertThat(map(assignPupilsProperties.get("pupilDrafts"))).containsEntry("type", "array");

		final Map<String, Object> removePupilsProperties = properties(specifications, "remove_pupils_from_course");
		assertThat(removePupilsProperties).containsKeys("courseId", "pupilIds", "lockedPupilAction")
				.doesNotContainKey("context");
		assertThat(map(removePupilsProperties.get("pupilIds"))).containsEntry("type", "array");

		final Map<String, Object> listCoursePupilsProperties = properties(specifications, "list_course_pupils");
		assertThat(listCoursePupilsProperties).containsKeys("courseId", "includeArchived");

		final Map<String, Object> listExamsOutputProperties = outputProperties(specifications, "list_exams");
		final Map<String, Object> examItems = map(map(listExamsOutputProperties.get("exams")).get("items"));
		assertThat(list(examItems.get("required"))).doesNotContain("originalExamId", "gradingScaleId");

		final Map<String, Object> updatePupilProperties = properties(specifications, "update_pupil");
		assertThat(updatePupilProperties).containsKeys("pupilId", "name", "surname", "duplicateNameAction")
				.doesNotContainKeys("context", "lifecycle");
	}

	private static Map<String, Object> properties(final List<SyncToolSpecification> specifications,
			final String toolName) {
		final Map<String, Object> schema = specifications.stream()
				.filter(specification -> specification.tool().name().equals(toolName)).findFirst().orElseThrow().tool()
				.inputSchema();
		return map(schema.get("properties"));
	}

	private static Map<String, Object> outputProperties(final List<SyncToolSpecification> specifications,
			final String toolName) {
		final Map<String, Object> schema = specifications.stream()
				.filter(specification -> specification.tool().name().equals(toolName)).findFirst().orElseThrow().tool()
				.outputSchema();
		return map(schema.get("properties"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(final Object value) {
		assertThat(value).isInstanceOf(Map.class);
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<String> list(final Object value) {
		assertThat(value).isInstanceOf(List.class);
		return (List<String>) value;
	}

	private static List<Object> toolGroups() {
		final CourseRepository courses = mock(CourseRepository.class);
		final ExamRepository exams = mock(ExamRepository.class);
		final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
		final LevelOfExpectationsRepository levelOfExpectations = mock(LevelOfExpectationsRepository.class);
		final PupilRepository pupils = mock(PupilRepository.class);
		final SubjectRepository subjects = mock(SubjectRepository.class);
		final CourseRosterWriter writer = mock(CourseRosterWriter.class);
		final PupilWriter pupilWriter = mock(PupilWriter.class);
		final CoursePupilAssignmentWriter assignmentWriter = mock(CoursePupilAssignmentWriter.class);
		final CoursePupilRemovalWriter removalWriter = mock(CoursePupilRemovalWriter.class);
		final PupilRosterConflictResolver conflictResolver = mock(PupilRosterConflictResolver.class);
		final PupilUpdateConflictResolver updateConflictResolver = mock(PupilUpdateConflictResolver.class);
		return List.of(new CourseMcpTools(courses, subjects, gradingScales),
				new ExamMcpTools(courses, exams, levelOfExpectations),
				new CourseRosterMcpTools(courses, subjects, gradingScales, conflictResolver, writer),
				new CoursePupilAssignmentMcpTools(courses, conflictResolver, assignmentWriter),
				new CoursePupilRemovalMcpTools(courses, removalWriter),
				new LevelOfExpectationsMcpTools(courses, exams, gradingScales, levelOfExpectations),
				new PupilMcpTools(pupils, conflictResolver, updateConflictResolver, pupilWriter),
				new PupilResultMcpTools(exams, gradingScales, levelOfExpectations, pupils));
	}

	private static McpTool tool(final Class<?> type, final String name, final Class<?>... parameters)
			throws NoSuchMethodException {
		final Method method = type.getMethod(name, parameters);
		return method.getAnnotation(McpTool.class);
	}
}
