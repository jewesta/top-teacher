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
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.ExamNoteSection;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@SpringBootTest
class LevelOfExpectationsRepositoryTests {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private PupilRepository pupilRepository;

	@Autowired
	private LevelOfExpectationsRepository levelOfExpectationsRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Test
	void savesAndFindsLevelOfExpectationsHierarchy() {
		final Exam exam = createExam(2035, "EH Klausur");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A",
				levelOfExpectationsRepository.nextPartSortOrder(exam.id())));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Inhalt", "**Textverstaendnis**", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", 0));
		final LoeRequirement requirement = levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Nennt zwei Argumente.", 6, false, 0));

		assertThat(levelOfExpectationsRepository.findPartsByExamId(exam.id())).containsExactly(part);
		assertThat(levelOfExpectationsRepository.findCategoriesByExamId(exam.id())).containsExactly(category);
		assertThat(levelOfExpectationsRepository.findTasksByExamId(exam.id())).containsExactly(task);
		assertThat(levelOfExpectationsRepository.findRequirementsByExamId(exam.id())).containsExactly(requirement);

		final LoeRequirement updatedRequirement = new LoeRequirement(requirement.id(), task.id(),
				"Nennt drei Argumente.", 8, true, 0);
		levelOfExpectationsRepository.saveRequirement(updatedRequirement);

		assertThat(levelOfExpectationsRepository.findRequirementsByExamId(exam.id()))
				.containsExactly(updatedRequirement);
	}

	@Test
	void cascadesDeletedParents() {
		final Exam exam = createExam(2036, "EH Cascade");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", 0));
		levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Formuliert sauber.", 4, false, 0));

		levelOfExpectationsRepository.deletePart(part.id());

		assertThat(levelOfExpectationsRepository.findPartsByExamId(exam.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findCategoriesByExamId(exam.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findTasksByExamId(exam.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findRequirementsByExamId(exam.id())).isEmpty();
	}

	@Test
	void movesSiblingsBySwappingSortOrder() {
		final Exam exam = createExam(2037, "EH Move");
		final LoePart first = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "A", 0));
		final LoePart second = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "B", 1));

		levelOfExpectationsRepository.movePart(second, -1);

		assertThat(levelOfExpectationsRepository.findPartsByExamId(exam.id())).extracting(LoePart::title)
				.containsExactly("B", "A");
		assertThat(levelOfExpectationsRepository.nextPartSortOrder(exam.id())).isEqualTo(2);
		assertThat(first.id()).isNotNull();
	}

	@Test
	void savesMovesAndDeletesNoteSections() {
		final Exam exam = createExam(2038, "EH Notes");
		final ExamNoteSection first = levelOfExpectationsRepository
				.saveNoteSection(new ExamNoteSection(null, exam.id(), "Hinweise", "Lies genau.", 0));
		final ExamNoteSection second = levelOfExpectationsRepository
				.saveNoteSection(new ExamNoteSection(null, exam.id(), "Notenschluessel", "15 Punkte = 1", 1));

		levelOfExpectationsRepository.moveNoteSection(second, -1);

		assertThat(levelOfExpectationsRepository.findNoteSectionsByExamId(exam.id())).extracting(ExamNoteSection::title)
				.containsExactly("Notenschluessel", "Hinweise");

		levelOfExpectationsRepository.deleteNoteSection(first.id());

		assertThat(levelOfExpectationsRepository.findNoteSectionsByExamId(exam.id()))
				.containsExactly(new ExamNoteSection(second.id(), exam.id(), "Notenschluessel", "15 Punkte = 1", 0));
	}

	@Test
	void syncsCriteriaAndStoresCriterionResults() {
		final Exam exam = createExam(2039, "EH Results");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", 0));
		final LoeRequirement requirement = levelOfExpectationsRepository.saveRequirement(new LoeRequirement(null,
				task.id(), "Nutzt die [korrekte Zeitform](eh:1) und [präzise Wortwahl](eh:2).", 8, false, 0));

		assertThat(levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id()))
				.extracting(LoeCriterion::criterionKey, LoeCriterion::label, LoeCriterion::sortOrder,
						LoeCriterion::active)
				.containsExactly(tuple("1", "korrekte Zeitform", 0, true), tuple("2", "präzise Wortwahl", 1, true));

		final LoeRequirement updatedRequirement = new LoeRequirement(requirement.id(), task.id(),
				"Nutzt die [richtige Zeitform](eh:1) und [passende Konnektoren](eh:3).", 8, false, 0);
		levelOfExpectationsRepository.saveRequirement(updatedRequirement);
		final List<LoeCriterion> updatedCriteria = levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id());

		assertThat(updatedCriteria).extracting(LoeCriterion::criterionKey, LoeCriterion::label)
				.containsExactly(tuple("1", "richtige Zeitform"), tuple("3", "passende Konnektoren"));

		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Ergebnis", Lifecycle.ACTIVE));
		final LoeCriterion firstCriterion = updatedCriteria.getFirst();
		levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(firstCriterion.id(), pupil.id(), true));

		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeCriterionResult(firstCriterion.id(), pupil.id(), true));
		assertThatThrownBy(() -> levelOfExpectationsRepository.deleteRequirement(requirement.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Für diesen Bereich wurden bereits Ergebnisse erfasst.");

		levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(firstCriterion.id(), pupil.id(), false));
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeCriterionResult(firstCriterion.id(), pupil.id(), false));
	}

	@Test
	void storesRequirementResultsAndProtectsHierarchyDeletion() {
		final Exam exam = createExam(2040, "EH Requirement Results");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", 0));
		final LoeRequirement requirement = levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Formuliert sauber.", 4, false, 0));
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Punkte", Lifecycle.ACTIVE));

		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 3, "Sehr sauber."));

		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeRequirementResult(requirement.id(), pupil.id(), 3, "Sehr sauber."));
		assertThatThrownBy(() -> levelOfExpectationsRepository.deleteRequirement(requirement.id()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Für diesen Bereich wurden bereits Ergebnisse erfasst.");

		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 4, "Noch besser."));
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeRequirementResult(requirement.id(), pupil.id(), 4, "Noch besser."));
	}

	@Test
	void deletesResultsForExamAndPupilOnly() {
		final Exam exam = createExam(2041, "EH Delete Results");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", 0));
		final LoeRequirement requirement = levelOfExpectationsRepository.saveRequirement(
				new LoeRequirement(null, task.id(), "Nutzt die [korrekte Zeitform](eh:1).", 5, false, 0));
		final LoeCriterion criterion = levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id()).getFirst();
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Löschen", Lifecycle.ACTIVE));
		final Pupil otherPupil = pupilRepository.save(new Pupil(null, "Test", "Bleibt", Lifecycle.ACTIVE));

		levelOfExpectationsRepository.saveCriterionResult(new LoeCriterionResult(criterion.id(), pupil.id(), true));
		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 3, "Wird gelöscht."));
		levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(criterion.id(), otherPupil.id(), true));
		levelOfExpectationsRepository.saveRequirementResult(
				new LoeRequirementResult(requirement.id(), otherPupil.id(), 4, "Bleibt bestehen."));

		levelOfExpectationsRepository.deleteResultsByExamAndPupil(exam.id(), pupil.id());

		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), otherPupil.id()))
				.containsExactly(new LoeCriterionResult(criterion.id(), otherPupil.id(), true));
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), otherPupil.id()))
				.containsExactly(new LoeRequirementResult(requirement.id(), otherPupil.id(), 4, "Bleibt bestehen."));
	}

	@Test
	void copiesDesignAndNotesWithoutResults() {
		final Exam sourceExam = createExam(2042, "EH Copy Source");
		final Exam targetExam = createExam(2043, "EH Copy Target");
		final LoePart part = levelOfExpectationsRepository
				.savePart(new LoePart(null, sourceExam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "**Fokus**", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", 0));
		final LoeRequirement requirement = levelOfExpectationsRepository.saveRequirement(
				new LoeRequirement(null, task.id(), "Nutzt die [korrekte Zeitform](eh:1).", 5, true, 0));
		final ExamNoteSection noteSection = levelOfExpectationsRepository
				.saveNoteSection(new ExamNoteSection(null, sourceExam.id(), "Hinweis", "Bitte sauber lesen.", 0));
		final LoeCriterion criterion = levelOfExpectationsRepository.findActiveCriteriaByExamId(sourceExam.id())
				.getFirst();
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Kopie", Lifecycle.ACTIVE));
		levelOfExpectationsRepository.saveCriterionResult(new LoeCriterionResult(criterion.id(), pupil.id(), true));
		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 4, "Schon erfasst."));

		levelOfExpectationsRepository.copyDesignAndNotes(sourceExam.id(), targetExam.id());

		final LoePart copiedPart = levelOfExpectationsRepository.findPartsByExamId(targetExam.id()).getFirst();
		final LoeCategory copiedCategory = levelOfExpectationsRepository.findCategoriesByExamId(targetExam.id())
				.getFirst();
		final LoeTask copiedTask = levelOfExpectationsRepository.findTasksByExamId(targetExam.id()).getFirst();
		final LoeRequirement copiedRequirement = levelOfExpectationsRepository.findRequirementsByExamId(targetExam.id())
				.getFirst();

		assertThat(copiedPart).isEqualTo(new LoePart(copiedPart.id(), targetExam.id(), part.title(), part.sortOrder()));
		assertThat(copiedPart.id()).isNotEqualTo(part.id());
		assertThat(copiedCategory).isEqualTo(new LoeCategory(copiedCategory.id(), copiedPart.id(), category.title(),
				category.descriptionMarkdown(), category.sortOrder()));
		assertThat(copiedTask)
				.isEqualTo(new LoeTask(copiedTask.id(), copiedCategory.id(), task.title(), task.sortOrder()));
		assertThat(copiedRequirement).isEqualTo(
				new LoeRequirement(copiedRequirement.id(), copiedTask.id(), requirement.descriptionMarkdown(),
						requirement.maxPoints(), requirement.bonus(), requirement.sortOrder()));
		assertThat(levelOfExpectationsRepository.findActiveCriteriaByExamId(targetExam.id()))
				.extracting(LoeCriterion::criterionKey, LoeCriterion::label)
				.containsExactly(tuple("1", "korrekte Zeitform"));
		final ExamNoteSection copiedNoteSection = levelOfExpectationsRepository
				.findNoteSectionsByExamId(targetExam.id()).getFirst();
		assertThat(copiedNoteSection).isEqualTo(new ExamNoteSection(copiedNoteSection.id(), targetExam.id(),
				noteSection.title(), noteSection.descriptionMarkdown(), noteSection.sortOrder()));
		assertThat(copiedNoteSection.id()).isNotEqualTo(noteSection.id());
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(targetExam.id(), pupil.id()))
				.isEmpty();
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(targetExam.id(), pupil.id()))
				.isEmpty();
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(sourceExam.id(), pupil.id()))
				.containsExactly(new LoeCriterionResult(criterion.id(), pupil.id(), true));
	}

	private Exam createExam(final int calendarYear, final String title) {
		final GradingScale gradingScale = gradingScaleRepository
				.save(new GradingScale(null, "EH Repo " + calendarYear + " " + title, 100, Lifecycle.ACTIVE));
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_10A, subject("Englisch"),
				new SchoolYear(calendarYear), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, gradingScale.id()));
		return examRepository.save(new Exam(null, course.id(), title, LocalDate.of(calendarYear, 9, 1)));
	}

	private Subject subject(final String name) {
		return subjectRepository.findAll().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
				.orElseThrow();
	}
}
