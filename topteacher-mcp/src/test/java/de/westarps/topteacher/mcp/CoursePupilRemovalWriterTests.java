package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class CoursePupilRemovalWriterTests {

	@Mock
	private CourseRepository courses;

	@Test
	void delegatesEveryRemovalToTheRepositoriesExistingIntegrityCheck() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil pupil = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(courses.findPupils(3)).thenReturn(List.of(pupil));

		final var result = new CoursePupilRemovalWriter(courses).remove(3, List.of(7, 7, 99));

		assertThat(result.removedPupils()).containsExactly(pupil);
		assertThat(result.notAssignedPupilIds()).containsExactly(99);
		verify(courses).removePupil(3, 7);
		verify(courses, never()).removePupil(3, 99);
	}

	@Test
	void rejectsAnArchivedCourseWithoutRemovingAnything() {
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.INACTIVE)));

		assertThatThrownBy(() -> new CoursePupilRemovalWriter(courses).remove(3, List.of(7)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Archived course");
		verify(courses, never()).removePupil(3, 7);
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 1);
	}
}
