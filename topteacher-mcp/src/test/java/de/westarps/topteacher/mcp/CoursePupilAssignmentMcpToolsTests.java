package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.CoursePupilAssignmentMcpTools.CoursePupilAssignmentStatus;
import de.westarps.topteacher.mcp.CoursePupilAssignmentWriter.AssignmentResult;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictAction;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictResolution;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilDraft;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class CoursePupilAssignmentMcpToolsTests {

	@Mock
	private CourseRepository courses;

	@Mock
	private PupilRepository pupils;

	@Mock
	private CoursePupilAssignmentWriter writer;

	private CoursePupilAssignmentMcpTools tools;

	@BeforeEach
	void setUp() {
		tools = new CoursePupilAssignmentMcpTools(courses, new PupilRosterConflictResolver(pupils), writer);
	}

	@Test
	void createsAndAssignsANewPupilToAnExistingCourse() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil created = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(courses.findPupils(3)).thenReturn(List.of());
		when(pupils.findActiveByExactName("Ada", "Lovelace")).thenReturn(List.of());
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of());
		when(writer.assign(eq(3), anyList())).thenReturn(new AssignmentResult(course, List.of(created), List.of()));

		final var result = tools.assignPupilsToCourse(contextWithoutElicitation(), 3,
				List.of(new PupilDraft("row-1", "Ada", "Lovelace")), null);

		assertThat(result.status()).isEqualTo(CoursePupilAssignmentStatus.UPDATED);
		assertThat(result.assignedPupils()).singleElement().satisfies(pupil -> assertThat(pupil.id()).isEqualTo(7));
		verify(writer).assign(3, List.of(new CourseRosterWriter.ResolvedPupil(null, "Ada", "Lovelace")));
	}

	@Test
	void activeExactNameRequiresTheUsualConflictDecision() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil existing = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(courses.findPupils(3)).thenReturn(List.of());
		when(pupils.findActiveByExactName("Ada", "Lovelace")).thenReturn(List.of(existing));
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of(7, SchoolClass.CLS_Q1));

		final var result = tools.assignPupilsToCourse(contextWithoutElicitation(), 3,
				List.of(new PupilDraft("row-1", "Ada", "Lovelace")), null);

		assertThat(result.status()).isEqualTo(CoursePupilAssignmentStatus.NEEDS_RESOLUTION);
		assertThat(result.conflicts()).singleElement().satisfies(
				conflict -> assertThat(conflict.allowedActions()).containsExactly("REUSE", "CREATE", "SKIP"));
		verifyNoInteractions(writer);
	}

	@Test
	void aUniquelyNamedActivePupilAlreadyInTheCourseIsSatisfiedWithoutAWrite() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil existing = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(courses.findPupils(3)).thenReturn(List.of(existing));

		final var result = tools.assignPupilsToCourse(contextWithoutElicitation(), 3,
				List.of(new PupilDraft("row-1", "Ada", "Lovelace")), null);

		assertThat(result.status()).isEqualTo(CoursePupilAssignmentStatus.UNCHANGED);
		assertThat(result.alreadyAssignedPupils()).singleElement()
				.satisfies(pupil -> assertThat(pupil.id()).isEqualTo(7));
		verifyNoInteractions(writer);
		verify(pupils, never()).findActiveByExactName("Ada", "Lovelace");
	}

	@Test
	void allSkippedEntriesCancelWithoutAWrite() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil existing = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(courses.findPupils(3)).thenReturn(List.of());
		when(pupils.findActiveByExactName("Ada", "Lovelace")).thenReturn(List.of(existing));
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of());

		final var result = tools.assignPupilsToCourse(contextWithoutElicitation(), 3,
				List.of(new PupilDraft("row-1", "Ada", "Lovelace")),
				List.of(new PupilConflictResolution("row-1", PupilConflictAction.SKIP, null)));

		assertThat(result.status()).isEqualTo(CoursePupilAssignmentStatus.CANCELLED);
		assertThat(result.skippedEntryKeys()).containsExactly("row-1");
		verifyNoInteractions(writer);
	}

	@Test
	void rejectsAnArchivedCourseBeforeResolvingPupils() {
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.INACTIVE)));

		assertThatThrownBy(() -> tools.assignPupilsToCourse(contextWithoutElicitation(), 3,
				List.of(new PupilDraft("row-1", "Ada", "Lovelace")), null)).isInstanceOf(IllegalArgumentException.class)
						.hasMessageContaining("Archived course");
		verify(courses, never()).findPupils(3);
		verifyNoInteractions(pupils, writer);
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 1);
	}

	private static McpSyncRequestContext contextWithoutElicitation() {
		return mock(McpSyncRequestContext.class);
	}
}
