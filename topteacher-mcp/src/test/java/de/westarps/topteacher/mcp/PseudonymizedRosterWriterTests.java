package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class PseudonymizedRosterWriterTests {

	@Mock
	private CourseRepository courses;

	@Mock
	private PupilRepository pupils;

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
	void idAssignmentsDeduplicateAndDoNotMatchNamesOrCreatePupils() {
		final Pupil alreadyAssigned = new Pupil(42, "Hans", "Müller", Lifecycle.ACTIVE);
		final Pupil added = new Pupil(43, "Hans", "Müller", Lifecycle.ACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course(3)));
		when(courses.findPupils(3)).thenReturn(List.of(alreadyAssigned));
		when(pupils.findById(42)).thenReturn(Optional.of(alreadyAssigned));
		when(pupils.findById(43)).thenReturn(Optional.of(added));

		final var result = new CoursePupilAssignmentWriter(courses, pupils)
				.assignExisting(3, List.of(42, 43, 43));

		assertThat(result.assignedPupils()).containsExactly(added);
		assertThat(result.alreadyAssignedPupils()).containsExactly(alreadyAssigned);
		verify(courses).assignPupil(3, 43);
		verify(courses, never()).assignPupil(3, 42);
		verify(pupils, never()).findActiveByExactName(any(), any());
		verify(pupils, never()).save(any());
	}

	@Test
	void invalidLaterIdsAreRejectedBeforeAnyAssignment() {
		when(courses.findById(3)).thenReturn(Optional.of(course(3)));
		when(pupils.findById(42)).thenReturn(Optional.of(new Pupil(42, "Hans", "Müller", Lifecycle.ACTIVE)));
		when(pupils.findById(43)).thenReturn(Optional.of(new Pupil(43, "Heike", "Meier", Lifecycle.INACTIVE)));

		assertThatThrownBy(() -> new CoursePupilAssignmentWriter(courses, pupils)
				.assignExisting(3, List.of(42, 43))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Archived pupil can not be newly assigned: 43");

		verify(courses, never()).assignPupil(anyInt(), anyInt());
		verify(pupils, never()).save(any());
	}

	@Test
	void courseCreationAssignsExistingIdsOnceWithoutCreatingPupilRecords() {
		final Course draft = course(null);
		final Course created = course(3);
		final Pupil existing = new Pupil(42, "Hans", "Müller", Lifecycle.ACTIVE);
		when(pupils.findById(42)).thenReturn(Optional.of(existing));
		when(courses.save(draft)).thenReturn(created);

		final var result = new CourseRosterWriter(courses, pupils).createWithExisting(draft, List.of(42, 42));

		assertThat(result.course()).isEqualTo(created);
		assertThat(result.pupils()).containsExactly(existing);
		verify(courses).assignPupil(3, 42);
		verify(pupils, never()).save(any());
		verify(pupils, never()).findActiveByExactName(any(), any());
	}

	@Test
	void invalidRosterIdsAreRejectedBeforeTheCourseIsCreated() {
		when(pupils.findById(42)).thenReturn(Optional.of(new Pupil(42, "Hans", "Müller", Lifecycle.INACTIVE)));

		assertThatThrownBy(() -> new CourseRosterWriter(courses, pupils)
				.createWithExisting(course(null), List.of(42))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Archived pupil can not be newly assigned: 42");

		verifyNoInteractions(courses);
		verify(pupils, never()).save(any());
	}

	@Test
	void legacyWritersAndResolversCannotBypassRestrictedIdentityPermissions() {
		final var resolved = List.of(new CourseRosterWriter.ResolvedPupil(null, "Hans", "Müller"));
		assertThatThrownBy(() -> new CourseRosterWriter(courses, pupils).create(course(null), resolved))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> new CoursePupilAssignmentWriter(courses, pupils).assign(3, resolved))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> new PupilWriter(pupils).create(resolved)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> new PupilWriter(pupils).updateName(42, "Heinz", "Müller", false))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> new PupilRosterConflictResolver(pupils).resolve(null,
				List.of(new PupilMcpSchema.PupilDraft("one", "Hans", "Müller")), null))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> new PupilUpdateConflictResolver(pupils).resolve(null,
				new Pupil(42, "Hans", "Müller", Lifecycle.ACTIVE), "Heinz", "Müller", null))
				.isInstanceOf(SecurityException.class);
		verifyNoInteractions(courses, pupils);
	}

	private static Course course(final Integer id) {
		return new Course(id, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 9);
	}
}
