package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
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
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@SpringBootTest
class ExamRepositoryTests {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Autowired
	private LevelOfExpectationsRepository levelOfExpectationsRepository;

	@Autowired
	private PupilRepository pupilRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Test
	void savesUpdatesAndFindsExamsForCourse() {
		final GradingScale gradingScale = createGradingScale("Exam Repo 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7A, subject("Englisch"),
				new SchoolYear(2030), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_8A, subject("Spanisch"),
				new SchoolYear(2030), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));

		final Exam saved = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2030, 9, 17)));
		examRepository.save(new Exam(null, otherCourse.id(), "Andere Klausur", LocalDate.of(2030, 9, 18)));

		assertThat(saved.id()).isNotNull();
		assertThat(examRepository.findById(saved.id())).contains(saved);
		assertThat(examRepository.findByCourseId(course.id())).containsExactly(saved);

		final Exam updated = new Exam(saved.id(), course.id(), "1. Klassenarbeit", LocalDate.of(2030, 9, 24));
		examRepository.save(updated);

		assertThat(examRepository.findById(saved.id())).contains(updated);
		assertThat(examRepository.findByCourseId(course.id())).containsExactly(updated);
		assertThat(examRepository.existsByCourseIdAndTitle(course.id(), updated.title())).isTrue();
		assertThat(examRepository.existsByCourseIdAndTitle(otherCourse.id(), updated.title())).isFalse();
	}

	@Test
	void rejectsChangedCourseId() {
		final GradingScale gradingScale = createGradingScale("Exam Course Guard 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7B, subject("Englisch"),
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_8B, subject("Englisch"),
				new SchoolYear(2031), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Exam saved = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2031, 9, 17)));

		assertThatThrownBy(
				() -> examRepository.save(new Exam(saved.id(), otherCourse.id(), saved.title(), saved.date())))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Exam course can not be changed.");
	}

	@Test
	void derivesNumbersWithinCourseAndUsesOriginalNumberForMakeupExams() {
		final GradingScale gradingScale = createGradingScale("Exam Numbers 100");
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_7C, subject("Englisch"),
				new SchoolYear(2032), CoursePeriod.FIRST_HALF, Lifecycle.ACTIVE, gradingScale.id()));
		final Course secondHalfCourse = courseRepository.save(new Course(null, SchoolClass.CLS_7C, subject("Englisch"),
				new SchoolYear(2032), CoursePeriod.SECOND_HALF, Lifecycle.ACTIVE, gradingScale.id()));

		final Exam otherExam = examRepository
				.save(new Exam(null, otherCourse.id(), "Andere Klausur", LocalDate.of(2032, 9, 17)));
		final Exam firstExam = examRepository
				.save(new Exam(null, secondHalfCourse.id(), "1. Klausur", LocalDate.of(2032, 9, 20)));
		final Exam makeupExam = examRepository.save(new Exam(null, secondHalfCourse.id(), "Nachschreibeklausur",
				LocalDate.of(2032, 9, 24), firstExam.id()));
		final Exam secondExam = examRepository
				.save(new Exam(null, secondHalfCourse.id(), "2. Klausur", LocalDate.of(2033, 2, 3)));

		assertThat(examRepository.findMainExamsByCourseId(secondHalfCourse.id())).containsExactly(firstExam,
				secondExam);
		assertThat(examRepository.findNumbersByCourseId(secondHalfCourse.id()))
				.containsEntry(firstExam.id(), new ExamNumber(1, false))
				.containsEntry(makeupExam.id(), new ExamNumber(1, true))
				.containsEntry(secondExam.id(), new ExamNumber(2, false)).doesNotContainKey(otherExam.id());
		assertThat(examRepository.findNumberById(makeupExam.id())).contains(new ExamNumber(1, true));
	}

	@Test
	void rejectsMakeupExamFromDifferentCourse() {
		final GradingScale gradingScale = createGradingScale("Exam Makeup Course 100");
		final Course originalCourse = courseRepository.save(new Course(null, SchoolClass.CLS_7F, subject("Englisch"),
				new SchoolYear(2032), CoursePeriod.FIRST_HALF, Lifecycle.ACTIVE, gradingScale.id()));
		final Course otherCourse = courseRepository.save(new Course(null, SchoolClass.CLS_7F, subject("Englisch"),
				new SchoolYear(2032), CoursePeriod.SECOND_HALF, Lifecycle.ACTIVE, gradingScale.id()));
		final Exam originalExam = examRepository
				.save(new Exam(null, originalCourse.id(), "1. Klausur", LocalDate.of(2032, 9, 17)));

		assertThatThrownBy(() -> examRepository.save(
				new Exam(null, otherCourse.id(), "Nachschreibeklausur", LocalDate.of(2032, 9, 24), originalExam.id())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Eine Nachschreibeklausur muss zum selben Kurs gehören.");
	}

	@Test
	void rejectsMakeupExamBeforeOriginalExamDate() {
		final GradingScale gradingScale = createGradingScale("Exam Makeup Date 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7D, subject("Englisch"),
				new SchoolYear(2033), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Exam originalExam = examRepository
				.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2033, 9, 17)));

		assertThatThrownBy(() -> examRepository
				.save(new Exam(null, course.id(), "Nachschreibeklausur", LocalDate.of(2033, 9, 16), originalExam.id())))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(
						"Das Datum einer Nachschreibeklausur darf nicht vor dem Datum der ursprünglichen Klausur liegen.");
	}

	@Test
	void rejectsMovingOriginalExamAfterMakeupExamDate() {
		final GradingScale gradingScale = createGradingScale("Exam Original Date 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_7E, subject("Englisch"),
				new SchoolYear(2034), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Exam originalExam = examRepository
				.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2034, 9, 17)));
		examRepository
				.save(new Exam(null, course.id(), "Nachschreibeklausur", LocalDate.of(2034, 9, 18), originalExam.id()));

		assertThatThrownBy(() -> examRepository
				.save(new Exam(originalExam.id(), course.id(), originalExam.title(), LocalDate.of(2034, 9, 19))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Das Datum der ursprünglichen Klausur darf nicht nach einer Nachschreibeklausur liegen.");
	}

	@Test
	void initializesPupilsFromCourseWhenCreatingExam() {
		final GradingScale gradingScale = createGradingScale("Exam Pupils Initial 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_8C, subject("Englisch"),
				new SchoolYear(2035), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Pupil ada = pupilRepository.save(new Pupil(null, "Ada", "Initial", Lifecycle.ACTIVE));
		final Pupil grace = pupilRepository.save(new Pupil(null, "Grace", "Later", Lifecycle.ACTIVE));
		courseRepository.assignPupil(course.id(), ada.id());

		final Exam exam = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2035, 9, 17)));
		courseRepository.assignPupil(course.id(), grace.id());

		assertThat(examRepository.findPupils(exam.id())).containsExactly(ada);
		assertThat(examRepository.findAssignablePupils(exam.id())).containsExactly(grace);
		assertThatThrownBy(() -> courseRepository.removePupil(course.id(), ada.id()))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(
						"Diese:r Schüler:in ist bereits einer Klausur zugeordnet und kann nicht aus dem Kurs entfernt werden.");
	}

	@Test
	void replacesExamPupilsWithCoursePupilsOnly() {
		final GradingScale gradingScale = createGradingScale("Exam Pupils Replace 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_8D, subject("Englisch"),
				new SchoolYear(2035), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Pupil ada = pupilRepository.save(new Pupil(null, "Ada", "Replace", Lifecycle.ACTIVE));
		final Pupil grace = pupilRepository.save(new Pupil(null, "Grace", "Replace", Lifecycle.ACTIVE));
		final Pupil alan = pupilRepository.save(new Pupil(null, "Alan", "Outside", Lifecycle.ACTIVE));
		courseRepository.assignPupil(course.id(), ada.id());
		courseRepository.assignPupil(course.id(), grace.id());
		final Exam exam = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2035, 9, 18)));

		examRepository.replacePupils(exam.id(), List.of(grace.id()));
		examRepository.assignPupil(exam.id(), ada.id());
		examRepository.removePupil(exam.id(), grace.id());

		assertThat(examRepository.findPupils(exam.id())).containsExactly(ada);
		assertThat(examRepository.hasPupil(exam.id(), ada.id())).isTrue();
		assertThat(examRepository.hasPupil(exam.id(), grace.id())).isFalse();
		assertThatThrownBy(() -> examRepository.replacePupils(exam.id(), List.of(alan.id())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Alle teilnehmenden Schüler:innen müssen dem Kurs zugeordnet sein.");
	}

	@Test
	void rejectsRemovingExamPupilsWithResults() {
		final GradingScale gradingScale = createGradingScale("Exam Locked Pupil 100");
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_8E, subject("Englisch"),
				new SchoolYear(2036), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Ada", "Locked", Lifecycle.ACTIVE));
		courseRepository.assignPupil(course.id(), pupil.id());
		final Exam exam = examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2036, 9, 18)));
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Inhalt", "", 0));
		final LoeTask task = levelOfExpectationsRepository.saveTask(new LoeTask(null, category.id(), "Aufgabe 1", 0));
		final LoeRequirement requirement = levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Anforderung", 5, false, 0));
		levelOfExpectationsRepository.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 3));

		assertThat(examRepository.findPupilRemovalLocks(exam.id())).containsEntry(pupil.id(),
				"Für diese:n Schüler:in sind bereits Ergebnisse erfasst. Bitte löschen Sie zuerst die Ergebnisse.");
		assertThatThrownBy(() -> examRepository.removePupil(exam.id(), pupil.id()))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(
						"Für diese:n Schüler:in sind bereits Ergebnisse erfasst. Bitte löschen Sie zuerst die Ergebnisse.");
		assertThatThrownBy(() -> examRepository.replacePupils(exam.id(), List.of()))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(
						"Für diese:n Schüler:in sind bereits Ergebnisse erfasst. Bitte löschen Sie zuerst die Ergebnisse.");
		assertThat(examRepository.findPupils(exam.id())).containsExactly(pupil);
	}

	private GradingScale createGradingScale(final String name) {
		return gradingScaleRepository.save(new GradingScale(null, name, 100, Lifecycle.ACTIVE));
	}

	private Subject subject(final String name) {
		return subjectRepository.findAll().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
				.orElseThrow();
	}
}
