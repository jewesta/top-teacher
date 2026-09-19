package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class CoursePupilAssignmentWriterTests {

	@Mock
	private CourseRepository courses;

	@Mock
	private PupilRepository pupils;

	private CoursePupilAssignmentWriter writer;

	@BeforeEach
	void setUp() {
		writer = new CoursePupilAssignmentWriter(courses, pupils);
	}

	@Test
	void createsAndAssignsNewPupilsWhileLeavingExistingAssignmentsUntouched() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil existing = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		final Pupil created = new Pupil(8, "Grace", "Hopper", Lifecycle.ACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(pupils.findById(7)).thenReturn(Optional.of(existing));
		when(courses.findPupils(3)).thenReturn(List.of(existing));
		when(pupils.save(new Pupil(null, "Grace", "Hopper", Lifecycle.ACTIVE))).thenReturn(created);

		final var result = writer.assign(3,
				List.of(new ResolvedPupil(7, "Ada", "Lovelace"), new ResolvedPupil(null, "Grace", "Hopper")));

		assertThat(result.assignedPupils()).containsExactly(created);
		assertThat(result.alreadyAssignedPupils()).containsExactly(existing);
		verify(courses).assignPupil(3, 8);
		verify(courses, never()).assignPupil(3, 7);
	}

	@Test
	void rejectsAnArchivedPupilBeforeCreatingOrAssigningAnything() {
		final Pupil archived = new Pupil(7, "Ada", "Lovelace", Lifecycle.INACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.ACTIVE)));
		when(pupils.findById(7)).thenReturn(Optional.of(archived));

		assertThatThrownBy(() -> writer.assign(3,
				List.of(new ResolvedPupil(7, "Ada", "Lovelace"), new ResolvedPupil(null, "Grace", "Hopper"))))
						.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Archived pupil");
		verify(pupils, never()).save(new Pupil(null, "Grace", "Hopper", Lifecycle.ACTIVE));
		verify(courses, never()).assignPupil(3, 7);
	}

	@Test
	void rejectsAnArchivedCourseBeforeInspectingOrChangingPupils() {
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.INACTIVE)));

		assertThatThrownBy(() -> writer.assign(3, List.of(new ResolvedPupil(null, "Grace", "Hopper"))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Archived course");
		verify(courses, never()).findPupils(3);
		verifyNoInteractions(pupils);
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 1);
	}
}
