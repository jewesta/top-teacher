package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import de.westarps.topteacher.backend.mcp.McpAccessTokenService;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"tt.mcp.enabled=true", "tt.mcp.token-file=", "server.servlet.context-path=/top-teacher",
		"spring.datasource.url=jdbc:h2:mem:mcp-duplication-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class TopTeacherMcpDuplicationHttpTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	@LocalServerPort private int port;
	@Autowired private McpAccessTokenService tokens;
	@Autowired private CourseRepository courses;
	@Autowired private ExamRepository exams;
	@Autowired private PupilRepository pupils;
	@Autowired private SubjectRepository subjects;
	@Autowired private GradingScaleRepository scales;
	@Autowired private LevelOfExpectationsRepository design;

	@ParameterizedTest
	@EnumSource(McpAccessLevel.class)
	void copiesLockedDesignOnceWithoutCopyingResultsOrImplicitlyAssigningPupils(final McpAccessLevel mode) throws Exception {
		final String unique = UUID.randomUUID().toString();
		final var scale = scales.findActive().getFirst();
		final var course = courses.save(new Course(null, SchoolClass.CLS_Q1, subjects.findActive().getFirst(),
				new SchoolYear(mode == McpAccessLevel.FULL ? 2077 : 2078), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, scale.id()));
		final var pupil = pupils.save(new Pupil(null, "SyntheticCopy", unique, Lifecycle.ACTIVE));
		courses.assignPupil(course.id(), pupil.id());
		final var source = exams.save(new Exam(null, course.id(), "Source", LocalDate.of(2077, 5, 1), null, scale.id()), List.of(pupil.id()));
		final var part = design.savePart(new LoePart(null, source.id(), "Part", 0));
		final var category = design.saveCategory(new LoeCategory(null, part.id(), "Category", "Context", 0));
		final var task = design.saveTask(new LoeTask(null, category.id(), "Task", "Task text", scale.maxPoints(), 0));
		final var material = design.saveMaterial(new LoeMaterial(null, part.id(), "Source material", "Material text", 0));
		final var requirement = design.saveRequirement(new LoeRequirement(null, task.id(),
				"Recognises [the finding](crit:1?points=" + scale.maxPoints() + ").", scale.maxPoints(), false, 0));
		design.saveExamFooterMarkdown(source.id(), "**Closing text**");
		design.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 0, 0, "Source result"));
		final long sourceRevision = design.designRevision(source.id());
		final var token = tokens.create("Copy " + mode + " " + unique, mode, null);
		try (HttpClient client = HttpClient.newHttpClient()) {
			final var initialized = client.send(request("""
				{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"copy-test","version":"1"}}}
				""", token.credential(), null), HttpResponse.BodyHandlers.ofString());
			assertThat(initialized.statusCode()).isEqualTo(200);
			final String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
			final Map<String, Object> arguments = Map.of("sourceExamId", source.id(), "expectedRevision", sourceRevision,
					"title", "Copy " + mode, "date", "2077-05-02", "initialPupilIds", List.of(), "requestId", "copy-key");
			final JsonNode copied = call(client, token.credential(), session, arguments);
			final int copyId = copied.path("exam").path("id").asInt();
			assertThat(copyId).isPositive().isNotEqualTo(source.id());
			assertThat(exams.findPupils(copyId)).isEmpty();
			assertThat(exams.findById(copyId).orElseThrow().originalExamId()).isNull();
			assertThat(design.hasResultsForExam(copyId)).isFalse();
			assertThat(design.findExamFooterMarkdown(copyId)).isEqualTo("**Closing text**");
			assertThat(design.findPartsByExamId(copyId)).singleElement().satisfies(item -> assertThat(item.id()).isNotEqualTo(part.id()));
			assertThat(design.findTasksByExamId(copyId)).singleElement().satisfies(item -> {
				assertThat(item.id()).isNotEqualTo(task.id());
				assertThat(item.descriptionMarkdown()).isEqualTo("Task text");
			});
			assertThat(design.findMaterialsByExamId(copyId)).singleElement().satisfies(item -> assertThat(item.id()).isNotEqualTo(material.id()));
			assertThat(design.findRequirementsByExamId(copyId)).singleElement().satisfies(item -> assertThat(item.id()).isNotEqualTo(requirement.id()));
			assertThat(design.findActiveCriteriaByExamId(copyId)).hasSize(1);
			assertThat(design.designRevision(source.id())).isEqualTo(sourceRevision);
			final JsonNode retried = call(client, token.credential(), session, arguments);
			assertThat(retried.path("exam").path("id").asInt()).isEqualTo(copyId);
			assertThat(retried.path("replayed").asBoolean()).isTrue();
			assertThat(exams.findByCourseId(course.id())).hasSize(2);
		}
	}

	private JsonNode call(final HttpClient client, final String credential, final String session,
			final Map<String, Object> arguments) throws Exception {
		final String body = JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", UUID.randomUUID().toString(),
				"method", "tools/call", "params", Map.of("name", "duplicate_exam", "arguments", arguments)));
		final var response = client.send(request(body, credential, session), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		final String payload = response.body().stripLeading().startsWith("{") ? response.body()
				: response.body().lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5).trim())
						.filter(line -> line.startsWith("{")).findFirst().orElseThrow();
		final var rpc = JSON.readTree(payload);
		assertThat(rpc.has("error")).as(payload).isFalse();
		final var result = rpc.path("result");
		assertThat(result.path("isError").asBoolean(false)).as(payload).isFalse();
		return result.path("structuredContent").isObject() ? result.path("structuredContent")
				: JSON.readTree(result.path("content").get(0).path("text").asString());
	}

	private HttpRequest request(final String body, final String credential, final String session) {
		final var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/top-teacher/mcp"))
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
				.header("Authorization", "Bearer " + credential).POST(HttpRequest.BodyPublishers.ofString(body));
		if (session != null) {
			builder.header("Mcp-Session-Id", session);
		}
		return builder.build();
	}
}
