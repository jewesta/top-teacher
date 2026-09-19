package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.mcp.ExamPupilRemovalMcpTools.ExamPupilRemovalStatus;
import de.westarps.topteacher.mcp.ExamPupilRemovalMcpTools.LockedPupilAction;
import de.westarps.topteacher.mcp.ExamPupilWriter.RemovalResult;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

@ExtendWith(MockitoExtension.class)
class ExamPupilRemovalMcpToolsTests {

	private static final String LOCK_REASON = "Results exist";

	@Mock
	private CourseRepository courses;

	@Mock
	private ExamRepository exams;

	@Mock
	private ExamPupilWriter writer;

	private ExamPupilRemovalMcpTools tools;

	@BeforeEach
	void setUp() {
		tools = new ExamPupilRemovalMcpTools(courses, exams, writer);
	}

	@Test
	void removesUnlockedPupilsWithoutResolution() {
		final Pupil removable = pupil(7);
		prepareExam(List.of(removable), Map.of());
		when(writer.remove(11, List.of(7))).thenReturn(new RemovalResult(exam(), List.of(removable), List.of()));

		final var result = tools.removePupilsFromExam(contextWithoutElicitation(), 11, List.of(7), null);

		assertThat(result.status()).isEqualTo(ExamPupilRemovalStatus.REMOVED);
		assertThat(result.removedPupils()).extracting(ExamMcpTools.PupilView::id).containsExactly(7);
	}

	@Test
	void lockedPupilsRequireResolutionBeforeAnyWrite() {
		final Pupil locked = pupil(7);
		final Pupil removable = pupil(8);
		prepareExam(List.of(locked, removable), Map.of(7, LOCK_REASON));

		final var result = tools.removePupilsFromExam(contextWithoutElicitation(), 11, List.of(7, 8), null);

		assertThat(result.status()).isEqualTo(ExamPupilRemovalStatus.NEEDS_RESOLUTION);
		assertThat(result.lockedPupils()).singleElement().satisfies(pupil -> {
			assertThat(pupil.id()).isEqualTo(7);
			assertThat(pupil.reason()).isEqualTo(LOCK_REASON);
			assertThat(pupil.allowedActions()).containsExactly("SKIP", "CANCEL");
		});
		verifyNoInteractions(writer);
	}

	@Test
	void skipKeepsLockedPupilsAndRemovesTheRemainder() {
		final Pupil locked = pupil(7);
		final Pupil removable = pupil(8);
		prepareExam(List.of(locked, removable), Map.of(7, LOCK_REASON));
		when(writer.remove(11, List.of(8))).thenReturn(new RemovalResult(exam(), List.of(removable), List.of()));

		final var result = tools.removePupilsFromExam(contextWithoutElicitation(), 11, List.of(7, 8),
				LockedPupilAction.SKIP);

		assertThat(result.status()).isEqualTo(ExamPupilRemovalStatus.REMOVED);
		assertThat(result.removedPupils()).extracting(ExamMcpTools.PupilView::id).containsExactly(8);
		verify(writer).remove(11, List.of(8));
	}

	@Test
	void cancelLeavesTheWholeBatchUntouched() {
		prepareExam(List.of(pupil(7), pupil(8)), Map.of(7, LOCK_REASON));

		final var result = tools.removePupilsFromExam(contextWithoutElicitation(), 11, List.of(7, 8),
				LockedPupilAction.CANCEL);

		assertThat(result.status()).isEqualTo(ExamPupilRemovalStatus.CANCELLED);
		verifyNoInteractions(writer);
	}

	@Test
	void nativeElicitationCanSkipLockedPupils() {
		final Pupil locked = pupil(7);
		final Pupil removable = pupil(8);
		prepareExam(List.of(locked, removable), Map.of(7, LOCK_REASON));
		final McpSyncRequestContext context = mock(McpSyncRequestContext.class);
		when(context.elicitEnabled()).thenReturn(true);
		when(context.elicit(any(ElicitFormRequest.class)))
				.thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("action", "SKIP")));
		when(writer.remove(11, List.of(8))).thenReturn(new RemovalResult(exam(), List.of(removable), List.of()));

		final var result = tools.removePupilsFromExam(context, 11, List.of(7, 8), null);

		assertThat(result.status()).isEqualTo(ExamPupilRemovalStatus.REMOVED);
		verify(writer).remove(11, List.of(8));
	}

	private void prepareExam(final List<Pupil> assignedPupils, final Map<Integer, String> removalLocks) {
		when(exams.findById(11)).thenReturn(Optional.of(exam()));
		when(courses.findById(3)).thenReturn(Optional.of(course()));
		when(exams.findPupils(11)).thenReturn(assignedPupils);
		when(exams.findPupilRemovalLocks(11)).thenReturn(removalLocks);
	}

	private static Exam exam() {
		return new Exam(11, 3, "Exam", LocalDate.of(2026, 10, 12), null, 9);
	}

	private static Pupil pupil(final int id) {
		return new Pupil(id, "Pupil", Integer.toString(id), Lifecycle.ACTIVE);
	}

	private static Course course() {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 9);
	}

	private static McpSyncRequestContext contextWithoutElicitation() {
		return mock(McpSyncRequestContext.class);
	}
}
