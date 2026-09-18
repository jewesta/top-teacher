package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class ExamPupilWriterTests {

	@Mock
	private CourseRepository courses;

	@Mock
	private ExamRepository exams;

	private ExamPupilWriter writer;

	@BeforeEach
	void setUp() {
		writer = new ExamPupilWriter(courses, exams);
	}

	@Test
	void assignsNewPupilsAndReportsAlreadyAssignedPupils() {
		final Pupil alreadyAssigned = pupil(7, Lifecycle.ACTIVE);
		final Pupil newlyAssigned = pupil(8, Lifecycle.ACTIVE);
		prepareActiveExam();
		when(courses.findPupils(3)).thenReturn(List.of(alreadyAssigned, newlyAssigned));
		when(exams.findPupils(11)).thenReturn(List.of(alreadyAssigned));

		final var result = writer.assign(11, List.of(7, 8, 8));

		assertThat(result.assignedPupils()).containsExactly(newlyAssigned);
		assertThat(result.alreadyAssignedPupils()).containsExactly(alreadyAssigned);
		verify(exams).assignPupil(11, 8);
		verify(exams, never()).assignPupil(11, 7);
	}

	@Test
	void rejectsArchivedOrOutsidePupilsBeforeWriting() {
		final Pupil active = pupil(7, Lifecycle.ACTIVE);
		final Pupil archived = pupil(8, Lifecycle.INACTIVE);
		prepareActiveExam();
		when(courses.findPupils(3)).thenReturn(List.of(active, archived));

		assertThatThrownBy(() -> writer.assign(11, List.of(7, 8, 99))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("active and assigned to course").hasMessageContaining("8", "99");
		verify(exams, never()).assignPupil(11, 7);
	}

	@Test
	void removesAssignedPupilsAndReportsAbsentIds() {
		final Pupil assigned = pupil(7, Lifecycle.INACTIVE);
		prepareActiveExam();
		when(exams.findPupils(11)).thenReturn(List.of(assigned));

		final var result = writer.remove(11, List.of(7, 7, 99));

		assertThat(result.removedPupils()).containsExactly(assigned);
		assertThat(result.notAssignedPupilIds()).containsExactly(99);
		verify(exams).removePupil(11, 7);
		verify(exams, never()).removePupil(11, 99);
	}

	@Test
	void rejectsChangesForAnExamInAnArchivedCourse() {
		when(exams.findById(11)).thenReturn(Optional.of(exam()));
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.INACTIVE)));

		assertThatThrownBy(() -> writer.assign(11, List.of(7))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Archived course exam");
		verify(exams, never()).assignPupil(11, 7);
	}

	private void prepareActiveExam() {
		when(exams.findById(11)).thenReturn(Optional.of(exam()));
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.ACTIVE)));
	}

	private static Exam exam() {
		return new Exam(11, 3, "Exam", LocalDate.of(2026, 10, 12), null, 9);
	}

	private static Pupil pupil(final int id, final Lifecycle lifecycle) {
		return new Pupil(id, "Pupil", Integer.toString(id), lifecycle);
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 9);
	}
}
