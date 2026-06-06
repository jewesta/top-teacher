package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@SpringBootTest
class ExamRepositoryTests {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Test
	void savesUpdatesAndFindsExamsForCourse() {
		final GradingScale gradingScale = createGradingScale("Exam Repo 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7A, Subject.ENGLISH,
				new SchoolYear(2030), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_8A, Subject.SPANISH,
				new SchoolYear(2030), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));

		final Exam saved = examRepository
				.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2030, 9, 17)));
		examRepository.save(new Exam(null, otherCourse.id(), "Andere Klausur", LocalDate.of(2030, 9, 18)));

		assertThat(saved.id()).isNotNull();
		assertThat(examRepository.findById(saved.id())).contains(saved);
		assertThat(examRepository.findByCourseId(course.id())).containsExactly(saved);

		final Exam updated = new Exam(saved.id(), course.id(), "1. Klassenarbeit", LocalDate.of(2030, 9, 24));
		examRepository.save(updated);

		assertThat(examRepository.findById(saved.id())).contains(updated);
		assertThat(examRepository.findByCourseId(course.id())).containsExactly(updated);
	}

	@Test
	void rejectsChangedCourseId() {
		final GradingScale gradingScale = createGradingScale("Exam Course Guard 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7B, Subject.ENGLISH,
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_8B, Subject.ENGLISH,
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Exam saved = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2031, 9, 17)));

		assertThatThrownBy(
				() -> examRepository.save(new Exam(saved.id(), otherCourse.id(), saved.title(), saved.date())))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Exam course can not be changed.");
	}

	private GradingScale createGradingScale(final String name) {
		return gradingScaleRepository.save(new GradingScale(null, name, 100, Lifecycle.ACTIVE));
	}
}
