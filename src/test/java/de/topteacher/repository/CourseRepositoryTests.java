package de.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.topteacher.backend.repo.CourseRepository;
import de.topteacher.model.Course;
import de.topteacher.model.Half;
import de.topteacher.model.Lifecycle;
import de.topteacher.model.SchoolClass;
import de.topteacher.model.SchoolYear;
import de.topteacher.model.Subject;
import de.topteacher.model.Term;

@SpringBootTest
class CourseRepositoryTests {

	@Autowired
	private CourseRepository courseRepository;

	@Test
	void savesUpdatesAndArchivesCourses() {
		final Course saved = courseRepository.save(new Course(null, SchoolClass.CLS_5A, Subject.ENGLISH,
				new Term(new SchoolYear(2026), Half.FIRST), Lifecycle.ACTIVE));

		assertThat(saved.id()).isNotNull();
		assertThat(courseRepository.findById(saved.id())).contains(saved);

		final Course updated = new Course(saved.id(), SchoolClass.CLS_5A, Subject.SPANISH,
				new Term(new SchoolYear(2026), Half.SECOND), Lifecycle.ACTIVE);
		courseRepository.save(updated);

		assertThat(courseRepository.findById(saved.id())).contains(updated);

		courseRepository.archive(saved.id());

		assertThat(courseRepository.findById(saved.id()))
				.hasValueSatisfying(course -> assertThat(course.lifecycle()).isEqualTo(Lifecycle.INACTIVE));
	}
}
