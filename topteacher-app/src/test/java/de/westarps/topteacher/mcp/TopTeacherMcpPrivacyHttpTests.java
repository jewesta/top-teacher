package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import de.westarps.topteacher.backend.mcp.McpAccessTokenService;
import de.westarps.topteacher.backend.mcp.McpAccessTokenService.CreatedToken;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"tt.mcp.enabled=true", "tt.mcp.token-file=", "server.servlet.context-path=/top-teacher",
		"spring.datasource.url=jdbc:h2:mem:mcp-privacy-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class TopTeacherMcpPrivacyHttpTests {

	private static final String INITIALIZE = """
		{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"privacy-test","version":"1"}}}
		""";

	@LocalServerPort
	private int port;

	@Autowired
	private McpAccessTokenService tokens;
	@Autowired
	private PupilRepository pupils;
	@Autowired
	private CourseRepository courses;
	@Autowired
	private ExamRepository exams;
	@Autowired
	private SubjectRepository subjects;
	@Autowired
	private GradingScaleRepository gradingScales;
	@Autowired
	private LevelOfExpectationsRepository levels;

	@Test
	void guardsEveryRosterResponseAndKeepsParallelFullAndRestrictedClientsIsolated() throws Exception {
		final Pupil hans = pupils.save(new Pupil(null, "Hans", "Müller", Lifecycle.ACTIVE));
		final Pupil hannah = pupils.save(new Pupil(null, "Hannah", "Meyer", Lifecycle.ACTIVE));
		final Course q1 = course(SchoolClass.CLS_Q1, 2086, hans);
		course(SchoolClass.CLS_EF, 2086, hannah);
		final Exam exam = exams.save(new Exam(null, q1.id(), "Privacy exam", LocalDate.of(2086, 5, 1), null, null),
				List.of(hans.id()));
		final CreatedToken full = tokens.create("Full privacy test", McpAccessLevel.FULL, null);
		final CreatedToken restricted = tokens.create("Restricted privacy test", McpAccessLevel.PSEUDONYMIZED, null);

		try (HttpClient client = HttpClient.newHttpClient()) {
			final String fullSession = initialize(client, full.credential());
			final String restrictedSession = initialize(client, restricted.credential());
			assertThat(call(client, full.credential(), fullSession, "list_pupils", "{\"query\":\"Hans\"}").body())
					.contains("Hans", "Müller");
			final String matches = call(client, restricted.credential(), restrictedSession, "list_pupils",
					"{\"query\":\"MH\"}").body();
			assertGuarded(matches);
			assertThat(matches).contains("MH", "Q1", "EF", hans.id().toString(), hannah.id().toString());
			assertThat(call(client, restricted.credential(), restrictedSession, "list_pupils",
					"{\"query\":\"Hans\"}").body()).doesNotContain("MH", "Hans", "Müller");

			for (final String tool : List.of("list_course_pupils", "list_exam_pupils", "get_pupil_result", "update_exam")) {
				final String arguments = switch (tool) {
				case "list_course_pupils" -> "{\"courseId\":" + q1.id() + "}";
				case "get_pupil_result" -> "{\"examId\":" + exam.id() + ",\"pupilId\":" + hans.id() + "}";
				case "update_exam" -> "{\"examId\":" + exam.id() + ",\"title\":\"Changed privacy exam\",\"expectedRevision\":"
						+ levels.designRevision(exam.id()) + ",\"requestId\":\"privacy-metadata\"}";
				default -> "{\"examId\":" + exam.id() + "}";
				};
				final String result = call(client, restricted.credential(), restrictedSession, tool, arguments).body();
				assertGuarded(result);
				assertThat(result).contains("MH").doesNotContain("\"isError\":true");
				assertThat(result).contains("displayName", "INITIALS").doesNotContain("\"name\":", "\"surname\":");
			}

			for (int iteration = 0; iteration < 4; iteration++) {
				final var named = CompletableFuture.supplyAsync(() -> uncheckedCall(client, full.credential(), fullSession,
						"list_course_pupils", "{\"courseId\":" + q1.id() + "}"));
				final var masked = CompletableFuture.supplyAsync(() -> uncheckedCall(client, restricted.credential(),
						restrictedSession, "list_course_pupils", "{\"courseId\":" + q1.id() + "}"));
				assertThat(named.get()).contains("Hans", "Müller");
				assertGuarded(masked.get());
			}
		}
	}

	@Test
	void deniesIdentityChangesButAllowsIdAssignmentsAndEmptyCourseCreation() throws Exception {
		final Pupil pupil = pupils.save(new Pupil(null, "HiddenGiven", "HiddenFamily", Lifecycle.ACTIVE));
		final Course course = course(SchoolClass.CLS_10F, 2087, pupil);
		final Exam exam = exams.save(new Exam(null, course.id(), "Assignment exam", LocalDate.of(2087, 5, 1), null, null),
				List.of());
		final CreatedToken restricted = tokens.create("Restricted writes", McpAccessLevel.PSEUDONYMIZED, null);
		try (HttpClient client = HttpClient.newHttpClient()) {
			final String session = initialize(client, restricted.credential());
			final String creation = call(client, restricted.credential(), session, "create_pupils",
					"{\"pupilDrafts\":[{\"entryKey\":\"x\",\"name\":\"ForbiddenGiven\",\"surname\":\"ForbiddenFamily\"}]}").body();
			assertThat(creation).contains("\"isError\":true");
			assertThat(pupils.findActiveByExactName("ForbiddenGiven", "ForbiddenFamily")).isEmpty();
			final String rename = call(client, restricted.credential(), session, "update_pupil",
					"{\"pupilId\":" + pupil.id() + ",\"name\":\"ForbiddenRename\"}").body();
			assertThat(rename).contains("\"isError\":true").doesNotContain("HiddenGiven", "HiddenFamily");
			assertThat(pupils.findById(pupil.id()).orElseThrow()).isEqualTo(pupil);

			final String assigned = call(client, restricted.credential(), session, "assign_pupils_to_exam",
					"{\"examId\":" + exam.id() + ",\"pupilIds\":[" + pupil.id() + "]}").body();
			assertThat(assigned).contains("ASSIGNED", "HH").doesNotContain("HiddenGiven", "HiddenFamily");
			assertThat(exams.findPupils(exam.id())).containsExactly(pupil);

			final String createdCourse = call(client, restricted.credential(), session, "create_course_with_pupils",
					"{\"course\":{\"schoolClass\":\"CLS_10E\",\"subjectId\":" + subjects.findActive().getFirst().id()
					+ ",\"calendarYear\":2088,\"coursePeriod\":\"FULL_YEAR\",\"gradingScaleId\":"
					+ gradingScales.findActive().getFirst().id() + "},\"pupilIds\":[]}").body();
			assertThat(createdCourse).contains("CREATED").doesNotContain("\"isError\":true");
			final int createdId = courses.findByNaturalKey(SchoolClass.CLS_10E, subjects.findActive().getFirst().id(),
					new SchoolYear(2088), CoursePeriod.FULL_YEAR).orElseThrow().id();
			final String courseAssignment = call(client, restricted.credential(), session, "assign_pupils_to_course",
					"{\"courseId\":" + createdId + ",\"pupilIds\":[" + pupil.id() + "]}").body();
			assertThat(courseAssignment).contains("HH").doesNotContain("HiddenGiven", "HiddenFamily", "\"isError\":true");
			assertThat(courses.findPupils(createdId)).containsExactly(pupil);
		}
	}

	@Test
	void rechecksRevocationAndPermissionsAndPreventsSessionReplayAcrossTokens() throws Exception {
		final CreatedToken full = tokens.create("Mutable full", McpAccessLevel.FULL, null);
		final CreatedToken restricted = tokens.create("Other restricted", McpAccessLevel.PSEUDONYMIZED, null);
		try (HttpClient client = HttpClient.newHttpClient()) {
			final String fullSession = initialize(client, full.credential());
			assertThat(call(client, restricted.credential(), fullSession, "list_pupils", "{}").statusCode()).isEqualTo(404);
			final HttpRequest replay = HttpRequest.newBuilder().uri(endpoint())
					.header("Accept", "text/event-stream").header("Authorization", "Bearer " + restricted.credential())
					.header("Mcp-Session-Id", fullSession).header("Last-Event-ID", "old-event").GET().build();
			assertThat(client.send(replay, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);

			tokens.update(full.metadata().id(), "Downgraded", McpAccessLevel.PSEUDONYMIZED, null);
			assertThat(call(client, full.credential(), fullSession, "list_pupils", "{}").statusCode()).isEqualTo(404);
			final String newSession = initialize(client, full.credential());
			assertThat(call(client, full.credential(), newSession, "list_pupils", "{}").statusCode()).isEqualTo(200);
			tokens.revoke(full.metadata().id());
			assertThat(call(client, full.credential(), newSession, "list_pupils", "{}").statusCode()).isEqualTo(401);

		}
	}

	private Course course(final SchoolClass schoolClass, final int year, final Pupil pupil) {
		final Course course = courses.save(new Course(null, schoolClass, subjects.findActive().getFirst(),
				new SchoolYear(year), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScales.findActive().getFirst().id()));
		courses.assignPupil(course.id(), pupil.id());
		return course;
	}

	private String initialize(final HttpClient client, final String token) throws Exception {
		final HttpResponse<String> initialized = client.send(request(INITIALIZE, token, null), HttpResponse.BodyHandlers.ofString());
		assertThat(initialized.statusCode()).isEqualTo(200);
		assertThat(initialized.body()).contains("exam-first", "get_mcp_context", "get_markdown_guide",
				"displayName", "identityRepresentation", "FULL_NAME", "INITIALS", "name and surname are omitted",
				"stable IDs", "not automatically name-redacted")
				.doesNotContain("pupil name fields contain", "surname is empty");
		final String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
		final HttpResponse<String> notification = client.send(request("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", token, session),
				HttpResponse.BodyHandlers.ofString());
		assertThat(notification.statusCode()).isIn(200, 202);
		return session;
	}

	private HttpResponse<String> call(final HttpClient client, final String token, final String session, final String tool,
			final String arguments) throws Exception {
		return client.send(request("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\""
				+ tool + "\",\"arguments\":" + arguments + "}}", token, session), HttpResponse.BodyHandlers.ofString());
	}

	private String uncheckedCall(final HttpClient client, final String token, final String session, final String tool,
			final String arguments) {
		try {
			return call(client, token, session, tool, arguments).body();
		} catch (final Exception failure) {
			throw new IllegalStateException(failure);
		}
	}

	private HttpRequest request(final String body, final String token, final String session) {
		final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(endpoint())
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
				.header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.ofString(body));
		if (session != null) {
			builder.header("Mcp-Session-Id", session);
		}
		return builder.build();
	}

	private URI endpoint() {
		return URI.create("http://127.0.0.1:" + port + "/top-teacher/mcp");
	}

	private static void assertGuarded(final String body) {
		assertThat(body).doesNotContain("Hans", "Hannah", "Müller", "Meyer");
	}
}
