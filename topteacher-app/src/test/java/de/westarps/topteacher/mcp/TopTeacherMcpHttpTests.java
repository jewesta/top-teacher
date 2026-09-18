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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

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

	@Autowired
	private CourseRepository courses;

	@Autowired
	private PupilRepository pupils;

	@Autowired
	private SubjectRepository subjects;

	@Autowired
	private GradingScaleRepository gradingScales;

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
			assertThat(tools.body()).contains("assign_pupils_to_course", "create_course_with_pupils",
					"create_level_of_expectations", "create_pupils", "get_course_creation_options",
					"get_level_of_expectations", "get_pupil_result", "list_courses", "list_exam_pupils", "list_exams",
					"list_pupils", "update_pupil");

			final Pupil activeScopePupil = pupils.save(new Pupil(null, "McpHttpActive", "Scope", Lifecycle.ACTIVE));
			final Pupil archivedScopePupil = pupils
					.save(new Pupil(null, "McpHttpArchived", "Scope", Lifecycle.INACTIVE));
			final HttpResponse<String> activePupils = client.send(request(pupilListCall(3, null), TOKEN, sessionId),
					HttpResponse.BodyHandlers.ofString());

			assertThat(activePupils.statusCode()).isEqualTo(200);
			assertThat(activePupils.body()).contains(activeScopePupil.name()).doesNotContain(archivedScopePupil.name());

			final HttpResponse<String> archivedPupils = client.send(
					request(pupilListCall(4, "ARCHIVED"), TOKEN, sessionId), HttpResponse.BodyHandlers.ofString());

			assertThat(archivedPupils.statusCode()).isEqualTo(200);
			assertThat(archivedPupils.body()).contains(archivedScopePupil.name())
					.doesNotContain(activeScopePupil.name());

			final Pupil existingPupil = pupils.save(new Pupil(null, "McpHttp", "Duplicate", Lifecycle.ACTIVE));
			final Subject subject = subjects.findActive().getFirst();
			final GradingScale gradingScale = gradingScales.findActive().getFirst();
			final String initialCourseCall = courseCreationCall(5, subject.id(), gradingScale.id(), null);
			final HttpResponse<String> unresolved = client.send(request(initialCourseCall, TOKEN, sessionId),
					HttpResponse.BodyHandlers.ofString());

			assertThat(unresolved.statusCode()).isEqualTo(200);
			assertThat(unresolved.body()).contains("NEEDS_RESOLUTION", "row-1", existingPupil.id().toString());
			assertThat(courses.findByNaturalKey(SchoolClass.CLS_10F, subject.id(), new SchoolYear(2098),
					CoursePeriod.FULL_YEAR)).isEmpty();

			final String retryCourseCall = courseCreationCall(6, subject.id(), gradingScale.id(), existingPupil.id());
			final HttpResponse<String> created = client.send(request(retryCourseCall, TOKEN, sessionId),
					HttpResponse.BodyHandlers.ofString());

			assertThat(created.statusCode()).isEqualTo(200);
			assertThat(created.body()).contains("CREATED", "McpHttp", "Duplicate");
			final int createdCourseId = courses
					.findByNaturalKey(SchoolClass.CLS_10F, subject.id(), new SchoolYear(2098), CoursePeriod.FULL_YEAR)
					.orElseThrow().id();
			assertThat(courses.findPupils(createdCourseId)).containsExactly(existingPupil);

			final String assignmentCall = pupilAssignmentCall(7, createdCourseId);
			final HttpResponse<String> assigned = client.send(request(assignmentCall, TOKEN, sessionId),
					HttpResponse.BodyHandlers.ofString());

			assertThat(assigned.statusCode()).isEqualTo(200);
			assertThat(assigned.body()).contains("UPDATED", "McpHttpAdded", "Pupil");
			final Pupil addedPupil = pupils.findActiveByExactName("McpHttpAdded", "Pupil").getFirst();
			assertThat(courses.findPupils(createdCourseId)).containsExactlyInAnyOrder(existingPupil, addedPupil);

			final HttpResponse<String> repeatedAssignment = client.send(
					request(pupilAssignmentCall(8, createdCourseId), TOKEN, sessionId),
					HttpResponse.BodyHandlers.ofString());

			assertThat(repeatedAssignment.statusCode()).isEqualTo(200);
			assertThat(repeatedAssignment.body()).contains("UNCHANGED", "McpHttpAdded", "Pupil");
			assertThat(pupils.findActiveByExactName("McpHttpAdded", "Pupil")).containsExactly(addedPupil);
		}
	}

	private static String pupilListCall(final int requestId, final String scope) {
		final String scopeArgument = scope == null ? "" : ",\"scope\":\"" + scope + "\"";
		return """
			{"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"list_pupils","arguments":{"query":"McpHttp"%s}}}
			""".formatted(
				requestId, scopeArgument).trim();
	}

	private static String pupilAssignmentCall(final int requestId, final int courseId) {
		return """
			{"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"assign_pupils_to_course","arguments":{"courseId":%d,"pupilDrafts":[{"entryKey":"row-added","name":"McpHttpAdded","surname":"Pupil"}]}}}
			""".formatted(
				requestId, courseId).trim();
	}

	private static String courseCreationCall(final int requestId, final int subjectId, final int gradingScaleId,
			final Integer reusedPupilId) {
		final String resolutions = reusedPupilId == null ? "" : """
			,"resolutions":[{"entryKey":"row-1","action":"REUSE","pupilId":%d}]
			""".formatted(reusedPupilId).trim();
		return """
			{"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"create_course_with_pupils","arguments":{"course":{"schoolClass":"CLS_10F","subjectId":%d,"calendarYear":2098,"coursePeriod":"FULL_YEAR","gradingScaleId":%d},"roster":[{"entryKey":"row-1","name":"McpHttp","surname":"Duplicate"}]%s}}}
			""".formatted(
				requestId, subjectId, gradingScaleId, resolutions).trim();
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
