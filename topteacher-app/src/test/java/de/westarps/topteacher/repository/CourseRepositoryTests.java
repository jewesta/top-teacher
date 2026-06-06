package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@SpringBootTest
class CourseRepositoryTests {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private PupilRepository pupilRepository;

	@Test
	void savesUpdatesAndArchivesCourses() {
		final Course saved = courseRepository.save(new Course(null, SchoolClass.CLS_5A, Subject.ENGLISH,
				new SchoolYear(2026), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));

		assertThat(saved.id()).isNotNull();
		assertThat(courseRepository.findById(saved.id())).contains(saved);

		final Course updated = new Course(saved.id(), SchoolClass.CLS_5A, Subject.SPANISH, new SchoolYear(2026),
				CoursePeriod.FIRST_HALF, Lifecycle.ACTIVE);
		courseRepository.save(updated);

		assertThat(courseRepository.findById(saved.id())).contains(updated);

		courseRepository.archive(saved.id());

		assertThat(courseRepository.findById(saved.id()))
				.hasValueSatisfying(course -> assertThat(course.lifecycle()).isEqualTo(Lifecycle.INACTIVE));
	}

	@Test
	void assignsFindsAndRemovesPupilsFromCourse() {
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_6A, Subject.ENGLISH,
				new SchoolYear(2027), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		final Pupil ada = pupilRepository.save(new Pupil(null, "Ada", "Lovelace", Lifecycle.ACTIVE));
		final Pupil grace = pupilRepository.save(new Pupil(null, "Grace", "Hopper", Lifecycle.ACTIVE));
		final Pupil archived = pupilRepository.save(new Pupil(null, "Inactive", "Pupil", Lifecycle.INACTIVE));

		assertThat(courseRepository.findAssignablePupils(course.id())).extracting(Pupil::id)
				.contains(ada.id(), grace.id()).doesNotContain(archived.id());

		courseRepository.assignPupil(course.id(), ada.id());
		courseRepository.assignPupil(course.id(), ada.id());

		assertThat(courseRepository.findPupils(course.id())).containsExactly(ada);
		assertThat(courseRepository.findAssignablePupils(course.id())).extracting(Pupil::id).doesNotContain(ada.id())
				.contains(grace.id());

		courseRepository.removePupil(course.id(), ada.id());

		assertThat(courseRepository.findPupils(course.id())).isEmpty();
		assertThat(courseRepository.findAssignablePupils(course.id())).extracting(Pupil::id).contains(ada.id());
	}

	@Test
	void findsOnlyActiveCourses() {
		final Course active = courseRepository.save(new Course(null, SchoolClass.CLS_9A, Subject.ENGLISH,
				new SchoolYear(2032), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		final Course inactive = courseRepository.save(new Course(null, SchoolClass.CLS_9A, Subject.SPANISH,
				new SchoolYear(2032), CoursePeriod.FULL_YEAR, Lifecycle.INACTIVE));

		assertThat(courseRepository.findActive()).extracting(Course::id).contains(active.id())
				.doesNotContain(inactive.id());
	}
}
