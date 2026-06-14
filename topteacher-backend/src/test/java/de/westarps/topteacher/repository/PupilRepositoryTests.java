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
class PupilRepositoryTests {

	@Autowired
	private PupilRepository pupilRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Test
	void savesUpdatesAndArchivesPupils() {
		final Pupil saved = pupilRepository.save(new Pupil(null, "Ada", "Lovelace", Lifecycle.ACTIVE));

		assertThat(saved.id()).isNotNull();
		assertThat(pupilRepository.findById(saved.id())).contains(saved);

		final Pupil updated = new Pupil(saved.id(), "Ada", "Byron", Lifecycle.ACTIVE);
		pupilRepository.save(updated);

		assertThat(pupilRepository.findById(saved.id())).contains(updated);

		pupilRepository.archive(saved.id());

		assertThat(pupilRepository.findById(saved.id()))
				.hasValueSatisfying(pupil -> assertThat(pupil.lifecycle()).isEqualTo(Lifecycle.INACTIVE));
	}

	@Test
	void findsLatestSchoolClassByPupilId() {
		final GradingScale gradingScale = gradingScaleRepository
				.save(new GradingScale(null, "Pupil Latest Class 100", 100, Lifecycle.ACTIVE));
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Doppel", "Name", Lifecycle.ACTIVE));
		final Course oldCourse = courseRepository.save(new Course(null, SchoolClass.CLS_5A, subject("Englisch"),
				new SchoolYear(2025), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Course latestCourse = courseRepository.save(new Course(null, SchoolClass.CLS_6A, subject("Spanisch"),
				new SchoolYear(2026), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));

		courseRepository.assignPupil(oldCourse.id(), pupil.id());
		courseRepository.assignPupil(latestCourse.id(), pupil.id());

		assertThat(pupilRepository.findLatestSchoolClassByPupilId()).containsEntry(pupil.id(), SchoolClass.CLS_6A);
	}

	private Subject subject(final String name) {
		return subjectRepository.findAll().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
				.orElseThrow();
	}
}
