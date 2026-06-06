package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
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

	@Test
	void savesUpdatesAndFindsExamsForCourse() {
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7A, Subject.ENGLISH,
				new SchoolYear(2030), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_8A, Subject.SPANISH,
				new SchoolYear(2030), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));

		final Exam saved = examRepository
				.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2030, 9, 17), 40));
		examRepository.save(new Exam(null, otherCourse.id(), "Andere Klausur", LocalDate.of(2030, 9, 18), 35));

		assertThat(saved.id()).isNotNull();
		assertThat(saved.maxPoints()).isEqualTo(40);
		assertThat(examRepository.findById(saved.id())).contains(saved);
		assertThat(examRepository.findByCourseId(course.id())).containsExactly(saved);

		final Exam updated = new Exam(saved.id(), course.id(), "1. Klassenarbeit", LocalDate.of(2030, 9, 24), 42);
		examRepository.save(updated);

		assertThat(examRepository.findById(saved.id())).contains(updated);
		assertThat(examRepository.findByCourseId(course.id())).containsExactly(updated);
	}

	@Test
	void rejectsChangedCourseId() {
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7B, Subject.ENGLISH,
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_8B, Subject.ENGLISH,
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		final Exam saved = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2031, 9, 17)));

		assertThatThrownBy(
				() -> examRepository.save(new Exam(saved.id(), otherCourse.id(), saved.title(), saved.date(),
						saved.maxPoints())))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Exam course can not be changed.");
	}

	@Test
	void rejectsNegativeMaxPoints() {
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7C, Subject.ENGLISH,
				new SchoolYear(2032), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));

		assertThatThrownBy(() -> new Exam(null, course.id(), "1. Klausur", LocalDate.of(2032, 9, 17), -1))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("maxPoints must not be negative");
	}
}
