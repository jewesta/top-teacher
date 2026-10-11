package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import de.westarps.topteacher.backend.backup.DatabaseBackupService;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

class PseudonymizedMcpToolsTests {

	private final PupilRepository pupils = mock(PupilRepository.class);
	private final CourseRepository courses = mock(CourseRepository.class);
	private final ExamRepository exams = mock(ExamRepository.class);
	private final GradingScaleRepository scales = mock(GradingScaleRepository.class);
	private final LevelOfExpectationsRepository levels = mock(LevelOfExpectationsRepository.class);
	private final PupilRosterConflictResolver conflicts = mock(PupilRosterConflictResolver.class);
	private final PupilUpdateConflictResolver renameConflicts = mock(PupilUpdateConflictResolver.class);
	private final PupilWriter pupilWriter = mock(PupilWriter.class);
	private final PupilMcpTools pupilTools = new PupilMcpTools(pupils, conflicts, renameConflicts, pupilWriter);
	private final Pupil pupil = new Pupil(42, "Hans", "Müller", Lifecycle.ACTIVE);
	private McpAccessContext.Scope access;

	@BeforeEach
	void authenticatePseudonymizedAccess() {
		access = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED);
	}

	@AfterEach
	void closeAccess() {
		access.close();
	}

	@Test
	void discoveryUsesInitialsAndIdsWithoutRealNameProbingAndRetainsCollisionContext() {
		final Pupil collision = new Pupil(43, "Heike", "Meier", Lifecycle.ACTIVE);
		when(pupils.findAll()).thenReturn(List.of(pupil, collision));
		when(pupils.findLatestSchoolClassByPupilId())
				.thenReturn(Map.of(42, SchoolClass.CLS_Q1, 43, SchoolClass.CLS_EF));

		assertThat(pupilTools.listPupils("Müller", null).pupils()).isEmpty();
		assertThat(pupilTools.listPupils("Hans", null).pupils()).isEmpty();
		assertThat(pupilTools.listPupils("MH", null).pupils()).hasSize(2).allSatisfy(view -> {
			assertThat(view.name()).isEqualTo("MH");
			assertThat(view.surname()).isEmpty();
		}).extracting(PupilMcpSchema.PupilView::latestSchoolClass).containsExactly("CLS_Q1", "CLS_EF");
		assertThat(pupilTools.listPupils("42", null).pupils()).singleElement()
				.satisfies(view -> assertThat(view.id()).isEqualTo(42));
		verifyNoInteractions(conflicts, renameConflicts, pupilWriter);
	}

	@Test
	void pupilCreationAndRenamingAreDeniedBeforeAnyLookupOrConflictResolution() {
		assertThatThrownBy(() -> pupilTools.createPupils(null,
				List.of(new PupilMcpSchema.PupilDraft("one", "Hans", "Müller")), null))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> pupilTools.updatePupil(null, 42, "Heinz", null, null))
				.isInstanceOf(SecurityException.class);
		verifyNoInteractions(pupils, conflicts, renameConflicts, pupilWriter);
	}

	@Test
	void courseAndExamRostersAndResultsPseudonymizeNamesButKeepUserComments() {
		when(courses.findById(3)).thenReturn(Optional.of(course()));
		when(courses.findPupils(3)).thenReturn(List.of(pupil));
		when(exams.findById(11)).thenReturn(Optional.of(exam()));
		when(exams.findPupils(11)).thenReturn(List.of(pupil));
		when(exams.hasPupil(11, 42)).thenReturn(true);
		when(pupils.findById(42)).thenReturn(Optional.of(pupil));
		when(levels.findRequirementsByExamId(11))
				.thenReturn(List.of(new LoeRequirement(31, 1, "Aufgabe", 3, false, 0)));
		when(levels.findRequirementResultsByExamAndPupil(11, 42))
				.thenReturn(List.of(new LoeRequirementResult(31, 42, 4, 0, "User-authored Hans comment")));

		final var courseRoster = new CourseMcpTools(courses, mock(SubjectRepository.class), scales)
				.listCoursePupils(3, null);
		assertThat(courseRoster.pupils()).singleElement().satisfies(view -> {
			assertThat(view.name()).isEqualTo("MH");
			assertThat(view.surname()).isEmpty();
			assertThat(view.id()).isEqualTo(42);
		});
		assertThat(new ExamMcpTools(courses, exams, levels).listExamPupils(11).pupils()).singleElement()
				.satisfies(view -> assertThat(view.name()).isEqualTo("MH"));
		final var result = new PupilResultMcpTools(exams, scales, levels, pupils).getPupilResult(11, 42);
		assertThat(result.pupil().name()).isEqualTo("MH");
		assertThat(result.pupil().surname()).isEmpty();
		assertThat(result.summary().effectivePoints()).isNull();
		assertThat(result.summary().gradingAvailable()).isFalse();
		assertThat(result.requirements()).singleElement()
				.satisfies(view -> assertThat(view.comment()).isEqualTo("User-authored Hans comment"));
	}

	@Test
	void examCreationAndMetadataChangesKeepAssignmentsAvailableAndPseudonymizeReturnedPupils() {
		when(courses.findById(3)).thenReturn(Optional.of(course()));
		when(courses.findPupils(3)).thenReturn(List.of(pupil));
		when(exams.save(any(Exam.class), any())).thenReturn(exam());
		when(exams.findById(11)).thenReturn(Optional.of(exam()));
		when(exams.findPupils(11)).thenReturn(List.of(pupil));
		final ExamWriteMcpTools tools = new ExamWriteMcpTools(courses, exams, scales, levels);

		assertThat(tools.createExam(3, "Exam", "2026-10-12", null, null, List.of(42)).pupils())
				.singleElement().satisfies(view -> assertThat(view.name()).isEqualTo("MH"));
		assertThat(tools.updateExam(11, "Updated exam", null).pupils()).singleElement().satisfies(view -> {
			assertThat(view.name()).isEqualTo("MH");
			assertThat(view.surname()).isEmpty();
		});
		verify(exams).save(any(Exam.class));
	}

	@Test
	void courseAssignmentUsesExistingIdsWithoutNameMatchingOrPupilCreation() {
		final CoursePupilAssignmentWriter writer = mock(CoursePupilAssignmentWriter.class);
		when(writer.assignExisting(3, List.of(42)))
				.thenReturn(new CoursePupilAssignmentWriter.AssignmentResult(course(), List.of(pupil), List.of()));
		final CoursePupilAssignmentMcpTools tools = new CoursePupilAssignmentMcpTools(courses, conflicts, writer);

		final var result = tools.assignPupilsToCourse(null, 3, null, null, List.of(42, 42));

		assertThat(result.assignedPupils()).singleElement().satisfies(view -> {
			assertThat(view.name()).isEqualTo("MH");
			assertThat(view.surname()).isEmpty();
		});
		verify(writer).assignExisting(3, List.of(42));
		assertThatThrownBy(() -> tools.assignPupilsToCourse(null, 3,
				List.of(new PupilMcpSchema.PupilDraft("one", "Hans", "Müller")), null, null))
				.isInstanceOf(SecurityException.class);
		verifyNoInteractions(courses, conflicts);
	}

	@Test
	void courseCreationAcceptsExistingIdsOrAnEmptyCourseAndRejectsNamedRosters() {
		final SubjectRepository subjects = mock(SubjectRepository.class);
		final CourseRosterWriter writer = mock(CourseRosterWriter.class);
		final CourseRosterMcpTools tools = new CourseRosterMcpTools(courses, subjects, scales, conflicts, writer);
		final var draft = new CourseRosterMcpTools.CourseDraft(SchoolClass.CLS_Q1, 2, 2026,
				CoursePeriod.FULL_YEAR, 9);
		assertThatThrownBy(() -> tools.createCourseWithPupils(null, draft,
				List.of(new PupilMcpSchema.PupilDraft("one", "Hans", "Müller")), null, null))
				.isInstanceOf(SecurityException.class);
		verifyNoInteractions(courses, subjects, scales, conflicts, writer);
		when(subjects.findById(2)).thenReturn(Optional.of(course().subject()));
		when(scales.findById(9)).thenReturn(Optional.of(new GradingScale(9, "Scale", 10, Lifecycle.ACTIVE)));
		when(writer.createWithExisting(any(Course.class), any()))
				.thenAnswer(invocation -> new CourseRosterWriter.CreatedCourseRoster(course(),
						invocation.<List<Integer>>getArgument(1).isEmpty() ? List.of() : List.of(pupil)));

		assertThat(tools.createCourseWithPupils(null, draft, null, null, List.of(42)).assignedPupils())
				.singleElement().satisfies(view -> assertThat(view.name()).isEqualTo("MH"));
		assertThat(tools.createCourseWithPupils(null, draft, null, null, null).assignedPupils()).isEmpty();
		verifyNoInteractions(conflicts);
	}

	@Test
	void examAssignmentsAreAllowedAndPseudonymizeBothNewAndExistingAssignments() {
		final ExamPupilWriter writer = mock(ExamPupilWriter.class);
		when(writer.assign(11, List.of(42))).thenReturn(new ExamPupilWriter.AssignmentResult(exam(),
				List.of(pupil), List.of(pupil)));

		final var result = new ExamPupilAssignmentMcpTools(writer).assignPupilsToExam(11, List.of(42));

		assertThat(result.assignedPupils()).singleElement()
				.satisfies(view -> assertThat(view.name()).isEqualTo("MH"));
		assertThat(result.alreadyAssignedPupils()).singleElement()
				.satisfies(view -> assertThat(view.surname()).isEmpty());
	}

	@Test
	void removalConfirmationsAndLockedPupilViewsNeverUseStoredNames() {
		when(courses.findById(3)).thenReturn(Optional.of(course()));
		when(courses.findPupils(3)).thenReturn(List.of(pupil));
		when(courses.findPupilRemovalLocks(3)).thenReturn(Map.of(42, "Exam assigned"));
		when(exams.findById(11)).thenReturn(Optional.of(exam()));
		when(exams.findPupils(11)).thenReturn(List.of(pupil));
		when(exams.findPupilRemovalLocks(11)).thenReturn(Map.of(42, "Results exist"));
		final McpSyncRequestContext context = mock(McpSyncRequestContext.class);
		when(context.elicitEnabled()).thenReturn(true);
		when(context.elicit(any(ElicitFormRequest.class)))
				.thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("action", "SKIP")));
		final CoursePupilRemovalWriter courseWriter = mock(CoursePupilRemovalWriter.class);
		final ExamPupilWriter examWriter = mock(ExamPupilWriter.class);

		final var courseRemoval = new CoursePupilRemovalMcpTools(courses, courseWriter)
				.removePupilsFromCourse(context, 3, List.of(42), null);
		final var examRemoval = new ExamPupilRemovalMcpTools(courses, exams, examWriter)
				.removePupilsFromExam(context, 11, List.of(42), null);

		assertThat(courseRemoval.lockedPupils()).singleElement()
				.satisfies(view -> assertThat(view.name()).isEqualTo("MH"));
		assertThat(examRemoval.lockedPupils()).singleElement()
				.satisfies(view -> assertThat(view.name()).isEqualTo("MH"));
		final ArgumentCaptor<ElicitFormRequest> requests = ArgumentCaptor.forClass(ElicitFormRequest.class);
		verify(context, org.mockito.Mockito.times(2)).elicit(requests.capture());
		assertThat(requests.getAllValues()).allSatisfy(request -> assertThat(request.message())
				.contains("MH", "ID 42").doesNotContain("Hans", "Müller"));
		verifyNoInteractions(courseWriter, examWriter);
	}

	@Test
	void removedPupilResponsesUsePseudonyms() {
		when(courses.findById(3)).thenReturn(Optional.of(course()));
		when(courses.findPupils(3)).thenReturn(List.of(pupil));
		when(exams.findById(11)).thenReturn(Optional.of(exam()));
		when(exams.findPupils(11)).thenReturn(List.of(pupil));
		final CoursePupilRemovalWriter courseWriter = mock(CoursePupilRemovalWriter.class);
		when(courseWriter.remove(3, List.of(42))).thenReturn(new CoursePupilRemovalWriter.RemovalResult(
				course(), List.of(pupil), List.of()));
		final ExamPupilWriter examWriter = mock(ExamPupilWriter.class);
		when(examWriter.remove(11, List.of(42)))
				.thenReturn(new ExamPupilWriter.RemovalResult(exam(), List.of(pupil), List.of()));

		assertThat(new CoursePupilRemovalMcpTools(courses, courseWriter)
				.removePupilsFromCourse(null, 3, List.of(42), null).removedPupils()).singleElement()
				.satisfies(view -> assertThat(view.name()).isEqualTo("MH"));
		assertThat(new ExamPupilRemovalMcpTools(courses, exams, examWriter)
				.removePupilsFromExam(null, 11, List.of(42), null).removedPupils()).singleElement()
				.satisfies(view -> assertThat(view.surname()).isEmpty());
	}

	@Test
	void serverSideBackupRemainsAvailableAndReturnsOnlyItsFileName() {
		final DatabaseBackupService backup = mock(DatabaseBackupService.class);
		when(backup.backUpNow()).thenReturn(Path.of("/tmp/private-backups/topteacher-db-20261011.zip"));

		assertThat(new DatabaseBackupMcpTools(backup).createDatabaseBackup().backups()).singleElement()
				.satisfies(view -> assertThat(view.fileName()).isEqualTo("topteacher-db-20261011.zip"));
	}

	private static Course course() {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 9);
	}

	private static Exam exam() {
		return new Exam(11, 3, "Exam", LocalDate.of(2026, 10, 12), null, 9);
	}
}
