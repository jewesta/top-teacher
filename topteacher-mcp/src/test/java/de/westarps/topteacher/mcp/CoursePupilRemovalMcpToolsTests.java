package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import de.westarps.topteacher.mcp.CoursePupilRemovalMcpTools.CoursePupilRemovalStatus;
import de.westarps.topteacher.mcp.CoursePupilRemovalMcpTools.LockedPupilAction;
import de.westarps.topteacher.mcp.CoursePupilRemovalWriter.RemovalResult;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

@ExtendWith(MockitoExtension.class)
class CoursePupilRemovalMcpToolsTests {

	private static final String LOCK_REASON = "Assigned to an exam";

	@Mock
	private CourseRepository courses;

	@Mock
	private CoursePupilRemovalWriter writer;

	private CoursePupilRemovalMcpTools tools;

	@BeforeEach
	void setUp() {
		tools = new CoursePupilRemovalMcpTools(courses, writer);
	}

	@Test
	void removesUnlockedPupilsWithoutResolution() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil removable = pupil(7, "Ada", "Lovelace");
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(courses.findPupils(3)).thenReturn(List.of(removable));
		when(courses.findPupilRemovalLocks(3)).thenReturn(Map.of());
		when(writer.remove(3, List.of(7))).thenReturn(new RemovalResult(course, List.of(removable), List.of()));

		final var result = tools.removePupilsFromCourse(contextWithoutElicitation(), 3, List.of(7), null);

		assertThat(result.status()).isEqualTo(CoursePupilRemovalStatus.REMOVED);
		assertThat(result.removedPupils()).singleElement().satisfies(pupil -> assertThat(pupil.id()).isEqualTo(7));
		assertThat(result.lockedPupils()).isEmpty();
	}

	@Test
	void lockedPupilsRequireResolutionBeforeAnyWrite() {
		final Pupil locked = pupil(7, "Ada", "Lovelace");
		final Pupil removable = pupil(8, "Grace", "Hopper");
		prepareCourse(List.of(locked, removable), Map.of(7, LOCK_REASON));

		final var result = tools.removePupilsFromCourse(contextWithoutElicitation(), 3, List.of(7, 8), null);

		assertThat(result.status()).isEqualTo(CoursePupilRemovalStatus.NEEDS_RESOLUTION);
		assertThat(result.lockedPupils()).singleElement().satisfies(pupil -> {
			assertThat(pupil.id()).isEqualTo(7);
			assertThat(pupil.reason()).isEqualTo(LOCK_REASON);
			assertThat(pupil.allowedActions()).containsExactly("SKIP", "CANCEL");
		});
		verifyNoInteractions(writer);
	}

	@Test
	void skipKeepsLockedPupilsAndRemovesTheRemainder() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil locked = pupil(7, "Ada", "Lovelace");
		final Pupil removable = pupil(8, "Grace", "Hopper");
		prepareCourse(List.of(locked, removable), Map.of(7, LOCK_REASON));
		when(writer.remove(3, List.of(8))).thenReturn(new RemovalResult(course, List.of(removable), List.of()));

		final var result = tools.removePupilsFromCourse(contextWithoutElicitation(), 3, List.of(7, 8),
				LockedPupilAction.SKIP);

		assertThat(result.status()).isEqualTo(CoursePupilRemovalStatus.REMOVED);
		assertThat(result.removedPupils()).extracting(CourseMcpTools.CoursePupilView::id).containsExactly(8);
		assertThat(result.lockedPupils()).extracting(CoursePupilRemovalMcpTools.LockedCoursePupilView::id)
				.containsExactly(7);
		verify(writer).remove(3, List.of(8));
	}

	@Test
	void cancelLeavesTheWholeBatchUntouched() {
		final Pupil locked = pupil(7, "Ada", "Lovelace");
		final Pupil removable = pupil(8, "Grace", "Hopper");
		prepareCourse(List.of(locked, removable), Map.of(7, LOCK_REASON));

		final var result = tools.removePupilsFromCourse(contextWithoutElicitation(), 3, List.of(7, 8),
				LockedPupilAction.CANCEL);

		assertThat(result.status()).isEqualTo(CoursePupilRemovalStatus.CANCELLED);
		verifyNoInteractions(writer);
	}

	@Test
	void skippingAnEntirelyLockedBatchIsUnchanged() {
		final Pupil locked = pupil(7, "Ada", "Lovelace");
		prepareCourse(List.of(locked), Map.of(7, LOCK_REASON));

		final var result = tools.removePupilsFromCourse(contextWithoutElicitation(), 3, List.of(7),
				LockedPupilAction.SKIP);

		assertThat(result.status()).isEqualTo(CoursePupilRemovalStatus.UNCHANGED);
		verifyNoInteractions(writer);
	}

	@Test
	void nativeElicitationCanSkipLockedPupils() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil locked = pupil(7, "Ada", "Lovelace");
		final Pupil removable = pupil(8, "Grace", "Hopper");
		prepareCourse(List.of(locked, removable), Map.of(7, LOCK_REASON));
		final McpSyncRequestContext context = mock(McpSyncRequestContext.class);
		when(context.elicitEnabled()).thenReturn(true);
		when(context.elicit(any(ElicitFormRequest.class)))
				.thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("action", "SKIP")));
		when(writer.remove(3, List.of(8))).thenReturn(new RemovalResult(course, List.of(removable), List.of()));

		final var result = tools.removePupilsFromCourse(context, 3, List.of(7, 8), null);

		assertThat(result.status()).isEqualTo(CoursePupilRemovalStatus.REMOVED);
		verify(writer).remove(3, List.of(8));
	}

	@Test
	void rejectsAnArchivedCourseBeforeInspectingAssignments() {
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.INACTIVE)));

		assertThatThrownBy(() -> tools.removePupilsFromCourse(contextWithoutElicitation(), 3, List.of(7), null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Archived course");
		verify(courses, never()).findPupils(3);
		verifyNoInteractions(writer);
	}

	private void prepareCourse(final List<Pupil> assignedPupils, final Map<Integer, String> removalLocks) {
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.ACTIVE)));
		when(courses.findPupils(3)).thenReturn(assignedPupils);
		when(courses.findPupilRemovalLocks(3)).thenReturn(removalLocks);
	}

	private static Pupil pupil(final int id, final String name, final String surname) {
		return new Pupil(id, name, surname, Lifecycle.ACTIVE);
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 1);
	}

	private static McpSyncRequestContext contextWithoutElicitation() {
		return mock(McpSyncRequestContext.class);
	}
}
