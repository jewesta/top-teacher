package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.GradingScale;
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
	private GradingScaleRepository gradingScaleRepository;

	@Autowired
	private PupilRepository pupilRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Test
	void savesUpdatesAndArchivesCourses() {
		final GradingScale gradingScale = createGradingScale("Course Repo 100");
		final GradingScale updatedGradingScale = createGradingScale("Course Repo 150", 150);
		final Course saved = courseRepository.save(new Course(null, SchoolClass.CLS_5A, subject("Englisch"),
				new SchoolYear(2026), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));

		assertThat(saved.id()).isNotNull();
		assertThat(courseRepository.findById(saved.id())).contains(saved);

		final Course updated = new Course(saved.id(), SchoolClass.CLS_5A, subject("Spanisch"), new SchoolYear(2026),
				CoursePeriod.FIRST_HALF, Lifecycle.ACTIVE, updatedGradingScale.id());
		courseRepository.save(updated);

		assertThat(courseRepository.findById(saved.id())).contains(updated);

		courseRepository.archive(saved.id());

		assertThat(courseRepository.findById(saved.id()))
				.hasValueSatisfying(course -> assertThat(course.lifecycle()).isEqualTo(Lifecycle.INACTIVE));
	}

	@Test
	void assignsFindsAndRemovesPupilsFromCourse() {
		final GradingScale gradingScale = createGradingScale("Course Assignment 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_6A, subject("Englisch"),
				new SchoolYear(2027), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
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
	void replacesPupilsFromAnotherCourse() {
		final GradingScale gradingScale = createGradingScale("Course Replace Pupils 100");
		final Course sourceCourse = courseRepository.save(new Course(null, SchoolClass.CLS_7A, subject("Englisch"),
				new SchoolYear(2028), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Course targetCourse = courseRepository.save(new Course(null, SchoolClass.CLS_7B, subject("Spanisch"),
				new SchoolYear(2028), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Pupil sourcePupil = pupilRepository.save(new Pupil(null, "Quelle", "Eins", Lifecycle.ACTIVE));
		final Pupil otherSourcePupil = pupilRepository.save(new Pupil(null, "Quelle", "Zwei", Lifecycle.ACTIVE));
		final Pupil replacedPupil = pupilRepository.save(new Pupil(null, "Alt", "Zuordnung", Lifecycle.ACTIVE));

		courseRepository.assignPupil(sourceCourse.id(), sourcePupil.id());
		courseRepository.assignPupil(sourceCourse.id(), otherSourcePupil.id());
		courseRepository.assignPupil(targetCourse.id(), replacedPupil.id());

		courseRepository.replacePupilsFromCourse(targetCourse.id(), sourceCourse.id());

		assertThat(courseRepository.findPupils(targetCourse.id())).extracting(Pupil::id)
				.containsExactlyInAnyOrder(sourcePupil.id(), otherSourcePupil.id());
		assertThat(courseRepository.findPupils(sourceCourse.id())).extracting(Pupil::id)
				.containsExactlyInAnyOrder(sourcePupil.id(), otherSourcePupil.id());
	}

	@Test
	void findsOnlyActiveCourses() {
		final GradingScale gradingScale = createGradingScale("Course Active 100");
		final Course active = courseRepository.save(new Course(null, SchoolClass.CLS_9A, subject("Englisch"),
				new SchoolYear(2032), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Course inactive = courseRepository.save(new Course(null, SchoolClass.CLS_9A, subject("Spanisch"),
				new SchoolYear(2032), CoursePeriod.FULL_YEAR, Lifecycle.INACTIVE, gradingScale.id()));

		assertThat(courseRepository.findActive()).extracting(Course::id).contains(active.id())
				.doesNotContain(inactive.id());
	}

	private GradingScale createGradingScale(final String name) {
		return createGradingScale(name, 100);
	}

	private GradingScale createGradingScale(final String name, final int maxPoints) {
		return gradingScaleRepository.save(new GradingScale(null, name, maxPoints, Lifecycle.ACTIVE));
	}

	private Subject subject(final String name) {
		return subjectRepository.findAll().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
				.orElseThrow();
	}
}
