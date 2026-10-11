package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

import de.westarps.topteacher.backend.backup.DatabaseBackupService;
import de.westarps.topteacher.backend.authoring.ExamAuthoringService;
import de.westarps.topteacher.backend.authoring.ExamAssessmentService;
import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.authoring.ExamCreationExecutor;
import de.westarps.topteacher.backend.authoring.ExamMutationExecutor;
import de.westarps.topteacher.backend.file.FileAccessSettingsService;
import de.westarps.topteacher.backend.file.MaterialImageImportService;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;

class TopTeacherMcpSchemaTests extends FullMcpAccessTest {

	private static final List<Class<?>> TOOL_GROUPS = List.of(CourseMcpTools.class, ExamMcpTools.class,
			ExamWriteMcpTools.class, ExamPupilAssignmentMcpTools.class, ExamPupilRemovalMcpTools.class,
			CourseRosterMcpTools.class, CoursePupilAssignmentMcpTools.class, CoursePupilRemovalMcpTools.class,
			DatabaseBackupMcpTools.class, LevelOfExpectationsMcpTools.class, PupilMcpTools.class,
			PupilResultMcpTools.class, PupilResultWriteMcpTools.class, ExamDesignMcpTools.class, ExamContentReadMcpTools.class,
			McpContextTools.class, MaterialImageMcpTools.class);

	@Test
	void exposesExamFirstOperationsInsteadOfTheObsoleteCombinedEhCreator() {
		final List<String> tools = TOOL_GROUPS.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).map(McpTool::name).sorted()
				.toList();

		assertThat(tools).containsExactly("apply_exam_content_changes", "apply_level_of_expectations_changes",
				"assign_pupils_to_course", "assign_pupils_to_exam", "create_course_with_pupils", "create_database_backup",
				"create_exam", "create_pupils", "duplicate_exam", "get_course_creation_options", "get_exam", "get_exam_authoring_options",
				"get_exam_footer", "get_exam_structure", "get_level_of_expectations", "get_markdown_guide", "get_material", "get_material_image", "get_mcp_context",
				"get_pupil_result", "get_task", "get_task_requirements", "import_material_image", "list_course_pupils", "list_courses", "list_exam_pupils",
				"list_exams", "list_pupils", "remove_pupils_from_course", "remove_pupils_from_exam", "save_pupil_result", "update_exam", "update_pupil");
	}

	@Test
	void retrievalIsReadOnlyAndCreationIsNonDestructive() throws Exception {
		assertThat(tool(CourseMcpTools.class, "listCourses", Boolean.class).annotations().readOnlyHint()).isTrue();
		assertThat(tool(LevelOfExpectationsMcpTools.class, "getLevelOfExpectations", int.class).annotations()
				.readOnlyHint()).isTrue();
		final McpTool creation = tool(LevelOfExpectationsMcpTools.class, "applyLevelOfExpectationsChanges", int.class,
				long.class, String.class, List.class);
		assertThat(creation.annotations().readOnlyHint()).isFalse();
		assertThat(creation.annotations().destructiveHint()).isTrue();
		assertThat(creation.annotations().idempotentHint()).isTrue();
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
		final McpTool databaseBackup = Arrays.stream(DatabaseBackupMcpTools.class.getDeclaredMethods())
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).findFirst().orElseThrow();
		assertThat(databaseBackup.annotations().readOnlyHint()).isFalse();
		assertThat(databaseBackup.annotations().destructiveHint()).isFalse();
		assertThat(databaseBackup.annotations().idempotentHint()).isFalse();
		final McpTool examCreation = tool(ExamWriteMcpTools.class, "createExam", int.class, String.class, String.class,
				Integer.class, Integer.class, List.class, String.class);
		assertThat(examCreation.annotations().destructiveHint()).isFalse();
		assertThat(examCreation.annotations().idempotentHint()).isTrue();
		final McpTool examUpdate = tool(ExamWriteMcpTools.class, "updateExam", int.class, String.class, String.class, long.class, String.class);
		assertThat(examUpdate.annotations().destructiveHint()).isTrue();
		assertThat(examUpdate.annotations().idempotentHint()).isTrue();
		final McpTool examPupilAssignment = Arrays.stream(ExamPupilAssignmentMcpTools.class.getDeclaredMethods())
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).findFirst().orElseThrow();
		assertThat(examPupilAssignment.annotations().destructiveHint()).isFalse();
		assertThat(examPupilAssignment.annotations().idempotentHint()).isTrue();
		final McpTool examPupilRemoval = Arrays.stream(ExamPupilRemovalMcpTools.class.getDeclaredMethods())
				.map(method -> method.getAnnotation(McpTool.class)).filter(Objects::nonNull).findFirst().orElseThrow();
		assertThat(examPupilRemoval.annotations().destructiveHint()).isTrue();
		assertThat(examPupilRemoval.annotations().idempotentHint()).isTrue();
	}

	@Test
	void generatedSchemasHaveObjectRootsAndStableParameterNames() {
		final List<SyncToolSpecification> specifications = ExamContentReadMcpSchemaCustomizer.customize(
				new SyncMcpToolProvider(toolGroups()).getToolSpecifications());

		assertThat(specifications).hasSize(33).allSatisfy(specification -> {
			assertThat(specification.tool().inputSchema()).containsEntry("type", "object");
			if (!specification.tool().name().equals("get_material_image")) {
				assertThat(specification.tool().outputSchema()).containsEntry("type", "object");
			}
		});

		for (final String focusedRead : List.of("get_task", "get_material", "get_exam_footer", "get_task_requirements")) {
			assertThat(properties(specifications, focusedRead)).containsKey("includeFaithfulRender");
			assertThat(map(properties(specifications, focusedRead).get("includeFaithfulRender"))).containsEntry("type", "boolean");
			assertThat(list(schema(specifications, focusedRead, true).get("required"))).doesNotContain("includeFaithfulRender");
			assertThat(outputProperties(specifications, focusedRead)).containsKeys("examId", "revision", "permissions", "faithfulRender");
			assertThat(list(schema(specifications, focusedRead, false).get("required"))).doesNotContain("faithfulRender");
		}
		assertThat(outputProperties(specifications, "get_task")).containsKey("task");
		assertThat(outputProperties(specifications, "get_material")).containsKey("material");
		assertThat(outputProperties(specifications, "get_exam_footer")).containsKey("footerMarkdown");
		assertThat(outputProperties(specifications, "get_task_requirements")).containsKeys("task", "requirements");
		assertThat(properties(specifications, "get_exam_structure")).doesNotContainKey("includeFaithfulRender");
		final Map<String, Object> imageImportProperties = properties(specifications, "import_material_image");
		assertThat(imageImportProperties).containsKeys("widthPercent", "alignment");
		assertThat(list(map(imageImportProperties.get("alignment")).get("enum")))
				.containsExactly("LEFT", "CENTER", "RIGHT");
		assertThat(list(schema(specifications, "import_material_image", true).get("required")))
				.doesNotContain("widthPercent", "alignment");

		final Map<String, Object> createProperties = properties(specifications, "apply_exam_content_changes");
		assertThat(createProperties).containsKeys("examId", "expectedRevision", "requestId", "commands", "requirementChanges")
				.doesNotContainKeys("parts", "noteSections", "accessLevel");
		assertThat(map(createProperties.get("commands"))).containsEntry("type", "array");
		assertThat(containsObjectProperties(schema(specifications, "apply_exam_content_changes", true),
				Set.of("type", "id", "clientKey", "parentId", "parentKey", "title", "markdown", "maxPoints", "order"))).isTrue();
		assertThat(outputProperties(specifications, "get_exam")).containsKeys("revision", "footerMarkdown", "parts", "permissions", "diagnostics")
				.doesNotContainKeys("pupils", "requirements", "results");
		final Map<String, Object> levelOutputSchema = schema(specifications, "get_level_of_expectations", false);
		final Map<String, Object> pointSummary = map(outputProperties(specifications, "get_level_of_expectations").get("points"));
		assertThat(list(pointSummary.get("required"))).doesNotContain("gradingScaleMaximum");
		assertThat(outputProperties(specifications, "get_level_of_expectations")).doesNotContainKeys("noteSections", "parts", "materials", "footerMarkdown");
		assertThat(containsObjectProperties(levelOutputSchema,
				Set.of("taskId", "partId", "categoryId", "title", "maxPoints", "requirements"))).isTrue();
		assertThat(containsObjectProperties(levelOutputSchema,
				Set.of("regularMaximumMatchesGradingScale", "requirementsMatchTasks", "validDesign"))).isTrue();
		final Map<String, Object> pupilResultSchema = schema(specifications, "get_pupil_result", false);
		final Map<String, Object> pupilItems = map(map(outputProperties(specifications, "list_pupils").get("pupils")).get("items"));
		assertThat(map(pupilItems.get("properties"))).containsKeys("id", "displayName", "identityRepresentation", "name", "surname");
		assertThat(list(pupilItems.get("required"))).contains("id", "displayName", "identityRepresentation")
				.doesNotContain("name", "surname");
		assertThat(containsObjectProperties(pupilResultSchema,
				Set.of("requirementId", "points", "pointUnits", "adjustmentUnits"))).isTrue();
		assertThat(containsObjectProperties(pupilResultSchema,
				Set.of("criterionId", "awardedPointUnits", "availablePointUnits"))).isTrue();
		final Map<String, Object> resultSummary = map(outputProperties(specifications, "get_pupil_result").get("summary"));
		assertThat(list(resultSummary.get("required"))).doesNotContain("effectivePoints");

		final Map<String, Object> createCourseProperties = properties(specifications, "create_course_with_pupils");
		assertThat(createCourseProperties).containsKeys("course", "roster", "resolutions", "pupilIds").doesNotContainKey("context");
		assertThat(map(createCourseProperties.get("roster"))).containsEntry("type", "array");

		final Map<String, Object> assignPupilsProperties = properties(specifications, "assign_pupils_to_course");
		assertThat(assignPupilsProperties).containsKeys("courseId", "pupilDrafts", "resolutions", "pupilIds")
				.doesNotContainKey("context");
		assertThat(map(assignPupilsProperties.get("pupilDrafts"))).containsEntry("type", "array");

		final Map<String, Object> removePupilsProperties = properties(specifications, "remove_pupils_from_course");
		assertThat(removePupilsProperties).containsKeys("courseId", "pupilIds", "lockedPupilAction")
				.doesNotContainKey("context");
		assertThat(map(removePupilsProperties.get("pupilIds"))).containsEntry("type", "array");

		final Map<String, Object> listCoursePupilsProperties = properties(specifications, "list_course_pupils");
		assertThat(listCoursePupilsProperties).containsKeys("courseId", "includeArchived");

		final Map<String, Object> createExamProperties = properties(specifications, "create_exam");
		assertThat(createExamProperties).containsKeys("courseId", "title", "date", "gradingScaleId", "originalExamId",
				"initialPupilIds", "requestId");
		assertThat(map(createExamProperties.get("initialPupilIds"))).containsEntry("type", "array");

		final Map<String, Object> updateExamProperties = properties(specifications, "update_exam");
		assertThat(updateExamProperties).containsKeys("examId", "title", "date", "expectedRevision", "requestId").doesNotContainKeys("courseId",
				"gradingScaleId", "originalExamId", "pupilIds");
		assertThat(properties(specifications, "duplicate_exam")).containsKeys("sourceExamId", "expectedRevision", "title", "date", "initialPupilIds", "requestId");
		assertThat(list(schema(specifications, "duplicate_exam", true).get("required"))).contains("initialPupilIds");

		final Map<String, Object> assignExamPupilsProperties = properties(specifications, "assign_pupils_to_exam");
		assertThat(assignExamPupilsProperties).containsKeys("examId", "pupilIds");
		assertThat(map(assignExamPupilsProperties.get("pupilIds"))).containsEntry("type", "array");

		final Map<String, Object> removeExamPupilsProperties = properties(specifications, "remove_pupils_from_exam");
		assertThat(removeExamPupilsProperties).containsKeys("examId", "pupilIds", "lockedPupilAction")
				.doesNotContainKey("context");

		final Map<String, Object> listExamsOutputProperties = outputProperties(specifications, "list_exams");
		final Map<String, Object> examItems = map(map(listExamsOutputProperties.get("exams")).get("items"));
		assertThat(list(examItems.get("required"))).doesNotContain("originalExamId", "gradingScaleId");

		final Map<String, Object> updatePupilProperties = properties(specifications, "update_pupil");
		assertThat(updatePupilProperties).containsKeys("pupilId", "name", "surname", "duplicateNameAction")
				.doesNotContainKeys("context", "lifecycle");
	}

	@Test
	void schemaCustomizationPreservesHandlersAndOtherToolsDuringBeanInitialization() {
		final List<SyncToolSpecification> original = new SyncMcpToolProvider(toolGroups()).getToolSpecifications();
		final var customizer = new ExamContentReadMcpSchemaCustomizer();
		final Object customized = customizer.postProcessAfterInitialization(original, "toolSpecs");

		assertThat(customized).isInstanceOf(List.class);
		final List<?> specifications = (List<?>) customized;
		assertThat(specifications).hasSize(original.size());
		for (int index = 0; index < original.size(); index++) {
			final var before = original.get(index);
			final var after = (SyncToolSpecification) specifications.get(index);
			assertThat(after.callHandler()).isSameAs(before.callHandler());
			assertThat(after.tool().inputSchema()).isEqualTo(before.tool().inputSchema());
			assertThat(after.tool().annotations()).isEqualTo(before.tool().annotations());
			assertThat(after.tool().description()).isEqualTo(before.tool().description());
		}
		final Object unrelated = new Object();
		assertThat(customizer.postProcessAfterInitialization(unrelated, "unrelated")).isSameAs(unrelated);
	}

	private static Map<String, Object> properties(final List<SyncToolSpecification> specifications,
			final String toolName) {
		final Map<String, Object> schema = specifications.stream()
				.filter(specification -> specification.tool().name().equals(toolName)).findFirst().orElseThrow().tool()
				.inputSchema();
		return map(schema.get("properties"));
	}

	private static Map<String, Object> schema(final List<SyncToolSpecification> specifications, final String toolName,
			final boolean input) {
		final var tool = specifications.stream().filter(specification -> specification.tool().name().equals(toolName))
				.findFirst().orElseThrow().tool();
		return input ? tool.inputSchema() : tool.outputSchema();
	}

	private static boolean containsObjectProperties(final Object value, final Set<String> expectedProperties) {
		if (value instanceof Map<?, ?> values) {
			final Object properties = values.get("properties");
			if (properties instanceof Map<?, ?> propertyValues
					&& propertyValues.keySet().containsAll(expectedProperties)) {
				return true;
			}
			return values.values().stream()
					.anyMatch(child -> containsObjectProperties(child, expectedProperties));
		}
		if (value instanceof List<?> values) {
			return values.stream().anyMatch(child -> containsObjectProperties(child, expectedProperties));
		}
		return false;
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
		final ExamPupilWriter examPupilWriter = mock(ExamPupilWriter.class);
		final DatabaseBackupService backupService = mock(DatabaseBackupService.class);
		final PupilRosterConflictResolver conflictResolver = mock(PupilRosterConflictResolver.class);
		final PupilUpdateConflictResolver updateConflictResolver = mock(PupilUpdateConflictResolver.class);
		final ExamAuthoringService authoring = mock(ExamAuthoringService.class);
		final ExamDesignMcpSupport support = mock(ExamDesignMcpSupport.class);
		return List.of(new CourseMcpTools(courses, subjects, gradingScales),
				new ExamMcpTools(courses, exams, levelOfExpectations),
				new ExamWriteMcpTools(courses, exams, gradingScales, levelOfExpectations, mock(ExamCreationExecutor.class),
						mock(ExamMutationExecutor.class), mock(ExamDesignGuard.class)),
				new ExamPupilAssignmentMcpTools(examPupilWriter),
				new ExamPupilRemovalMcpTools(courses, exams, examPupilWriter),
				new CourseRosterMcpTools(courses, subjects, gradingScales, conflictResolver, writer),
				new CoursePupilAssignmentMcpTools(courses, conflictResolver, assignmentWriter),
				new CoursePupilRemovalMcpTools(courses, removalWriter), new DatabaseBackupMcpTools(backupService),
				new LevelOfExpectationsMcpTools(support, authoring),
				new ExamDesignMcpTools(support, authoring, courses, gradingScales),
				new ExamContentReadMcpTools(support, levelOfExpectations),
				new McpContextTools(mock(FileAccessSettingsService.class)),
				new MaterialImageMcpTools(mock(MaterialImageImportService.class)),
				new PupilResultWriteMcpTools(mock(ExamAssessmentService.class), mock(PupilResultMcpTools.class), mock(ExamDesignGuard.class)),
				new PupilMcpTools(pupils, conflictResolver, updateConflictResolver, pupilWriter),
				new PupilResultMcpTools(exams, gradingScales, levelOfExpectations, pupils));
	}

	private static McpTool tool(final Class<?> type, final String name, final Class<?>... parameters)
			throws NoSuchMethodException {
		final Method method = type.getMethod(name, parameters);
		return method.getAnnotation(McpTool.class);
	}
}
