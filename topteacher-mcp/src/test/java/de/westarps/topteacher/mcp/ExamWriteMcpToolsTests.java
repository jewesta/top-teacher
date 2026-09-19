package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.ExamWriteMcpTools.ExamWriteStatus;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.ExamNumber;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class ExamWriteMcpToolsTests {

	@Mock
	private CourseRepository courses;

	@Mock
	private ExamRepository exams;

	@Mock
	private GradingScaleRepository gradingScales;

	@Mock
	private LevelOfExpectationsRepository levelOfExpectations;

	private ExamWriteMcpTools tools;

	@BeforeEach
	void setUp() {
		tools = new ExamWriteMcpTools(courses, exams, gradingScales, levelOfExpectations);
	}

	@Test
	void createsMainExamWithCourseScaleAndAllActiveCoursePupilsByDefault() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil active = pupil(7, Lifecycle.ACTIVE);
		final Pupil archived = pupil(8, Lifecycle.INACTIVE);
		when(courses.findById(course.id())).thenReturn(Optional.of(course));
		when(courses.findPupils(course.id())).thenReturn(List.of(active, archived));
		when(exams.existsByCourseIdAndTitle(course.id(), "1. Klausur")).thenReturn(false);
		when(exams.save(any(Exam.class), anyCollection())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
		prepareResult(active);

		final var result = tools.createExam(course.id(), " 1. Klausur ", "2026-11-03", null, null, null);

		assertThat(result.status()).isEqualTo(ExamWriteStatus.CREATED);
		assertThat(result.exam().gradingScaleId()).isEqualTo(course.gradingScaleId());
		assertThat(result.pupils()).extracting(ExamMcpTools.PupilView::id).containsExactly(active.id());
		final ArgumentCaptor<Exam> exam = ArgumentCaptor.forClass(Exam.class);
		@SuppressWarnings("unchecked")
		final ArgumentCaptor<List<Integer>> pupilIds = ArgumentCaptor.forClass(List.class);
		verify(exams).save(exam.capture(), pupilIds.capture());
		assertThat(exam.getValue().title()).isEqualTo("1. Klausur");
		assertThat(exam.getValue().gradingScaleId()).isEqualTo(course.gradingScaleId());
		assertThat(pupilIds.getValue()).containsExactly(active.id());
	}

	@Test
	void makeupExamDefaultsToActiveCoursePupilsOutsideTheOriginalExam() {
		final Course course = course(Lifecycle.ACTIVE);
		final Pupil originalPupil = pupil(7, Lifecycle.ACTIVE);
		final Pupil makeupPupil = pupil(8, Lifecycle.ACTIVE);
		final Exam originalExam = new Exam(20, course.id(), "1. Klausur", LocalDate.of(2026, 10, 10), null,
				course.gradingScaleId());
		when(courses.findById(course.id())).thenReturn(Optional.of(course));
		when(courses.findPupils(course.id())).thenReturn(List.of(originalPupil, makeupPupil));
		when(exams.findById(originalExam.id())).thenReturn(Optional.of(originalExam));
		when(exams.findPupils(originalExam.id())).thenReturn(List.of(originalPupil));
		when(exams.save(any(Exam.class), anyCollection())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
		prepareResult(makeupPupil);

		final var result = tools.createExam(course.id(), "Nachschreibeklausur", "2026-10-12", null, originalExam.id(),
				null);

		assertThat(result.exam().originalExamId()).isEqualTo(originalExam.id());
		final ArgumentCaptor<Exam> exam = ArgumentCaptor.forClass(Exam.class);
		@SuppressWarnings("unchecked")
		final ArgumentCaptor<List<Integer>> pupilIds = ArgumentCaptor.forClass(List.class);
		verify(exams).save(exam.capture(), pupilIds.capture());
		assertThat(pupilIds.getValue()).containsExactly(makeupPupil.id());
	}

	@Test
	void acceptsAnActiveGradingScaleOverrideAndAnExplicitEmptyRoster() {
		final Course course = course(Lifecycle.ACTIVE);
		final GradingScale override = new GradingScale(12, "Short exam", 30, Lifecycle.ACTIVE);
		when(courses.findById(course.id())).thenReturn(Optional.of(course));
		when(gradingScales.findById(override.id())).thenReturn(Optional.of(override));
		when(exams.save(any(Exam.class), anyCollection())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
		prepareResult();

		final var result = tools.createExam(course.id(), "Kurztest", "2026-10-12", override.id(), null, List.of());

		assertThat(result.exam().gradingScaleId()).isEqualTo(override.id());
		assertThat(result.pupils()).isEmpty();
	}

	@Test
	void updatesOnlyTitleAndDateWhilePreservingImmutableRelationships() {
		final Course course = course(Lifecycle.ACTIVE);
		final Exam current = new Exam(11, course.id(), "Old", LocalDate.of(2026, 10, 10), 4, course.gradingScaleId());
		when(exams.findById(current.id())).thenReturn(Optional.of(current));
		when(courses.findById(course.id())).thenReturn(Optional.of(course));
		when(exams.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));
		prepareResult();

		final var result = tools.updateExam(current.id(), "New", "2026-10-12");

		assertThat(result.status()).isEqualTo(ExamWriteStatus.UPDATED);
		final ArgumentCaptor<Exam> updated = ArgumentCaptor.forClass(Exam.class);
		verify(exams).save(updated.capture());
		assertThat(updated.getValue()).isEqualTo(new Exam(current.id(), course.id(), "New", LocalDate.of(2026, 10, 12),
				current.originalExamId(), current.gradingScaleId()));
	}

	@Test
	void rejectsDuplicateTitlesBeforeWriting() {
		final Course course = course(Lifecycle.ACTIVE);
		when(courses.findById(course.id())).thenReturn(Optional.of(course));
		when(exams.existsByCourseIdAndTitle(course.id(), "Duplicate")).thenReturn(true);

		assertThatThrownBy(() -> tools.createExam(course.id(), "Duplicate", "2026-10-12", null, null, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
	}

	private void prepareResult(final Pupil... pupils) {
		when(exams.findNumberById(11)).thenReturn(Optional.of(new ExamNumber(1, false)));
		when(exams.findPupils(11)).thenReturn(List.of(pupils));
		when(levelOfExpectations.hasResultsForExam(11)).thenReturn(false);
	}

	private static Exam withId(final Exam exam) {
		return new Exam(11, exam.courseId(), exam.title(), exam.date(), exam.originalExamId(), exam.gradingScaleId());
	}

	private static Pupil pupil(final int id, final Lifecycle lifecycle) {
		return new Pupil(id, "Pupil", Integer.toString(id), lifecycle);
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 9);
	}
}
