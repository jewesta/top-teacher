package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.ExpectationHorizonRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.EhCategory;
import de.westarps.topteacher.model.EhCriterion;
import de.westarps.topteacher.model.EhCriterionResult;
import de.westarps.topteacher.model.EhPart;
import de.westarps.topteacher.model.EhRequirement;
import de.westarps.topteacher.model.EhRequirementResult;
import de.westarps.topteacher.model.EhTask;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.ExamNoteSection;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@SpringBootTest
class ExpectationHorizonRepositoryTests {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private PupilRepository pupilRepository;

	@Autowired
	private ExpectationHorizonRepository expectationHorizonRepository;

	@Test
	void savesAndFindsExpectationHorizonHierarchy() {
		final Exam exam = createExam(2035, "EH Klausur");
		final EhPart part = expectationHorizonRepository.savePart(new EhPart(null, exam.id(), "Klausurteil A",
				expectationHorizonRepository.nextPartSortOrder(exam.id())));
		final EhCategory category = expectationHorizonRepository
				.saveCategory(new EhCategory(null, part.id(), "Inhalt", "**Textverstaendnis**", 0));
		final EhTask task = expectationHorizonRepository.saveTask(new EhTask(null, category.id(), "Teilaufgabe 1", 0));
		final EhRequirement requirement = expectationHorizonRepository
				.saveRequirement(new EhRequirement(null, task.id(), "Nennt zwei Argumente.", 6, false, 0));

		assertThat(expectationHorizonRepository.findPartsByExamId(exam.id())).containsExactly(part);
		assertThat(expectationHorizonRepository.findCategoriesByExamId(exam.id())).containsExactly(category);
		assertThat(expectationHorizonRepository.findTasksByExamId(exam.id())).containsExactly(task);
		assertThat(expectationHorizonRepository.findRequirementsByExamId(exam.id())).containsExactly(requirement);

		final EhRequirement updatedRequirement = new EhRequirement(requirement.id(), task.id(), "Nennt drei Argumente.",
				8, true, 0);
		expectationHorizonRepository.saveRequirement(updatedRequirement);

		assertThat(expectationHorizonRepository.findRequirementsByExamId(exam.id()))
				.containsExactly(updatedRequirement);
	}

	@Test
	void cascadesDeletedParents() {
		final Exam exam = createExam(2036, "EH Cascade");
		final EhPart part = expectationHorizonRepository.savePart(new EhPart(null, exam.id(), "Klausurteil A", 0));
		final EhCategory category = expectationHorizonRepository
				.saveCategory(new EhCategory(null, part.id(), "Sprache", "", 0));
		final EhTask task = expectationHorizonRepository.saveTask(new EhTask(null, category.id(), "Teilaufgabe 1", 0));
		expectationHorizonRepository
				.saveRequirement(new EhRequirement(null, task.id(), "Formuliert sauber.", 4, false, 0));

		expectationHorizonRepository.deletePart(part.id());

		assertThat(expectationHorizonRepository.findPartsByExamId(exam.id())).isEmpty();
		assertThat(expectationHorizonRepository.findCategoriesByExamId(exam.id())).isEmpty();
		assertThat(expectationHorizonRepository.findTasksByExamId(exam.id())).isEmpty();
		assertThat(expectationHorizonRepository.findRequirementsByExamId(exam.id())).isEmpty();
	}

	@Test
	void movesSiblingsBySwappingSortOrder() {
		final Exam exam = createExam(2037, "EH Move");
		final EhPart first = expectationHorizonRepository.savePart(new EhPart(null, exam.id(), "A", 0));
		final EhPart second = expectationHorizonRepository.savePart(new EhPart(null, exam.id(), "B", 1));

		expectationHorizonRepository.movePart(second, -1);

		assertThat(expectationHorizonRepository.findPartsByExamId(exam.id())).extracting(EhPart::title)
				.containsExactly("B", "A");
		assertThat(expectationHorizonRepository.nextPartSortOrder(exam.id())).isEqualTo(2);
		assertThat(first.id()).isNotNull();
	}

	@Test
	void savesMovesAndDeletesNoteSections() {
		final Exam exam = createExam(2038, "EH Notes");
		final ExamNoteSection first = expectationHorizonRepository
				.saveNoteSection(new ExamNoteSection(null, exam.id(), "Hinweise", "Lies genau.", 0));
		final ExamNoteSection second = expectationHorizonRepository
				.saveNoteSection(new ExamNoteSection(null, exam.id(), "Notenschluessel", "15 Punkte = 1", 1));

		expectationHorizonRepository.moveNoteSection(second, -1);

		assertThat(expectationHorizonRepository.findNoteSectionsByExamId(exam.id())).extracting(ExamNoteSection::title)
				.containsExactly("Notenschluessel", "Hinweise");

		expectationHorizonRepository.deleteNoteSection(first.id());

		assertThat(expectationHorizonRepository.findNoteSectionsByExamId(exam.id()))
				.containsExactly(new ExamNoteSection(second.id(), exam.id(), "Notenschluessel", "15 Punkte = 1", 0));
	}

	@Test
	void syncsCriteriaAndStoresCriterionResults() {
		final Exam exam = createExam(2039, "EH Results");
		final EhPart part = expectationHorizonRepository.savePart(new EhPart(null, exam.id(), "Klausurteil A", 0));
		final EhCategory category = expectationHorizonRepository
				.saveCategory(new EhCategory(null, part.id(), "Sprache", "", 0));
		final EhTask task = expectationHorizonRepository.saveTask(new EhTask(null, category.id(), "Teilaufgabe 1", 0));
		final EhRequirement requirement = expectationHorizonRepository.saveRequirement(new EhRequirement(null,
				task.id(), "Nutzt die [korrekte Zeitform](eh:1) und [präzise Wortwahl](eh:2).", 8, false, 0));

		assertThat(expectationHorizonRepository.findActiveCriteriaByExamId(exam.id()))
				.extracting(EhCriterion::criterionKey, EhCriterion::label, EhCriterion::sortOrder,
						EhCriterion::active)
				.containsExactly(tuple("1", "korrekte Zeitform", 0, true), tuple("2", "präzise Wortwahl", 1, true));

		final EhRequirement updatedRequirement = new EhRequirement(requirement.id(), task.id(),
				"Nutzt die [richtige Zeitform](eh:1) und [passende Konnektoren](eh:3).", 8, false, 0);
		expectationHorizonRepository.saveRequirement(updatedRequirement);
		final List<EhCriterion> updatedCriteria = expectationHorizonRepository.findActiveCriteriaByExamId(exam.id());

		assertThat(updatedCriteria).extracting(EhCriterion::criterionKey, EhCriterion::label)
				.containsExactly(tuple("1", "richtige Zeitform"), tuple("3", "passende Konnektoren"));

		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Ergebnis", Lifecycle.ACTIVE));
		final EhCriterion firstCriterion = updatedCriteria.getFirst();
		expectationHorizonRepository
				.saveCriterionResult(new EhCriterionResult(firstCriterion.id(), pupil.id(), true));

		assertThat(expectationHorizonRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new EhCriterionResult(firstCriterion.id(), pupil.id(), true));
		assertThatThrownBy(() -> expectationHorizonRepository.deleteRequirement(requirement.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Für diesen Bereich wurden bereits Ergebnisse erfasst.");

		expectationHorizonRepository
				.saveCriterionResult(new EhCriterionResult(firstCriterion.id(), pupil.id(), false));
		assertThat(expectationHorizonRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new EhCriterionResult(firstCriterion.id(), pupil.id(), false));
	}

	@Test
	void storesRequirementResultsAndProtectsHierarchyDeletion() {
		final Exam exam = createExam(2040, "EH Requirement Results");
		final EhPart part = expectationHorizonRepository.savePart(new EhPart(null, exam.id(), "Klausurteil A", 0));
		final EhCategory category = expectationHorizonRepository
				.saveCategory(new EhCategory(null, part.id(), "Sprache", "", 0));
		final EhTask task = expectationHorizonRepository.saveTask(new EhTask(null, category.id(), "Teilaufgabe 1", 0));
		final EhRequirement requirement = expectationHorizonRepository
				.saveRequirement(new EhRequirement(null, task.id(), "Formuliert sauber.", 4, false, 0));
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Punkte", Lifecycle.ACTIVE));

		expectationHorizonRepository
				.saveRequirementResult(new EhRequirementResult(requirement.id(), pupil.id(), 3, "Sehr sauber."));

		assertThat(expectationHorizonRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new EhRequirementResult(requirement.id(), pupil.id(), 3, "Sehr sauber."));
		assertThatThrownBy(() -> expectationHorizonRepository.deleteRequirement(requirement.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Für diesen Bereich wurden bereits Ergebnisse erfasst.");

		expectationHorizonRepository
				.saveRequirementResult(new EhRequirementResult(requirement.id(), pupil.id(), 4, "Noch besser."));
		assertThat(expectationHorizonRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new EhRequirementResult(requirement.id(), pupil.id(), 4, "Noch besser."));
	}

	private Exam createExam(final int calendarYear, final String title) {
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_10A, Subject.ENGLISH,
				new SchoolYear(calendarYear), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE));
		return examRepository.save(new Exam(null, course.id(), title, LocalDate.of(calendarYear, 9, 1)));
	}
}
