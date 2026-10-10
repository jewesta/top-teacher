package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@SpringBootTest
class LevelOfExpectationsRepositoryTests {

	private static final String CORRECTION_MODE_MESSAGE = "Der Erwartungshorizont ist im Korrekturmodus. Struktur, Punkte und Kriterien können nicht geändert werden.";
	private static final String RESULTS_BLOCKED_MESSAGE = "Ergebnisse können erst erfasst werden, wenn die regulären Anforderungspunkte jeder Aufgabe genau den Aufgabenpunkten entsprechen und die Aufgabensumme zur Notenskala passt.";

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

	@Autowired
	private NamedParameterJdbcTemplate jdbc;

	@Test
	void savesAndFindsLevelOfExpectationsHierarchy() {
		final Exam exam = createExam(2035, "EH Klausur");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A",
				levelOfExpectationsRepository.nextPartSortOrder(exam.id())));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Inhalt", "**Textverstaendnis**", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", "**Aufgabentext**", 6, 0));
		final LoeMaterial material = levelOfExpectationsRepository
				.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 1", "**Quellentext**", 0));
		final LoeRequirement requirement = levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Nennt zwei Argumente.", 6, false, 0));

		assertThat(levelOfExpectationsRepository.findPartsByExamId(exam.id())).containsExactly(part);
		assertThat(levelOfExpectationsRepository.findCategoriesByExamId(exam.id())).containsExactly(category);
		assertThat(levelOfExpectationsRepository.findTasksByExamId(exam.id())).containsExactly(task);
		assertThat(levelOfExpectationsRepository.findMaterialsByExamId(exam.id())).containsExactly(material);
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
		levelOfExpectationsRepository.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 1", "Text", 0));
		levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Formuliert sauber.", 4, false, 0));

		levelOfExpectationsRepository.deletePart(part.id());

		assertThat(levelOfExpectationsRepository.findPartsByExamId(exam.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findCategoriesByExamId(exam.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findTasksByExamId(exam.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findRequirementsByExamId(exam.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findMaterialsByExamId(exam.id())).isEmpty();
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
	void syncsCriteriaAndStoresCriterionResults() {
		final Exam exam = createExam(2039, "EH Results");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", "", 100, 0));
		assertThatThrownBy(() -> levelOfExpectationsRepository.saveRequirement(
				new LoeRequirement(null, task.id(), "[Zu viel](crit:1?points=1,5)", 1, false, 0)))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Ordne 0,5 Kriterienpunkte weniger zu.");
		assertThatThrownBy(() -> levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "[Offen](crit:1?points=?)", 1, false, 0)))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Kriterium „Offen“: Ordne eine gültige Punktzahl zu.");
		final LoeRequirement requirement = levelOfExpectationsRepository.saveRequirement(new LoeRequirement(null,
				task.id(), "Nutzt die [korrekte Zeitform](crit:1?points=0,5) und [präzise Wortwahl](crit:2?points=1.5).", 100, false,
				0));

		assertThat(levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id())).extracting(
				LoeCriterion::criterionKey, LoeCriterion::label, LoeCriterion::pointUnits, LoeCriterion::sortOrder,
				LoeCriterion::active)
				.containsExactly(tuple("1", "korrekte Zeitform", 1, 0, true),
						tuple("2", "präzise Wortwahl", 3, 1, true));

		final LoeRequirement updatedRequirement = new LoeRequirement(requirement.id(), task.id(),
				"Nutzt die [richtige Zeitform](crit:1?points=1,5) und [passende Konnektoren](crit:3).", 100, false, 0);
		levelOfExpectationsRepository.saveRequirement(updatedRequirement);
		final List<LoeCriterion> updatedCriteria = levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id());

		assertThat(updatedCriteria).extracting(LoeCriterion::criterionKey, LoeCriterion::label,
				LoeCriterion::pointUnits).containsExactly(tuple("1", "richtige Zeitform", 3),
						tuple("3", "passende Konnektoren", 2));

		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Ergebnis", Lifecycle.ACTIVE));
		final LoeCriterion firstCriterion = updatedCriteria.getFirst();
		levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(firstCriterion.id(), pupil.id(), 1));
		assertThat(jdbc.queryForObject("""
			select achieved from eh_criterion_result where criterion_id = :criterionId and pupil_id = :pupilId
			""", Map.of("criterionId", firstCriterion.id(), "pupilId", pupil.id()), Boolean.class)).isFalse();
		levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(firstCriterion.id(), pupil.id(), 3));
		assertThat(jdbc.queryForObject("""
			select achieved from eh_criterion_result where criterion_id = :criterionId and pupil_id = :pupilId
			""", Map.of("criterionId", firstCriterion.id(), "pupilId", pupil.id()), Boolean.class)).isTrue();
		assertThatThrownBy(() -> levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(firstCriterion.id(), pupil.id(), 4)))
					.isInstanceOf(IllegalArgumentException.class);

		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeCriterionResult(firstCriterion.id(), pupil.id(), 3));
		assertThatThrownBy(() -> levelOfExpectationsRepository.saveRequirement(new LoeRequirement(requirement.id(),
				task.id(), "Nutzt die [richtige Zeitform](crit:1?points=2) und [passende Konnektoren](crit:3).", 100, false,
				0))).isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);
		assertThatThrownBy(() -> levelOfExpectationsRepository.deleteRequirement(requirement.id()))
				.isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);

		levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(firstCriterion.id(), pupil.id(), 0));
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeCriterionResult(firstCriterion.id(), pupil.id(), 0));
	}

	@Test
	void storesRequirementResultsAndProtectsHierarchyDeletion() {
		final Exam exam = createExam(2040, "EH Requirement Results");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", "", 100, 0));
		final LoeRequirement requirement = levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Formuliert sauber.", 100, false, 0));
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Punkte", Lifecycle.ACTIVE));

		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 3, "Sehr sauber."));

		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeRequirementResult(requirement.id(), pupil.id(), 3, "Sehr sauber."));
		assertThatThrownBy(() -> levelOfExpectationsRepository.deleteRequirement(requirement.id()))
				.isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);

		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 4, "Noch besser."));
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(new LoeRequirementResult(requirement.id(), pupil.id(), 4, "Noch besser."));

		final LoeRequirementResult partialResult = new LoeRequirementResult(requirement.id(), pupil.id(), 7, 1,
				"Ein halber Zusatzpunkt.");
		levelOfExpectationsRepository.saveRequirementResult(partialResult);
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.containsExactly(partialResult);
		assertThat(partialResult.points()).isEqualTo(4);
	}

	@Test
	void blocksResultsUntilTaskAndRequirementPointBudgetsAreValid() {
		final Exam exam = createExam(2045, "EH Point Distribution");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Inhalt", "", 0));
		LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Aufgabe 1", "Aufgabentext", 90, 0));
		LoeRequirement requirement = levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Anforderung", 90, false, 0));
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Verteilung", Lifecycle.ACTIVE));

		assertThat(levelOfExpectationsRepository.isPointDistributionValidForExam(exam.id())).isFalse();
		final LoeRequirement initialRequirement = requirement;
		assertThatThrownBy(() -> levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(initialRequirement.id(), pupil.id(), 45)))
						.isInstanceOf(IllegalStateException.class).hasMessage(RESULTS_BLOCKED_MESSAGE);

		task = levelOfExpectationsRepository.saveTask(new LoeTask(task.id(), task.categoryId(), task.title(),
				task.descriptionMarkdown(), 100, task.sortOrder()));
		assertThat(levelOfExpectationsRepository.isPointDistributionValidForExam(exam.id())).isFalse();

		requirement = levelOfExpectationsRepository.saveRequirement(new LoeRequirement(requirement.id(), task.id(),
				requirement.descriptionMarkdown(), 100, false, requirement.sortOrder()));
		assertThat(levelOfExpectationsRepository.isPointDistributionValidForExam(exam.id())).isTrue();
		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 45));
	}

	@Test
	void treatsAnExamWithoutAGradingScaleAsAnInvalidPointDistribution() {
		final Exam exam = createExam(2046, "EH Missing Grading Scale");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Inhalt", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Aufgabe 1", "Aufgabentext", 100, 0));
		levelOfExpectationsRepository
				.saveRequirement(new LoeRequirement(null, task.id(), "Anforderung", 100, false, 0));

		jdbc.getJdbcTemplate().execute("alter table exam alter column grading_scale_id drop not null");
		try {
			jdbc.update("update exam set grading_scale_id = null where id = :id", Map.of("id", exam.id()));

			assertThat(levelOfExpectationsRepository.isPointDistributionValidForExam(exam.id())).isFalse();
		} finally {
			jdbc.update("update exam set grading_scale_id = :gradingScaleId where id = :id",
					Map.of("gradingScaleId", exam.gradingScaleId(), "id", exam.id()));
			jdbc.getJdbcTemplate().execute("alter table exam alter column grading_scale_id set not null");
		}
	}

	@Test
	void allowsRequirementTextCorrectionsButLocksTasksMaterialsAndStructureAfterResultsExist() {
		final Exam exam = createExam(2044, "EH Correction Mode");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "Beschreibung", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", "Aufgabentext", 100, 0));
		final LoeRequirement requirement = levelOfExpectationsRepository.saveRequirement(new LoeRequirement(null,
				task.id(), "Nutzt die [korrekte Zeitform](crit:1) und [präzise Wortwahl](crit:2).", 100, false, 0));
		final LoeMaterial material = levelOfExpectationsRepository
				.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 1", "Text", 0));
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Korrektur", Lifecycle.ACTIVE));
		final LoeCriterion criterion = levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id()).getFirst();
		assertThat(levelOfExpectationsRepository.hasResultsForExam(exam.id())).isFalse();

		levelOfExpectationsRepository.saveCriterionResult(new LoeCriterionResult(criterion.id(), pupil.id(), 2));
		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 6, "Schon bewertet."));

		assertThat(levelOfExpectationsRepository.hasResultsForExam(exam.id())).isTrue();

		levelOfExpectationsRepository.saveRequirement(new LoeRequirement(requirement.id(), requirement.taskId(),
				"Nutzt die [richtige Zeitform](crit:1) und [treffende Wortwahl](crit:2).", 100, false, 0));

		assertThat(levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id()))
				.extracting(LoeCriterion::criterionKey, LoeCriterion::label)
				.containsExactly(tuple("1", "richtige Zeitform"), tuple("2", "treffende Wortwahl"));
		assertThatThrownBy(() -> levelOfExpectationsRepository.saveRequirement(new LoeRequirement(requirement.id(),
				requirement.taskId(), "Nutzt die [richtige Zeitform](crit:1).", 100, false, 0)))
						.isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);
		assertThatThrownBy(() -> levelOfExpectationsRepository.saveRequirement(new LoeRequirement(requirement.id(),
				requirement.taskId(),
				"Nutzt die [richtige Zeitform](crit:1), [treffende Wortwahl](crit:2) und [Satzbau](crit:3).", 100, false, 0)))
						.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> levelOfExpectationsRepository.saveRequirement(new LoeRequirement(requirement.id(),
				requirement.taskId(), requirement.descriptionMarkdown(), 10, false, 0)))
						.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Teil B", 1)))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> levelOfExpectationsRepository
				.savePart(new LoePart(part.id(), part.examId(), "Klausurteil Alpha", 0)))
						.isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);
		assertThatThrownBy(() -> levelOfExpectationsRepository
				.saveCategory(new LoeCategory(category.id(), category.partId(), "Stil", "Neue Beschreibung", 0)))
						.isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);
		assertThatThrownBy(() -> levelOfExpectationsRepository.saveTask(new LoeTask(task.id(), task.categoryId(),
				"Aufgabe 1", task.descriptionMarkdown(), task.maxPoints(), task.sortOrder())))
						.isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);
		assertThatThrownBy(() -> levelOfExpectationsRepository.saveMaterial(new LoeMaterial(material.id(), part.id(),
				"Quelle geändert", material.descriptionMarkdown(), material.sortOrder())))
						.isInstanceOf(IllegalStateException.class).hasMessage(CORRECTION_MODE_MESSAGE);
		assertThatThrownBy(() -> levelOfExpectationsRepository.movePart(part, 1))
				.isInstanceOf(IllegalStateException.class);
		final Exam unlockedExam = createExam(2047, "EH Unlocked Material Move");
		final LoePart unlockedPart = levelOfExpectationsRepository
				.savePart(new LoePart(null, unlockedExam.id(), "Klausurteil A", 0));
		final LoeMaterial forgedMaterial = new LoeMaterial(material.id(), unlockedPart.id(), material.title(),
				material.descriptionMarkdown(), material.sortOrder());
		assertThatThrownBy(() -> levelOfExpectationsRepository.moveMaterial(forgedMaterial, 1))
				.isInstanceOf(IllegalStateException.class)
						.hasMessage(CORRECTION_MODE_MESSAGE);
		assertThatThrownBy(() -> levelOfExpectationsRepository.deleteTask(task.id()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void deletesResultsForExamAndPupilOnly() {
		final Exam exam = createExam(2041, "EH Delete Results");
		final LoePart part = levelOfExpectationsRepository.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", "", 100, 0));
		final LoeRequirement requirement = levelOfExpectationsRepository.saveRequirement(
				new LoeRequirement(null, task.id(), "Nutzt die [korrekte Zeitform](crit:1).", 100, false, 0));
		final LoeCriterion criterion = levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id()).getFirst();
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Löschen", Lifecycle.ACTIVE));
		final Pupil otherPupil = pupilRepository.save(new Pupil(null, "Test", "Bleibt", Lifecycle.ACTIVE));

		levelOfExpectationsRepository.saveCriterionResult(new LoeCriterionResult(criterion.id(), pupil.id(), 2));
		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 3, "Wird gelöscht."));
		levelOfExpectationsRepository
				.saveCriterionResult(new LoeCriterionResult(criterion.id(), otherPupil.id(), 2));
		levelOfExpectationsRepository.saveRequirementResult(
				new LoeRequirementResult(requirement.id(), otherPupil.id(), 4, "Bleibt bestehen."));

		levelOfExpectationsRepository.deleteResultsByExamAndPupil(exam.id(), pupil.id());

		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), pupil.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id())).isEmpty();
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(exam.id(), otherPupil.id()))
				.containsExactly(new LoeCriterionResult(criterion.id(), otherPupil.id(), 2));
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), otherPupil.id()))
				.containsExactly(new LoeRequirementResult(requirement.id(), otherPupil.id(), 4, "Bleibt bestehen."));
	}

	@Test
	void deduplicatesMaterialImagesAndRemovesThemAfterTheLastReference() {
		final Exam exam = createExam(2048, "Material Images");
		final LoePart part = levelOfExpectationsRepository
				.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeMaterial first = levelOfExpectationsRepository
				.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 1", "", 0));
		final LoeMaterial second = levelOfExpectationsRepository
				.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 2", "", 1));
		final LoeMaterialImage firstUpload = image("4n8k2p7c", "a".repeat(64));
		final LoeMaterialImage duplicateUpload = image("abcdefgh", firstUpload.contentHash());

		final LoeMaterial savedFirst = levelOfExpectationsRepository.saveMaterialWithImages(
				new LoeMaterial(first.id(), first.partId(), first.title(), "![Diagramm](img:4n8k2p7c?width=75)", 0),
				List.of(firstUpload));
		final LoeMaterial savedSecond = levelOfExpectationsRepository.saveMaterialWithImages(
				new LoeMaterial(second.id(), second.partId(), second.title(), "![Diagramm](img:abcdefgh)", 1),
				List.of(duplicateUpload));

		assertThat(savedFirst.descriptionMarkdown()).contains("img:4n8k2p7c");
		assertThat(savedSecond.descriptionMarkdown()).contains("img:4n8k2p7c").doesNotContain("img:abcdefgh");
		assertThat(levelOfExpectationsRepository.findMaterialImagesByIds(List.of("4n8k2p7c", "abcdefgh")))
				.extracting(LoeMaterialImage::id).containsExactly("4n8k2p7c");

		levelOfExpectationsRepository.deleteMaterial(first.id());
		assertThat(levelOfExpectationsRepository.findReferencedMaterialImageById("4n8k2p7c")).isPresent();
		levelOfExpectationsRepository.deleteMaterial(second.id());
		assertThat(levelOfExpectationsRepository.findReferencedMaterialImageById("4n8k2p7c")).isEmpty();
	}

	@Test
	void retriesMaterialImageIdentifiersThatCollideInTheDatabase() {
		final Exam exam = createExam(2049, "Material Image ID Collision");
		final LoePart part = levelOfExpectationsRepository
				.savePart(new LoePart(null, exam.id(), "Klausurteil A", 0));
		final LoeMaterial first = levelOfExpectationsRepository
				.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 1", "", 0));
		final LoeMaterial second = levelOfExpectationsRepository
				.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 2", "", 1));
		levelOfExpectationsRepository.saveMaterialWithImages(
				new LoeMaterial(first.id(), first.partId(), first.title(), "![Erstes](img:sameid01)", 0),
				List.of(image("sameid01", "c".repeat(64))));

		final LoeMaterial savedSecond = levelOfExpectationsRepository.saveMaterialWithImages(
				new LoeMaterial(second.id(), second.partId(), second.title(), "![Zweites](img:sameid01)", 1),
				List.of(image("sameid01", "d".repeat(64))));
		final String replacementId = LoeMaterialImageReferences.ids(savedSecond.descriptionMarkdown()).iterator().next();

		assertThat(replacementId).matches("[a-z0-9]{8}").isNotEqualTo("sameid01");
		assertThat(levelOfExpectationsRepository.findMaterialImagesByIds(List.of("sameid01", replacementId)))
				.extracting(LoeMaterialImage::id).containsExactlyInAnyOrder("sameid01", replacementId);
	}

	@Test
	void copiesDesignWithoutResults() {
		final Exam sourceExam = createExam(2042, "EH Copy Source");
		final Exam targetExam = createExam(2043, "EH Copy Target");
		final LoePart part = levelOfExpectationsRepository
				.savePart(new LoePart(null, sourceExam.id(), "Klausurteil A", 0));
		final LoeCategory category = levelOfExpectationsRepository
				.saveCategory(new LoeCategory(null, part.id(), "Sprache", "**Fokus**", 0));
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe 1", "Aufgabentext", 100, 0));
		final LoeMaterial material = levelOfExpectationsRepository
				.saveMaterial(new LoeMaterial(null, part.id(), "Quelle 1", "Quellentext", 0));
		final LoeMaterial materialWithImage = levelOfExpectationsRepository.saveMaterialWithImages(
				new LoeMaterial(material.id(), material.partId(), material.title(),
						"Quellentext\n\n![Diagramm](img:copyimg1?width=75)", material.sortOrder()),
				List.of(image("copyimg1", "b".repeat(64))));
		final LoeRequirement requirement = levelOfExpectationsRepository.saveRequirement(
				new LoeRequirement(null, task.id(), "Nutzt die [korrekte Zeitform](crit:1).", 100, false, 0));
		final LoeCriterion criterion = levelOfExpectationsRepository.findActiveCriteriaByExamId(sourceExam.id())
				.getFirst();
		final Pupil pupil = pupilRepository.save(new Pupil(null, "Test", "Kopie", Lifecycle.ACTIVE));
		levelOfExpectationsRepository.saveCriterionResult(new LoeCriterionResult(criterion.id(), pupil.id(), 2));
		levelOfExpectationsRepository
				.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 4, "Schon erfasst."));

		levelOfExpectationsRepository.copyDesign(sourceExam.id(), targetExam.id());

		final LoePart copiedPart = levelOfExpectationsRepository.findPartsByExamId(targetExam.id()).getFirst();
		final LoeCategory copiedCategory = levelOfExpectationsRepository.findCategoriesByExamId(targetExam.id())
				.getFirst();
		final LoeTask copiedTask = levelOfExpectationsRepository.findTasksByExamId(targetExam.id()).getFirst();
		final LoeRequirement copiedRequirement = levelOfExpectationsRepository.findRequirementsByExamId(targetExam.id())
				.getFirst();
		final LoeMaterial copiedMaterial = levelOfExpectationsRepository.findMaterialsByExamId(targetExam.id())
				.getFirst();

		assertThat(copiedPart).isEqualTo(new LoePart(copiedPart.id(), targetExam.id(), part.title(), part.sortOrder()));
		assertThat(copiedPart.id()).isNotEqualTo(part.id());
		assertThat(copiedCategory).isEqualTo(new LoeCategory(copiedCategory.id(), copiedPart.id(), category.title(),
				category.descriptionMarkdown(), category.sortOrder()));
		assertThat(copiedTask)
				.isEqualTo(new LoeTask(copiedTask.id(), copiedCategory.id(), task.title(), task.descriptionMarkdown(),
						task.maxPoints(), task.sortOrder()));
		assertThat(copiedMaterial).isEqualTo(new LoeMaterial(copiedMaterial.id(), copiedPart.id(), material.title(),
				materialWithImage.descriptionMarkdown(), material.sortOrder()));
		assertThat(levelOfExpectationsRepository.findMaterialImagesByIds(List.of("copyimg1"))).hasSize(1);
		assertThat(copiedRequirement).isEqualTo(
				new LoeRequirement(copiedRequirement.id(), copiedTask.id(), requirement.descriptionMarkdown(),
						requirement.maxPoints(), requirement.bonus(), requirement.sortOrder()));
		assertThat(levelOfExpectationsRepository.findActiveCriteriaByExamId(targetExam.id()))
				.extracting(LoeCriterion::criterionKey, LoeCriterion::label)
				.containsExactly(tuple("1", "korrekte Zeitform"));
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(targetExam.id(), pupil.id()))
				.isEmpty();
		assertThat(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(targetExam.id(), pupil.id()))
				.isEmpty();
		assertThat(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(sourceExam.id(), pupil.id()))
				.containsExactly(new LoeCriterionResult(criterion.id(), pupil.id(), 2));
	}

	private static LoeMaterialImage image(final String id, final String contentHash) {
		return new LoeMaterialImage(id, contentHash, "image/png", 2, 1, 3, new byte[] {
				1, 2, 3
		});
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
