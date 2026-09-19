package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.CourseRosterWriter.CreatedCourseRoster;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class CourseRosterWriterTests {

	@Mock
	private CourseRepository courses;

	@Mock
	private PupilRepository pupils;

	@Test
	void createsNewPupilsReusesActivePupilsAndAssignsEachIdentityOnce() {
		final Course draft = new Course(null, SchoolClass.CLS_8A, new Subject(3, "Englisch", Lifecycle.ACTIVE),
				new SchoolYear(2035), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 5);
		final Course createdCourse = new Course(99, draft.schoolClass(), draft.subject(), draft.schoolYear(),
				draft.coursePeriod(), draft.lifecycle(), draft.gradingScaleId());
		final Pupil existing = new Pupil(42, "Anna", "Müller", Lifecycle.ACTIVE);
		final Pupil createdPupil = new Pupil(77, "Ben", "Becker", Lifecycle.ACTIVE);
		when(courses.findByNaturalKey(any(), anyInt(), any(), any())).thenReturn(Optional.empty());
		when(courses.save(draft)).thenReturn(createdCourse);
		when(pupils.findById(42)).thenReturn(Optional.of(existing));
		when(pupils.save(new Pupil(null, "Ben", "Becker", Lifecycle.ACTIVE))).thenReturn(createdPupil);
		final CourseRosterWriter writer = new CourseRosterWriter(courses, pupils);

		final CreatedCourseRoster result = writer.create(draft, List.of(new ResolvedPupil(42, "Anna", "Müller"),
				new ResolvedPupil(42, "Anna", "Müller"), new ResolvedPupil(null, "Ben", "Becker")));

		assertThat(result.course()).isEqualTo(createdCourse);
		assertThat(result.pupils()).containsExactly(existing, createdPupil);
		verify(pupils, never()).save(existing);
		final InOrder writes = inOrder(courses, pupils);
		writes.verify(courses).save(draft);
		writes.verify(courses).assignPupil(99, 42);
		writes.verify(courses).assignPupil(99, 77);
	}

	@Test
	void rejectsArchivedPupilsBeforeCreatingTheCourse() {
		final Course draft = new Course(null, SchoolClass.CLS_8A, new Subject(3, "Englisch", Lifecycle.ACTIVE),
				new SchoolYear(2035), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 5);
		when(pupils.findById(42)).thenReturn(Optional.of(new Pupil(42, "Anna", "Müller", Lifecycle.INACTIVE)));
		final CourseRosterWriter writer = new CourseRosterWriter(courses, pupils);

		assertThatThrownBy(() -> writer.create(draft, List.of(new ResolvedPupil(42, "Anna", "Müller"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Archived pupil can not be newly assigned: 42");
		verify(courses, never()).save(any());
		verify(courses, never()).assignPupil(anyInt(), anyInt());
	}
}
