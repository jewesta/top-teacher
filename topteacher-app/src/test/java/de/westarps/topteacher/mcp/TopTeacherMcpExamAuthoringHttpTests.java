package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.westarps.topteacher.backend.mcp.McpAccessTokenService;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderedPng;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"tt.mcp.enabled=true", "tt.mcp.token-file=", "server.servlet.context-path=/top-teacher",
		"spring.datasource.url=jdbc:h2:mem:mcp-authoring-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class TopTeacherMcpExamAuthoringHttpTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String INITIALIZE = """
		{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"exam-authoring-test","version":"1"}}}
		""";
	@LocalServerPort private int port;
	@TempDir private Path tempDir;
	@Autowired private McpAccessTokenService tokens;
	@Autowired private CourseRepository courses;
	@Autowired private PupilRepository pupils;
	@Autowired private SubjectRepository subjects;
	@Autowired private GradingScaleRepository scales;
	@Autowired private LevelOfExpectationsRepository levels;
	@Autowired private AppSettings settings;
	@MockitoBean private FaithfulMarkdownRenderer renderer;

	@ParameterizedTest
	@EnumSource(McpAccessLevel.class)
	void authorsAnExamIncrementallyAndEnforcesCorrectionModeOverRealHttp(final McpAccessLevel mode) throws Exception {
		settings.saveTtFileAccessInstallationLocation(InstallationLocation.LOCAL);
		final String unique = UUID.randomUUID().toString();
		final Pupil pupil = pupils.save(new Pupil(null, "SyntheticAuthoring", unique, Lifecycle.ACTIVE));
		final Course course = course(unique, pupil);
		final var token = tokens.create("Authoring " + mode + " " + unique, mode, null);
		try (HttpClient client = HttpClient.newHttpClient()) {
			final Exchange exchange = initialize(client, token.credential());
			final JsonNode inventory = rpc(exchange, "tools/list", Map.of()).path("result");
			final List<String> toolNames = new ArrayList<>();
			inventory.path("tools").forEach(tool -> toolNames.add(tool.path("name").asString()));
			assertThat(toolNames).contains("get_mcp_context", "get_markdown_guide", "get_exam",
					"get_exam_authoring_options", "apply_exam_content_changes", "apply_level_of_expectations_changes",
					"import_material_image", "get_material_image", "save_pupil_result", "get_exam_structure", "get_task",
					"get_material", "get_exam_footer", "get_task_requirements").doesNotContain("create_level_of_expectations");
			for (final String name : List.of("get_task", "get_material", "get_exam_footer", "get_task_requirements")) {
				final JsonNode tool = java.util.stream.StreamSupport.stream(inventory.path("tools").spliterator(), false)
						.filter(candidate -> name.equals(candidate.path("name").asString())).findFirst().orElseThrow();
				assertThat(tool.path("inputSchema").path("properties").has("includeFaithfulRender")).isTrue();
				assertThat(tool.path("inputSchema").path("required").toString()).doesNotContain("includeFaithfulRender");
				assertThat(tool.path("outputSchema").path("properties").has("examId")).isTrue();
				assertThat(tool.path("outputSchema").path("properties").has("permissions")).isTrue();
			}
			final JsonNode context = data(call(exchange, "get_mcp_context", Map.of()));
			assertThat(context.path("accessLevel").asString()).isEqualTo(mode.name());
			assertThat(context.path("imageImport").path("installationLocation").asString()).isEqualTo("LOCAL");
			assertThat(context.toString()).doesNotContain("serverPath", "clientPath");
			assertThat(context.path("availableOperations").toString()).contains("get_exam_structure", "get_task",
					"get_material", "get_exam_footer", "get_task_requirements");
			assertGuides(exchange);

			final Map<String, Object> creationArguments = Map.of("courseId", course.id(),
					"title", "Synthetic exam " + mode, "date", "2076-05-01", "requestId", "create-shell");
			final JsonNode createdExam = data(call(exchange, "create_exam", creationArguments));
			final int examId = createdExam.path("exam").path("id").asInt();
			if (mode == McpAccessLevel.PSEUDONYMIZED) {
				assertThat(createdExam.toString()).doesNotContain(pupil.name(), pupil.surname());
			}
			final JsonNode emptyExam = snapshot(exchange, examId);
			assertThat(emptyExam.path("parts").size()).isZero();
			final JsonNode initial = content(exchange, examId, emptyExam.path("revision").asLong(), "initial", List.of(
					Map.of("type", "CREATE_PART", "clientKey", "part-a", "title", "Part A"),
					Map.of("type", "CREATE_CATEGORY", "clientKey", "category-a", "parentKey", "part-a", "title", "Tasks"),
					Map.of("type", "CREATE_TASK", "clientKey", "task-a", "parentKey", "category-a", "title", "Analyse",
							"markdown", "Explain the source.", "maxPoints", 40),
					Map.of("type", "CREATE_TASK", "clientKey", "task-zero", "parentKey", "category-a", "title", "Check",
							"markdown", "Check your answer.", "maxPoints", 0),
					Map.of("type", "CREATE_MATERIAL", "clientKey", "material", "parentKey", "part-a", "title", "Source",
							"markdown", "Synthetic source text."),
					Map.of("type", "SET_FOOTER", "markdown", "**Good luck!**")));
			final int partA = createdId(initial, "part-a");
			final int categoryA = createdId(initial, "category-a");
			final int taskA = createdId(initial, "task-a");
			final int taskZero = createdId(initial, "task-zero");
			final int materialId = createdId(initial, "material");
			assertThat(levels.findRequirementsByExamId(examId)).isEmpty();
			final JsonNode contentOnlyEh = data(call(exchange, "get_level_of_expectations", Map.of("examId", examId)));
			assertThat(contentOnlyEh.path("tasks").size()).isEqualTo(2);
			contentOnlyEh.path("tasks").forEach(task -> assertThat(task.path("requirements").size()).isZero());
			assertThat(snapshot(exchange, examId).path("footerMarkdown").asString()).isEqualTo("**Good luck!**");
			final JsonNode creationReplay = data(call(exchange, "create_exam", creationArguments));
			assertThat(creationReplay.path("replayed").asBoolean()).isTrue();
			assertThat(creationReplay.path("exam").path("id").asInt()).isEqualTo(examId);
			assertThat(creationReplay.path("revision").asLong()).isEqualTo(initial.path("revision").asLong());
			assertThat(creationReplay.path("mutationRevision").asLong()).isEqualTo(createdExam.path("mutationRevision").asLong());

			final long additionRevision = initial.path("revision").asLong();
			final List<Map<String, Object>> additions = List.of(
					Map.of("type", "CREATE_PART", "clientKey", "part-b", "title", "Part B"),
					Map.of("type", "CREATE_CATEGORY", "clientKey", "category-b", "parentKey", "part-b", "title", "Argument"),
					Map.of("type", "CREATE_TASK", "clientKey", "task-b", "parentKey", "category-b", "title", "Discuss",
							"markdown", "Discuss the implications.", "maxPoints", 60));
			final JsonNode added = content(exchange, examId, additionRevision, "addition", additions);
			final int partB = createdId(added, "part-b");
			final int taskB = createdId(added, "task-b");
			assertThat(levels.findPartsByExamId(examId)).hasSize(2);
			assertThat(levels.findRequirementsByExamId(examId)).isEmpty();
			final JsonNode edited = content(exchange, examId, added.path("revision").asLong(), "target-edit", List.of(
					Map.of("type", "UPDATE_TASK", "id", taskA, "markdown", "Explain with evidence.")));
			assertThat(levels.findTasksByExamId(examId).stream().filter(task -> task.id() == taskA).findFirst().orElseThrow())
					.satisfies(task -> {
						assertThat(task.title()).isEqualTo("Analyse");
						assertThat(task.descriptionMarkdown()).isEqualTo("Explain with evidence.");
					});

			final Path imagePath = image();
			final JsonNode imported = data(call(exchange, "import_material_image", Map.of("examId", examId,
					"materialId", materialId, "sourcePath", imagePath.toString(), "altText", "Synthetic chart",
					"widthPercent", 75, "alignment", "CENTER", "expectedRevision", edited.path("revision").asLong(),
					"requestId", "image")));
			final String imageId = imported.path("image").path("imageId").asString();
			assertThat(imported.path("mutationRevision").asLong()).isEqualTo(imported.path("revision").asLong());
			assertThat(imported.path("material").path("descriptionMarkdown").asString())
					.contains("![Synthetic chart](img:" + imageId + "?width=75)");
			final JsonNode imageResult = call(exchange, "get_material_image",
					Map.of("examId", examId, "materialId", materialId, "imageId", imageId));
			assertNativeImage(imageResult);
			final JsonNode imageSnapshot = snapshot(exchange, examId);
			final JsonNode material = findById(findById(imageSnapshot.path("parts"), partA).path("materials"), materialId);
			assertThat(material.path("images").get(0).path("imageId").asString()).isEqualTo(imageId);
			assertThat(material.path("images").get(0).has("data")).isFalse();
			final JsonNode spareCreated = content(exchange, examId, imported.path("revision").asLong(), "spare-material", List.of(
					Map.of("type", "CREATE_MATERIAL", "clientKey", "spare", "parentId", partA, "title", "Temporary chart")));
			final int spareMaterialId = createdId(spareCreated, "spare");
			final Map<String, Object> spareImportArguments = Map.of("examId", examId, "materialId", spareMaterialId,
					"sourcePath", imagePath.toString(), "altText", "Automatic chart",
					"expectedRevision", spareCreated.path("revision").asLong(), "requestId", "spare-image");
			final JsonNode spareImported = data(call(exchange, "import_material_image", spareImportArguments));
			assertThat(spareImported.path("image").path("imageId").asString()).isEqualTo(imageId);
			assertThat(spareImported.path("material").path("descriptionMarkdown").asString())
					.isEqualTo("![Automatic chart](img:" + imageId + ")");
			final JsonNode spareDeleted = content(exchange, examId, spareImported.path("revision").asLong(), "delete-spare", List.of(
					Map.of("type", "DELETE_MATERIAL", "id", spareMaterialId, "acknowledgeDeletion", true)));
			final JsonNode deletedMaterialReplay = data(call(exchange, "import_material_image", spareImportArguments));
			assertThat(deletedMaterialReplay.path("replayed").asBoolean()).isTrue();
			assertThat(deletedMaterialReplay.path("revision").asLong()).isEqualTo(spareDeleted.path("revision").asLong());
			assertThat(deletedMaterialReplay.path("mutationRevision").asLong()).isEqualTo(spareImported.path("mutationRevision").asLong());
			assertThat(deletedMaterialReplay.has("material")).isFalse();

			final JsonNode eh = data(call(exchange, "apply_level_of_expectations_changes", Map.of("examId", examId,
					"expectedRevision", deletedMaterialReplay.path("revision").asLong(), "requestId", "eh", "commands", List.of(
							Map.of("type", "CREATE_REQUIREMENT", "clientKey", "requirement-a", "taskId", taskA,
									"markdown", "Uses [evidence](crit:1?points=40).", "maxPoints", 40),
							Map.of("type", "CREATE_REQUIREMENT", "clientKey", "requirement-b", "taskId", taskB,
									"markdown", "Shows [reasoning](crit:1?points=60).", "maxPoints", 60)))));
			final int requirementA = createdId(eh, "requirement-a");
			final int criterionA = levels.findActiveCriteriaByExamId(examId).stream()
					.filter(criterion -> criterion.requirementId() == requirementA).findFirst().orElseThrow().id();
			final JsonNode reordered = content(exchange, examId, eh.path("revision").asLong(), "reorder", List.of(
					Map.of("type", "REORDER_PARTS", "order", List.of(Map.of("id", partB), Map.of("id", partA))),
					Map.of("type", "REORDER_TASKS", "parentId", categoryA,
							"order", List.of(Map.of("id", taskZero), Map.of("id", taskA)))));
			assertThat(levels.findPartsByExamId(examId)).extracting(part -> part.id()).containsExactly(partB, partA);
			assertThat(levels.findTasksByExamId(examId).stream().filter(task -> task.categoryId() == categoryA).toList())
					.extracting(task -> task.id()).containsExactly(taskZero, taskA);
			assertThat(levels.findRequirementsByExamId(examId).stream().filter(requirement -> requirement.id() == requirementA)
					.findFirst().orElseThrow().taskId()).isEqualTo(taskA);
			assertThat(levels.isPointDistributionValidForExam(examId)).isTrue();
			assertFocusedReads(exchange, examId, reordered.path("revision").asLong(), partA, partB, taskA,
					taskZero, materialId, requirementA, imageId);
			assertOptionalRenders(exchange, examId, taskA, materialId);
			final int otherExamId = data(call(exchange, "create_exam", Map.of("courseId", course.id(),
					"title", "Other exam " + unique, "date", "2076-05-02", "initialPupilIds", List.of(),
					"requestId", "other-shell"))).path("exam").path("id").asInt();
			assertFailure(exchange, "get_task", Map.of("examId", otherExamId, "taskId", taskA));
			assertFailure(exchange, "get_task_requirements", Map.of("examId", otherExamId, "taskId", taskA));
			assertFailure(exchange, "get_material", Map.of("examId", otherExamId, "materialId", materialId));
			assertFailure(exchange, "get_task", Map.of("examId", examId, "taskId", Integer.MAX_VALUE));
			assertFailure(exchange, "get_material", Map.of("examId", examId, "materialId", Integer.MAX_VALUE));
			assertThat(data(call(exchange, "get_exam_footer", Map.of("examId", otherExamId)))
					.path("footerMarkdown").asString()).isEmpty();
			final JsonNode emptyStructure = data(call(exchange, "get_exam_structure", Map.of("examId", otherExamId)));
			assertThat(emptyStructure.path("parts").size()).isZero();
			assertThat(emptyStructure.path("closingTextPresent").asBoolean()).isFalse();
			final JsonNode replay = content(exchange, examId, additionRevision, "addition", additions);
			assertThat(replay.path("replayed").asBoolean()).isTrue();
			assertThat(createdId(replay, "part-b")).isEqualTo(partB);
			assertThat(levels.findPartsByExamId(examId)).hasSize(2);
			assertFailure(exchange, "apply_exam_content_changes", Map.of("examId", examId,
					"expectedRevision", additionRevision, "requestId", "stale", "commands",
					List.of(Map.of("type", "SET_FOOTER", "markdown", "Stale overwrite"))));
			assertThat(snapshot(exchange, examId).path("revision").asLong()).isEqualTo(reordered.path("revision").asLong());

			final JsonNode assessed = data(call(exchange, "save_pupil_result", Map.of("examId", examId,
					"pupilId", pupil.id(), "expectedRevision", reordered.path("revision").asLong(), "requestId", "zero-result",
					"assessments", List.of(Map.of("requirementId", requirementA, "freePointUnits", 0,
							"criterionAwards", List.of(Map.of("criterionId", criterionA, "pointUnits", 0)),
							"comment", "Synthetic result")))));
			assertThat(assessed.path("replayed").asBoolean()).isFalse();
			if (mode == McpAccessLevel.PSEUDONYMIZED) {
				assertThat(assessed.toString()).doesNotContain(pupil.name(), pupil.surname());
			}
			final JsonNode locked = snapshot(exchange, examId);
			final long lockedRevision = locked.path("revision").asLong();
			assertThat(locked.path("permissions").path("correctionMode").asBoolean()).isTrue();
			assertThat(locked.path("permissions").path("canEditExamContent").asBoolean()).isFalse();
			for (final Map<String, Object> forbidden : List.<Map<String, Object>>of(
					Map.of("type", "UPDATE_TASK", "id", taskA, "markdown", "Locked change"),
					Map.of("type", "CREATE_PART", "clientKey", "locked-addition", "title", "Locked addition"),
					Map.of("type", "SET_FOOTER", "markdown", "Locked footer"),
					Map.of("type", "REORDER_PARTS", "order", List.of(Map.of("id", partA), Map.of("id", partB))))) {
				assertFailure(exchange, "apply_exam_content_changes", Map.of("examId", examId, "expectedRevision", lockedRevision,
						"requestId", UUID.randomUUID().toString(), "commands", List.of(forbidden)));
			}
			assertFailure(exchange, "import_material_image", Map.of("examId", examId, "materialId", materialId,
					"sourcePath", imagePath.toString(), "altText", "Locked chart", "expectedRevision", lockedRevision,
					"requestId", "locked-image"));
			assertThat(snapshot(exchange, examId).path("revision").asLong()).isEqualTo(lockedRevision);
			final JsonNode corrected = data(call(exchange, "apply_level_of_expectations_changes", Map.of("examId", examId,
					"expectedRevision", lockedRevision, "requestId", "safe-correction", "commands", List.of(
							Map.of("type", "UPDATE_REQUIREMENT", "id", requirementA,
									"markdown", "Uses [precise evidence](crit:1?points=40).")))));
			assertThat(corrected.path("revision").asLong()).isGreaterThan(lockedRevision);
			assertThat(levels.findActiveCriteriaByExamId(examId).stream().filter(criterion -> criterion.id() == criterionA)
					.findFirst().orElseThrow().label()).isEqualTo("precise evidence");
			assertThat(levels.findRequirementResultsByExamAndPupil(examId, pupil.id())).singleElement()
					.satisfies(result -> assertThat(result.pointUnits()).isZero());
			assertThat(snapshot(exchange, examId).path("footerMarkdown").asString()).isEqualTo("**Good luck!**");
			assertThat(data(call(exchange, "get_task_requirements", Map.of("examId", examId, "taskId", taskA)))
					.path("requirements").get(0).path("criteria").get(0).path("label").asString()).isEqualTo("precise evidence");
			assertThat(data(call(exchange, "get_task", Map.of("examId", examId, "taskId", taskA)))
					.path("permissions").path("canEditExamContent").asBoolean()).isFalse();
		}
	}

	private void assertFocusedReads(final Exchange exchange, final int examId, final long revision,
			final int partA, final int partB, final int taskA, final int taskZero,
			final int materialId, final int requirementA, final String imageId) throws Exception {
		final JsonNode outline = data(call(exchange, "get_exam_structure", Map.of("examId", examId)));
		assertThat(outline.path("revision").asLong()).isEqualTo(revision);
		assertThat(outline.path("closingTextPresent").asBoolean()).isTrue();
		assertThat(outline.path("parts").get(0).path("id").asInt()).isEqualTo(partB);
		assertThat(outline.path("parts").get(1).path("id").asInt()).isEqualTo(partA);
		final JsonNode category = findById(outline.path("parts"), partA).path("categories").get(0);
		assertThat(category.path("tasks").get(0).path("id").asInt()).isEqualTo(taskZero);
		assertThat(category.path("tasks").get(1).path("id").asInt()).isEqualTo(taskA);
		assertThat(outline.toString()).doesNotContain("descriptionMarkdown", "footerMarkdown", "images", "requirements",
				"Explain with evidence.", "Synthetic source text.", "Uses [evidence]");
		final JsonNode task = data(call(exchange, "get_task", Map.of("examId", examId, "taskId", taskA)));
		assertThat(task.path("task").path("id").asInt()).isEqualTo(taskA);
		assertThat(task.path("task").path("descriptionMarkdown").asString()).isEqualTo("Explain with evidence.");
		assertThat(task.path("task").path("maxPoints").asInt()).isEqualTo(40);
		assertThat(task.toString()).doesNotContain("Discuss the implications.", "Synthetic source text.", "requirements", "footerMarkdown");
		final JsonNode material = data(call(exchange, "get_material", Map.of("examId", examId, "materialId", materialId)));
		assertThat(material.path("material").path("id").asInt()).isEqualTo(materialId);
		assertThat(material.path("material").path("descriptionMarkdown").asString()).startsWith("Synthetic source text.");
		assertThat(material.path("material").path("images").get(0).path("imageId").asString()).isEqualTo(imageId);
		assertThat(material.path("material").path("images").get(0).has("data")).isFalse();
		assertThat(material.toString()).doesNotContain("Explain with evidence.", "Discuss the implications.", "requirements", "footerMarkdown");
		final JsonNode footer = data(call(exchange, "get_exam_footer", Map.of("examId", examId)));
		assertThat(footer.path("footerMarkdown").asString()).isEqualTo("**Good luck!**");
		assertThat(footer.toString()).doesNotContain("Explain with evidence.", "Synthetic source text.", "requirements");
		final JsonNode requirements = data(call(exchange, "get_task_requirements", Map.of("examId", examId, "taskId", taskA)));
		assertThat(requirements.path("task").path("id").asInt()).isEqualTo(taskA);
		assertThat(requirements.path("requirements").size()).isOne();
		assertThat(requirements.path("requirements").get(0).path("id").asInt()).isEqualTo(requirementA);
		assertThat(requirements.path("requirements").get(0).path("criteria").get(0).path("label").asString()).isEqualTo("evidence");
		assertThat(requirements.toString()).doesNotContain("Explain with evidence.", "Shows [reasoning]", "Synthetic source text.");
		final JsonNode emptyRequirements = data(call(exchange, "get_task_requirements", Map.of("examId", examId, "taskId", taskZero)));
		assertThat(emptyRequirements.path("requirements").size()).isZero();
		for (final JsonNode response : List.of(outline, task, material, footer, requirements, emptyRequirements)) {
			assertThat(response.path("examId").asInt()).isEqualTo(examId);
			assertThat(response.path("revision").asLong()).isEqualTo(revision);
			assertThat(response.path("permissions").path("correctionMode").asBoolean()).isFalse();
			assertThat(response.toString()).doesNotContain("pupils", "results", "SyntheticAuthoring");
		}
	}

	private void assertGuides(final Exchange exchange) throws Exception {
		final JsonNode material = data(call(exchange, "get_markdown_guide", Map.of("context", "MATERIAL")));
		assertThat(material.path("capabilities").path("managedImages").asBoolean()).isTrue();
		assertThat(material.path("capabilities").path("assessmentCriteria").asBoolean()).isFalse();
		assertThat(material.path("markdown").asString()).contains("img:");
		final JsonNode requirement = data(call(exchange, "get_markdown_guide", Map.of("context", "REQUIREMENT")));
		assertThat(requirement.path("capabilities").path("assessmentCriteria").asBoolean()).isTrue();
		assertThat(requirement.path("capabilities").path("managedImages").asBoolean()).isFalse();
		assertThat(requirement.path("markdown").asString()).contains("crit:");
		final JsonNode footer = data(call(exchange, "get_markdown_guide", Map.of("context", "EXAM_FOOTER")));
		assertThat(footer.path("capabilities").path("pupilExamSheetExport").asBoolean()).isFalse();
	}

	private void assertOptionalRenders(final Exchange exchange, final int examId, final int taskId,
			final int materialId) throws Exception {
		final JsonNode plainTask = call(exchange, "get_task", Map.of("examId", examId, "taskId", taskId,
				"includeFaithfulRender", false));
		assertThat(data(plainTask).has("faithfulRender")).isFalse();
		assertThat(plainTask.path("content").size()).isOne();
		verifyNoInteractions(renderer);
		final ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", png);
		when(renderer.render(any())).thenReturn(new RenderedPng(png.toByteArray(), 2, 3));
		final Map<String, Map<String, Object>> requests = Map.of(
				"get_task", Map.of("examId", examId, "taskId", taskId, "includeFaithfulRender", true),
				"get_material", Map.of("examId", examId, "materialId", materialId, "includeFaithfulRender", true),
				"get_exam_footer", Map.of("examId", examId, "includeFaithfulRender", true),
				"get_task_requirements", Map.of("examId", examId, "taskId", taskId, "includeFaithfulRender", true));
		for (final var entry : requests.entrySet()) {
			final JsonNode result = call(exchange, entry.getKey(), entry.getValue());
			assertThat(result.path("structuredContent").isObject()).isTrue();
			assertThat(data(result).path("faithfulRender").path("status").asString()).isEqualTo("rendered");
			assertThat(data(result).path("faithfulRender").path("pixelWidth").asInt()).isEqualTo(2);
			assertThat(data(result).path("faithfulRender").path("pixelHeight").asInt()).isEqualTo(3);
			assertThat(result.path("content").size()).isEqualTo(2);
			assertThat(result.path("content").get(0).path("type").asString()).isEqualTo("text");
			assertThat(JSON.readTree(result.path("content").get(0).path("text").asString())).isEqualTo(data(result));
			final JsonNode image = result.path("content").get(1);
			assertThat(image.path("type").asString()).isEqualTo("image");
			assertThat(image.path("mimeType").asString()).isEqualTo("image/png");
			final BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(
					Base64.getDecoder().decode(image.path("data").asString())));
			assertThat(decoded.getWidth()).isEqualTo(2);
			assertThat(decoded.getHeight()).isEqualTo(3);
		}
		when(renderer.render(any())).thenThrow(new IllegalStateException("Private browser installation path"));
		final JsonNode unavailable = call(exchange, "get_task", requests.get("get_task"));
		assertThat(data(unavailable).path("task").path("descriptionMarkdown").asString())
				.isEqualTo("Explain with evidence.");
		assertThat(data(unavailable).path("faithfulRender").path("status").asString()).isEqualTo("unavailable");
		assertThat(unavailable.path("content").size()).isOne();
		assertThat(unavailable.toString()).doesNotContain("Private browser installation path");
	}

	private JsonNode content(final Exchange exchange, final int examId, final long revision, final String requestId,
			final List<? extends Map<String, ?>> commands) throws Exception {
		return data(call(exchange, "apply_exam_content_changes", Map.of("examId", examId, "expectedRevision", revision,
				"requestId", requestId, "commands", commands)));
	}

	private JsonNode snapshot(final Exchange exchange, final int examId) throws Exception {
		return data(call(exchange, "get_exam", Map.of("examId", examId)));
	}

	private void assertFailure(final Exchange exchange, final String tool, final Map<String, ?> arguments) throws Exception {
		final JsonNode response = rpc(exchange, "tools/call", Map.of("name", tool, "arguments", arguments));
		assertThat(response.has("error") || response.path("result").path("isError").asBoolean(false))
				.as("Expected %s to fail: %s", tool, response).isTrue();
	}

	private JsonNode call(final Exchange exchange, final String tool, final Map<String, ?> arguments) throws Exception {
		final JsonNode response = rpc(exchange, "tools/call", Map.of("name", tool, "arguments", arguments));
		assertThat(response.has("error")).as(response.toString()).isFalse();
		final JsonNode result = response.path("result");
		assertThat(result.path("isError").asBoolean(false)).as(result.toString()).isFalse();
		return result;
	}

	private JsonNode rpc(final Exchange exchange, final String method, final Map<String, ?> params) throws Exception {
		final String body = JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", UUID.randomUUID().toString(),
				"method", method, "params", params));
		final HttpResponse<String> response = exchange.client().send(request(body, exchange.token(), exchange.session()),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		final String json = response.body().stripLeading().startsWith("{") ? response.body()
				: response.body().lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5).trim())
						.filter(line -> line.startsWith("{")).findFirst().orElseThrow();
		return JSON.readTree(json);
	}

	private static JsonNode data(final JsonNode toolResult) {
		final JsonNode structured = toolResult.path("structuredContent");
		return structured.isObject() ? structured : JSON.readTree(toolResult.path("content").get(0).path("text").asString());
	}

	private static int createdId(final JsonNode result, final String key) {
		for (final JsonNode created : result.path("created")) {
			if (created.path("clientKey").asString("").equals(key)) {
				return created.path("id").asInt();
			}
		}
		throw new AssertionError("No created ID for " + key + ": " + result);
	}

	private static JsonNode findById(final JsonNode values, final int id) {
		for (final JsonNode value : values) {
			if (value.path("id").asInt() == id) {
				return value;
			}
		}
		throw new AssertionError("No node with ID " + id);
	}

	private static void assertNativeImage(final JsonNode result) throws Exception {
		assertThat(result.path("content").size()).isOne();
		final JsonNode content = result.path("content").get(0);
		assertThat(content.path("type").asString()).isEqualTo("image");
		assertThat(content.path("mimeType").asString()).isEqualTo("image/png");
		final BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(content.path("data").asString())));
		assertThat(image.getWidth()).isEqualTo(2);
		assertThat(image.getHeight()).isEqualTo(3);
	}

	private Exchange initialize(final HttpClient client, final String token) throws Exception {
		final HttpResponse<String> initialized = client.send(request(INITIALIZE, token, null), HttpResponse.BodyHandlers.ofString());
		assertThat(initialized.statusCode()).as(initialized.body()).isEqualTo(200);
		final String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
		final HttpResponse<String> notification = client.send(request(
				"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token, session), HttpResponse.BodyHandlers.ofString());
		assertThat(notification.statusCode()).isIn(200, 202);
		return new Exchange(client, token, session);
	}

	private HttpRequest request(final String body, final String token, final String session) {
		final HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + port + "/top-teacher/mcp"))
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
				.header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.ofString(body));
		if (session != null) {
			request.header("Mcp-Session-Id", session).header("MCP-Protocol-Version", "2025-06-18");
		}
		return request.build();
	}

	private Course course(final String unique, final Pupil pupil) {
		final Subject subject = subjects.save(new Subject(null, "Authoring " + unique, Lifecycle.ACTIVE));
		final int scaleId = scales.findActive().stream().filter(scale -> scale.maxPoints() == 100).findFirst().orElseThrow().id();
		final Course course = courses.save(new Course(null, SchoolClass.CLS_10F, subject, new SchoolYear(2076),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, scaleId));
		courses.assignPupil(course.id(), pupil.id());
		return course;
	}

	private Path image() throws Exception {
		final Path path = tempDir.resolve("synthetic-chart.png");
		ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", path.toFile());
		return path;
	}

	private record Exchange(HttpClient client, String token, String session) {
	}
}
