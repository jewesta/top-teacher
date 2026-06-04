package de.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.topteacher.backend.repo.CourseRepository;
import de.topteacher.backend.repo.ExamRepository;
import de.topteacher.model.Course;
import de.topteacher.model.CoursePeriod;
import de.topteacher.model.Exam;
import de.topteacher.model.Lifecycle;
import de.topteacher.model.SchoolClass;
import de.topteacher.model.SchoolYear;
import de.topteacher.model.Subject;

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

		final Exam saved = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2030, 9, 17)));
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
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7B, Subject.ENGLISH,
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_8B, Subject.ENGLISH,
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		final Exam saved = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2031, 9, 17)));

		assertThatThrownBy(
				() -> examRepository.save(new Exam(saved.id(), otherCourse.id(), saved.title(), saved.date())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Exam course can not be changed.");
	}
}
