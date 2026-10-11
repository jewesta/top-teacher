package de.westarps.topteacher.backend.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

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
import de.westarps.topteacher.model.loe.ExamAuthoringResult;
import de.westarps.topteacher.model.loe.ExamContentCommand;
import de.westarps.topteacher.model.loe.ExamContentCommand.Type;
import de.westarps.topteacher.model.loe.ExamNodeReference;
import de.westarps.topteacher.model.loe.LevelOfExpectationsCommand;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@SpringBootTest
class ExamAuthoringServiceTests {

	private static final AtomicInteger NEXT_YEAR = new AtomicInteger(2300);

	@Autowired private ExamAuthoringService authoring;
	@Autowired private ExamDesignGuard guard;
	@Autowired private LevelOfExpectationsRepository repository;
	@Autowired private ExamRepository exams;
	@Autowired private CourseRepository courses;
	@Autowired private SubjectRepository subjects;
	@Autowired private GradingScaleRepository scales;
	@Autowired private PupilRepository pupils;
	@Autowired private NamedParameterJdbcTemplate jdbc;

	@Test
	void createsPupilContentByLocalParentKeysWithoutGeneratingRequirements() {
		final Exam exam = exam();
		final var result = apply(exam, "initial", List.of(
				create(Type.CREATE_PART, "part", null, null, "Klausurteil A", null, null),
				create(Type.CREATE_CATEGORY, "category", null, "part", "Inhaltliche Leistung", "Anweisung", null),
				create(Type.CREATE_TASK, "task", null, "category", "Aufgabe 1", "Bearbeite die Quelle.", 6),
				create(Type.CREATE_MATERIAL, "material", null, "part", "Quelle 1", "> [!NUMBERED]\n> Erste Zeile", null),
				footer("Viel Erfolg!")));

		assertThat(result.replayed()).isFalse();
		assertThat(result.revision()).isGreaterThan(0).isEqualTo(result.mutationRevision());
		assertThat(result.created()).extracting(ExamAuthoringResult.CreatedNode::clientKey)
				.containsExactly("part", "category", "task", "material");
		assertThat(result.changes().parts()).hasSize(1);
		assertThat(result.changes().categories()).hasSize(1);
		assertThat(result.changes().tasks()).extracting(LoeTask::descriptionMarkdown).containsExactly("Bearbeite die Quelle.");
		assertThat(result.changes().materials()).hasSize(1);
		assertThat(result.changes().footerMarkdown()).isEqualTo("Viel Erfolg!");
		assertThat(repository.findRequirementsByExamId(exam.id())).isEmpty();
		assertThat(result.diagnostics()).anyMatch(message -> message.startsWith("TASK_POINT_MISMATCH"));
	}

	@Test
	void addsMorePartsAndTasksIncrementallyAndReordersByIdsOrCreatedKeys() {
		final Exam exam = exam();
		final var initial = initial(exam, 4);
		final int categoryId = id(initial, "category");
		final int firstTaskId = id(initial, "task");
		final var added = apply(exam, "append", List.of(
				create(Type.CREATE_TASK, "secondTask", categoryId, null, "Aufgabe 2", "Zweite Aufgabe", 3),
				create(Type.CREATE_PART, "secondPart", null, null, "Klausurteil B", null, null),
				create(Type.CREATE_CATEGORY, "secondCategory", null, "secondPart", "Analyse", null, null),
				create(Type.CREATE_TASK, "thirdTask", null, "secondCategory", "Aufgabe 3", null, 2),
				reorder(Type.REORDER_TASKS, categoryId, List.of(new ExamNodeReference(null, "secondTask"), ref(firstTaskId)))));

		assertThat(repository.findPartsByExamId(exam.id())).extracting(LoePart::id)
				.containsExactly(id(initial, "part"), id(added, "secondPart"));
		assertThat(repository.findTasksByExamId(exam.id()).stream().filter(task -> task.categoryId() == categoryId))
				.extracting(LoeTask::id).containsExactly(id(added, "secondTask"), firstTaskId);
		assertThat(added.changes().orders()).anySatisfy(order -> {
			assertThat(order.type()).isEqualTo("TASK");
			assertThat(order.parentId()).isEqualTo(categoryId);
			assertThat(order.ids()).containsExactly(id(added, "secondTask"), firstTaskId);
		});
		assertThat(repository.findRequirementsByExamId(exam.id())).isEmpty();
	}

	@Test
	void updatesOnlySuppliedFieldsAndEmptyMarkdownExplicitlyClears() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int taskId = id(initial, "task");
		apply(exam, "title", List.of(update(Type.UPDATE_TASK, taskId, "Neuer Titel", null, null)));
		assertThat(repository.findTasksByExamId(exam.id()).getFirst())
				.satisfies(task -> {
					assertThat(task.title()).isEqualTo("Neuer Titel");
					assertThat(task.descriptionMarkdown()).isEqualTo("Task source");
					assertThat(task.maxPoints()).isEqualTo(6);
				});
		apply(exam, "clear", List.of(update(Type.UPDATE_TASK, taskId, null, "", null), footer("")));
		assertThat(repository.findTasksByExamId(exam.id()).getFirst().descriptionMarkdown()).isEmpty();
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEmpty();
	}

	@Test
	void createsRequirementsAgainstTasksCreatedInTheSameAtomicBatch() {
		final Exam exam = exam();
		final var result = authoring.applyContentChanges(exam.id(), guard.revision(exam.id()), "content-eh", List.of(
				create(Type.CREATE_PART, "part", null, null, "A", null, null),
				create(Type.CREATE_CATEGORY, "category", null, "part", "Inhalt", null, null),
				create(Type.CREATE_TASK, "task", null, "category", "Aufgabe", null, 6)),
				List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "requirement", null,
						"task", "[Erklärung](crit:1?points=2)", 6, false, null)));

		assertThat(result.changes().requirements()).hasSize(1);
		assertThat(result.changes().requirements().getFirst().taskId()).isEqualTo(id(result, "task"));
		assertThat(result.diagnostics()).anyMatch(message -> message.startsWith("CRITERION_POINT_MISMATCH"));
		assertThat(result.changes().criteria()).extracting(LoeCriterion::criterionKey).containsExactly("1");
	}

	@Test
	void rejectsUnknownParentsForwardKeysAndDuplicateKeysWithoutPartialWrites() {
		final Exam exam = exam();
		final long revision = guard.revision(exam.id());
		assertThatThrownBy(() -> apply(exam, "forward", List.of(
				create(Type.CREATE_CATEGORY, "category", null, "future", "Inhalt", null, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("earlier created PART");
		assertThatThrownBy(() -> apply(exam, "duplicate", List.of(
				create(Type.CREATE_PART, "same", null, null, "A", null, null),
				create(Type.CREATE_PART, "same", null, null, "B", null, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("clientKey must be unique");
		assertThatThrownBy(() -> apply(exam, "unknown", List.of(
				create(Type.CREATE_TASK, "task", Integer.MAX_VALUE, null, "Task", null, 3))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong to this exam");
		assertThat(repository.findPartsByExamId(exam.id())).isEmpty();
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
		assertThat(receipts(exam)).isZero();
	}

	@Test
	void rejectsForeignNodeIdsEvenWhenTheirTypeAndIdAreValid() {
		final Exam exam = exam();
		final Exam foreign = exam();
		final var foreignDesign = initial(foreign, 6);
		assertThatThrownBy(() -> apply(exam, "foreign", List.of(
				update(Type.UPDATE_TASK, id(foreignDesign, "task"), "Changed", null, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong to this exam");
		assertThatThrownBy(() -> apply(exam, "foreign-parent", List.of(
				create(Type.CREATE_TASK, null, id(foreignDesign, "category"), null, "Task", null, 3))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong to this exam");
		assertThat(repository.findTasksByExamId(foreign.id()).getFirst().title()).isEqualTo("Aufgabe 1");
	}

	@Test
	void rejectsIncompleteDuplicateAndForeignSiblingOrders() {
		final Exam exam = exam();
		final var initial = initial(exam, 4);
		final int category = id(initial, "category");
		final int first = id(initial, "task");
		final var added = apply(exam, "second", List.of(create(Type.CREATE_TASK, "second", category, null, "Zwei", null, 3)));
		final int second = id(added, "second");
		final long revision = guard.revision(exam.id());
		for (final List<ExamNodeReference> order : List.of(List.of(ref(first)), List.of(ref(first), ref(first)),
				List.of(ref(first), ref(Integer.MAX_VALUE)))) {
			assertThatThrownBy(() -> apply(exam, "bad-order-" + order.size() + "-" + order.hashCode(),
					List.of(reorder(Type.REORDER_TASKS, category, order)))).isInstanceOf(IllegalArgumentException.class);
		}
		assertThat(repository.findTasksByExamId(exam.id())).extracting(LoeTask::id).containsExactly(first, second);
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
	}

	@Test
	void reordersEachContentLevelAndRequirementsWithoutChangingIdentity() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int part = id(initial, "part");
		final int task = id(initial, "task");
		final var added = apply(exam, "siblings", List.of(
				create(Type.CREATE_PART, "part2", null, null, "B", null, null),
				create(Type.CREATE_CATEGORY, "category2", part, null, "Sprache", null, null),
				create(Type.CREATE_MATERIAL, "material2", part, null, "Quelle 2", "Zweite Quelle", null)));
		final var eh = eh(exam, "requirements", List.of(
				requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "r1", task, null, "Erstens", 2, false, null),
				requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "r2", task, null, "Zweitens", 2, false, null)));
		apply(exam, "content-order", List.of(
				reorder(Type.REORDER_PARTS, null, List.of(ref(id(added, "part2")), ref(part))),
				reorder(Type.REORDER_CATEGORIES, part, List.of(ref(id(added, "category2")), ref(id(initial, "category")))),
				reorder(Type.REORDER_MATERIALS, part, List.of(ref(id(added, "material2")), ref(id(initial, "material"))))));
		eh(exam, "eh-order", List.of(requirement(LevelOfExpectationsCommand.Type.REORDER_REQUIREMENTS, null,
				null, task, null, null, null, null, List.of(ref(id(eh, "r2")), ref(id(eh, "r1"))))));
		assertThat(repository.findPartsByExamId(exam.id())).extracting(LoePart::id).containsExactly(id(added, "part2"), part);
		assertThat(repository.findCategoriesByExamId(exam.id())).extracting(LoeCategory::id)
				.containsExactly(id(added, "category2"), id(initial, "category"));
		assertThat(repository.findMaterialsByExamId(exam.id())).extracting(LoeMaterial::id)
				.containsExactly(id(added, "material2"), id(initial, "material"));
		assertThat(repository.findRequirementsByExamId(exam.id())).extracting(LoeRequirement::id)
				.containsExactly(id(eh, "r2"), id(eh, "r1"));
	}

	@Test
	void requiresExplicitCascadeAcknowledgementAndReturnsDeletionImpact() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int part = id(initial, "part");
		final int task = id(initial, "task");
		eh(exam, "requirements", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "requirement", task, null, "[Beleg](crit:1)", 3, false, null)));
		final long revision = guard.revision(exam.id());
		assertThatThrownBy(() -> apply(exam, "delete-no-ack", List.of(delete(Type.DELETE_PART, part, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acknowledgeDeletion=true");
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
		final var removed = apply(exam, "delete-ack", List.of(delete(Type.DELETE_PART, part, true)));
		assertThat(removed.deletions()).singleElement().satisfies(impact -> {
			assertThat(impact.parts()).isEqualTo(1);
			assertThat(impact.categories()).isEqualTo(1);
			assertThat(impact.tasks()).isEqualTo(1);
			assertThat(impact.materials()).isEqualTo(1);
			assertThat(impact.requirements()).isEqualTo(1);
			assertThat(impact.criteria()).isEqualTo(1);
		});
		assertThat(repository.findPartsByExamId(exam.id())).isEmpty();
		assertThat(repository.findRequirementsByExamId(exam.id())).isEmpty();
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEqualTo("Original footer");
		assertThat(removed.changes().orders()).singleElement().satisfies(order -> assertThat(order.ids()).isEmpty());
	}

	@Test
	void rejectsOverAllocationAfterTheEntireBatchAndRollsBackContentAndReceipt() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int task = id(initial, "task");
		eh(exam, "full", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "requirement", task, null, "Requirement", 6, false, null)));
		final long revision = guard.revision(exam.id());
		final int receipts = receipts(exam);
		assertThatThrownBy(() -> apply(exam, "bad-budget", List.of(footer("Unsaved footer"),
				update(Type.UPDATE_TASK, task, null, null, 3))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("over-allocated");
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEqualTo("Original footer");
		assertThat(repository.findTasksByExamId(exam.id()).getFirst().maxPoints()).isEqualTo(6);
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
		assertThat(receipts(exam)).isEqualTo(receipts);
	}

	@Test
	void coordinatesBudgetAndRequirementChangesWithinOneBatch() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int task = id(initial, "task");
		final var eh = eh(exam, "full", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "requirement", task, null, "Requirement", 6, false, null)));
		final var result = authoring.applyContentChanges(exam.id(), guard.revision(exam.id()), "coordinated",
				List.of(update(Type.UPDATE_TASK, task, null, null, 3)),
				List.of(requirement(LevelOfExpectationsCommand.Type.UPDATE_REQUIREMENT, id(eh, "requirement"),
						null, null, null, null, 3, null, null)));
		assertThat(result.changes().tasks()).singleElement().satisfies(item -> assertThat(item.maxPoints()).isEqualTo(3));
		assertThat(result.changes().requirements()).singleElement().satisfies(item -> assertThat(item.maxPoints()).isEqualTo(3));
		assertThat(result.diagnostics()).noneMatch(message -> message.startsWith("TASK_POINT_MISMATCH"));
	}

	@Test
	void rejectsRequirementOverAllocationAndInvalidCriterionDefinitionsWithoutPartialWrites() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int task = id(initial, "task");
		final long revision = guard.revision(exam.id());
		assertThatThrownBy(() -> eh(exam, "too-many", List.of(
				requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "first", task, null, "First", 4, false, null),
				requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "second", task, null, "Second", 4, false, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("over-allocated");
		assertThatThrownBy(() -> eh(exam, "invalid-crit", List.of(
				requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "invalid", task, null,
						"[Unsicher](crit:1?points=?)", 3, false, null))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> eh(exam, "crit-over", List.of(
				requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "invalid", task, null,
						"[Zu viel](crit:1?points=4)", 3, false, null))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(repository.findRequirementsByExamId(exam.id())).isEmpty();
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
	}

	@Test
	void rejectsTaskPointsAboveTheGradingMaximumButAcceptsIncompleteAllocation() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		assertThat(initial.diagnostics()).anyMatch(message -> message.startsWith("EXAM_POINT_MISMATCH"));
		assertThatThrownBy(() -> apply(exam, "excess-total", List.of(
				update(Type.UPDATE_TASK, id(initial, "task"), null, null, 21))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grading scale maximum");
		assertThat(repository.findTasksByExamId(exam.id()).getFirst().maxPoints()).isEqualTo(6);
	}

	@Test
	void locksEveryContentOperationAfterAResultAndAllowsOnlySafeRequirementTextCorrections() {
		final Exam exam = exam();
		final var initial = initial(exam, 20);
		final int task = id(initial, "task");
		final var eh = eh(exam, "eh", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "requirement", task, null, "[Beleg](crit:1?points=20)", 20, false, null)));
		final int requirement = id(eh, "requirement");
		final Pupil pupil = pupils.save(new Pupil(null, "Authoring", "ResultLock", Lifecycle.ACTIVE));
		repository.saveRequirementResult(new LoeRequirementResult(requirement, pupil.id(), 4, ""));
		final int criterionId = repository.findActiveCriteriaByExamId(exam.id()).getFirst().id();
		for (final Type type : Type.values()) {
			assertThatThrownBy(() -> apply(exam, "locked-" + type, List.of(
					new ExamContentCommand(type, null, null, null, null, null, null, null, null, null))))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("locked");
		}
		final var correction = eh(exam, "correction", List.of(requirement(LevelOfExpectationsCommand.Type.UPDATE_REQUIREMENT,
				requirement, null, null, null, "Präzisierung: [Beleg erläutern](crit:1?points=20)", null, null, null)));
		assertThat(correction.changes().criteria()).singleElement().satisfies(criterion -> assertThat(criterion.id()).isEqualTo(criterionId));
		assertThatThrownBy(() -> eh(exam, "new-key", List.of(requirement(LevelOfExpectationsCommand.Type.UPDATE_REQUIREMENT,
				requirement, null, null, null, "[Anderes Kriterium](crit:2?points=20)", null, null, null))))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> eh(exam, "new-budget", List.of(requirement(LevelOfExpectationsCommand.Type.UPDATE_REQUIREMENT,
				requirement, null, null, null, null, 19, null, null))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aFirstCriterionResultAlsoLocksContentAndRequirementAdditions() {
		final Exam exam = exam();
		final var initial = initial(exam, 20);
		final int task = id(initial, "task");
		eh(exam, "eh", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "requirement", task, null, "[Beleg](crit:1?points=20)", 20, false, null)));
		final Pupil pupil = pupils.save(new Pupil(null, "Criterion", "AuthoringLock", Lifecycle.ACTIVE));
		repository.saveCriterionResult(new LoeCriterionResult(repository.findActiveCriteriaByExamId(exam.id()).getFirst().id(), pupil.id(), 0));
		assertThatThrownBy(() -> apply(exam, "footer-locked", List.of(footer("Changed")))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> eh(exam, "new-locked", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "new", task, null, "Additional", 0, true, null)))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void staleRevisionWritesFailWithoutSavingAnyCommandsOrRetryReceipt() {
		final Exam exam = exam();
		final long before = guard.revision(exam.id());
		initial(exam, 6);
		assertThatThrownBy(() -> authoring.applyContentChanges(exam.id(), before, "stale", List.of(footer("Stale footer"))))
				.isInstanceOf(ExamDesignConflictException.class);
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEqualTo("Original footer");
		assertThat(receipts(exam)).isEqualTo(1);
	}

	@Test
	void retryUsesStableIdsButReturnsCurrentCanonicalContentAndSeparateMutationRevision() {
		final Exam exam = exam();
		final long originalRevision = guard.revision(exam.id());
		final List<ExamContentCommand> commands = List.of(create(Type.CREATE_PART, "part", null, null, "Original title", null, null), footer("Sensitive authored footer"));
		final var first = authoring.applyContentChanges(exam.id(), originalRevision, "retry", commands);
		final int part = id(first, "part");
		apply(exam, "later", List.of(update(Type.UPDATE_PART, part, "Current title", null, null), footer("Current footer")));
		final long currentRevision = guard.revision(exam.id());
		final var retried = authoring.applyContentChanges(exam.id(), originalRevision, "retry", commands);
		assertThat(retried.replayed()).isTrue();
		assertThat(retried.created()).isEqualTo(first.created());
		assertThat(retried.revision()).isEqualTo(currentRevision);
		assertThat(retried.mutationRevision()).isEqualTo(first.revision());
		assertThat(retried.changes().parts()).singleElement().satisfies(item -> assertThat(item.title()).isEqualTo("Current title"));
		assertThat(retried.changes().footerMarkdown()).isEqualTo("Current footer");
		assertThat(guard.revision(exam.id())).isEqualTo(currentRevision);
		assertThat(repository.findPartsByExamId(exam.id())).hasSize(1);
		final String receipt = jdbc.queryForObject("select result_metadata from exam_design_mutation where exam_id=:exam and request_id='retry'",
				Map.of("exam", exam.id()), String.class);
		assertThat(new String(Base64.getDecoder().decode(receipt), StandardCharsets.UTF_8))
				.doesNotContain("Original title", "Sensitive authored footer", "Current title", "Current footer");
	}

	@Test
	void reusedRequestIdWithDifferentBodyOrExpectedRevisionIsRejected() {
		final Exam exam = exam();
		final long before = guard.revision(exam.id());
		final List<ExamContentCommand> commands = List.of(footer("First"));
		authoring.applyContentChanges(exam.id(), before, "same-id", commands);
		assertThatThrownBy(() -> authoring.applyContentChanges(exam.id(), before, "same-id", List.of(footer("Second"))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different mutation");
		assertThatThrownBy(() -> authoring.applyContentChanges(exam.id(), guard.revision(exam.id()), "same-id", commands))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different mutation");
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEqualTo("First");
	}

	@Test
	void boundsCommandsTextAndAggregatePartsAndRollsBackTheWholeBatch() {
		final Exam exam = exam();
		assertThatThrownBy(() -> apply(exam, "empty", List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> apply(exam, "long", List.of(footer("x".repeat(50_001)))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("50000");
		final List<ExamContentCommand> commands = new ArrayList<>();
		for (int index = 0; index < 21; index++) commands.add(create(Type.CREATE_PART, "part" + index, null, null, "Part " + index, null, null));
		assertThatThrownBy(() -> apply(exam, "too-many-parts", commands))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("node limit");
		assertThat(repository.findPartsByExamId(exam.id())).isEmpty();
		assertThat(guard.revision(exam.id())).isZero();
		assertThat(receipts(exam)).isZero();
	}

	@Test
	void boundsAndValidatesEveryLocalKeyBeforeHashingOrOpeningATransaction() {
		for (final String key : List.of("x".repeat(100_000), "", "1startsWithNumber", "not a key", "ümlaut")) {
			final List<ExamContentCommand> contentCommands = List.of(
					create(Type.CREATE_PART, key, null, null, "Part", null, null),
					create(Type.CREATE_CATEGORY, null, null, key, "Category", null, null),
					new ExamContentCommand(Type.REORDER_PARTS, null, null, null, null, null, null, null,
							List.of(new ExamNodeReference(null, key)), null));
			for (int index = 0; index < contentCommands.size(); index++) {
				final String field = List.of("clientKey", "parentKey", "order clientKey").get(index);
				final ExamContentCommand command = contentCommands.get(index);
				assertThatThrownBy(() -> authoring.applyContentChanges(Integer.MAX_VALUE, 0, "invalid-content-key", List.of(command)))
						.isInstanceOf(IllegalArgumentException.class).hasMessageContaining(field);
			}
			final List<LevelOfExpectationsCommand> requirementCommands = List.of(
					requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, key, 1, null, "", 1, false, null),
					requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, null, null, key, "", 1, false, null),
					requirement(LevelOfExpectationsCommand.Type.REORDER_REQUIREMENTS, null, null, 1, null, null, null, null,
							List.of(new ExamNodeReference(null, key))));
			for (int index = 0; index < requirementCommands.size(); index++) {
				final String field = List.of("clientKey", "taskKey", "order clientKey").get(index);
				final LevelOfExpectationsCommand command = requirementCommands.get(index);
				assertThatThrownBy(() -> authoring.applyLevelOfExpectationsChanges(Integer.MAX_VALUE, 0, "invalid-requirement-key", List.of(command)))
						.isInstanceOf(IllegalArgumentException.class).hasMessageContaining(field);
			}
		}
		final Exam exam = exam();
		final String maximumLengthKey = "x".repeat(64);
		final var result = apply(exam, "maximum-local-key", List.of(
				create(Type.CREATE_PART, maximumLengthKey, null, null, "Part", null, null),
				create(Type.CREATE_CATEGORY, "category", null, maximumLengthKey, "Category", null, null),
				new ExamContentCommand(Type.REORDER_PARTS, null, null, null, null, null, null, null,
						List.of(new ExamNodeReference(null, maximumLengthKey)), null)));
		assertThat(result.created()).extracting(ExamAuthoringResult.CreatedNode::clientKey)
				.containsExactly(maximumLengthKey, "category");
	}

	@Test
	void rejectsFieldsThatCouldPretendToReparentNodesOrClearByOmission() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		assertThatThrownBy(() -> apply(exam, "reparent", List.of(new ExamContentCommand(Type.UPDATE_TASK,
				id(initial, "task"), null, id(initial, "category"), null, "Title", null, null, null, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parentId is not allowed");
		assertThatThrownBy(() -> apply(exam, "empty-update", List.of(update(Type.UPDATE_TASK, id(initial, "task"), null, null, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one editable field");
		assertThatThrownBy(() -> apply(exam, "omitted-footer", List.of(footer(null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires markdown");
	}

	@Test
	void managedImagesCannotBePlacedInTasksOrFooterAndMissingMaterialImagesAreRejected() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final String image = "![Quelle](img:missing1?width=75)";
		assertThatThrownBy(() -> apply(exam, "task-image", List.of(update(Type.UPDATE_TASK, id(initial, "task"), null, image, null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only in Material");
		assertThatThrownBy(() -> apply(exam, "footer-image", List.of(footer(image))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only in Material");
		assertThatThrownBy(() -> apply(exam, "unknown-image", List.of(update(Type.UPDATE_MATERIAL, id(initial, "material"), null, image, null))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(repository.findMaterialsByExamId(exam.id()).getFirst().descriptionMarkdown()).isEqualTo("Material source");
	}

	@Test
	void archivedCoursesCannotReceiveAuthoringChangesOrBeReactivated() {
		final Exam exam = exam();
		jdbc.update("update course set lifecycle='INACTIVE' where id=:id", Map.of("id", exam.courseId()));
		assertThatThrownBy(() -> apply(exam, "archived", List.of(footer("Not saved"))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Archivierte");
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEmpty();
		assertThat(courses.findById(exam.courseId()).orElseThrow().lifecycle()).isEqualTo(Lifecycle.INACTIVE);
	}

	@Test
	void rejectsCriteriaOutsideRequirementsAndMalformedManagedImagesButAllowsLiteralCodeExamples() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int task = id(initial, "task");
		final int material = id(initial, "material");
		for (final ExamContentCommand command : List.of(
				update(Type.UPDATE_TASK, task, null, "[Kriterium](crit:1)", null),
				update(Type.UPDATE_MATERIAL, material, null, "[Kriterium](crit:1?points=2)", null),
				footer("[Kriterium](crit:1)"))) {
			assertThatThrownBy(() -> apply(exam, "invalid-context-" + command.type(), List.of(command)))
					.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only in EH requirements");
		}
		for (final String source : List.of("![Beschreibung](img:placeholder)",
				"![Beschreibung](img:missing1?width=101)", "![Beschreibung](img:missing1?width=0)",
				"![Beschreibung](img:missing1?width=75&extra=1)",
				"![Beschreibung](img:missing1?align=middle)", "![Beschreibung](img:missing1?align=CENTER)",
				"![Beschreibung](img:missing1?align=center&align=right)", "![](img:missing1)",
				"[Beschreibung](img:missing1)", "![Kriterium](crit:1)")) {
			assertThatThrownBy(() -> apply(exam, "invalid-image-" + Math.abs(source.hashCode()), List.of(
					update(Type.UPDATE_MATERIAL, material, null, source, null))))
					.isInstanceOf(IllegalArgumentException.class);
		}
		final String code = "Beispiel: `[Kriterium](crit:1)`.\n\n```markdown\n![Beispiel](img:placeholder)\n```";
		apply(exam, "literal-code", List.of(update(Type.UPDATE_TASK, task, null, code, null)));
		assertThat(repository.findTasksByExamId(exam.id()).getFirst().descriptionMarkdown()).isEqualTo(code);
	}

	@Test
	void firstResultWaitsForAnInFlightDesignMutationAndValidatesTheCommittedDesign() throws Exception {
		final Exam exam = exam();
		final var initial = initial(exam, 20);
		final int task = id(initial, "task");
		final var eh = eh(exam, "ready-eh", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "requirement", task, null, "Requirement", 20, false, null)));
		final int requirementId = id(eh, "requirement");
		final Pupil pupil = pupils.save(new Pupil(null, "Concurrent", "FirstResult" + exam.id(), Lifecycle.ACTIVE));
		final CountDownLatch designLocked = new CountDownLatch(1);
		final CountDownLatch releaseDesign = new CountDownLatch(1);
		final CountDownLatch resultAttempted = new CountDownLatch(1);
		try (var workers = Executors.newFixedThreadPool(2)) {
			try {
				final var editing = workers.submit(() -> guard.write(exam.id(), guard.revision(exam.id()), () -> {
					final LoeTask old = repository.findTasksByExamId(exam.id()).getFirst();
					repository.saveTask(new LoeTask(old.id(), old.categoryId(), old.title(), old.descriptionMarkdown(), 10, old.sortOrder()));
					final LoeRequirement oldRequirement = repository.findRequirementsByExamId(exam.id()).getFirst();
					repository.saveRequirement(new LoeRequirement(oldRequirement.id(), task, oldRequirement.descriptionMarkdown(), 10, false, oldRequirement.sortOrder()));
					designLocked.countDown();
					await(releaseDesign);
					return null;
				}));
				assertThat(designLocked.await(5, TimeUnit.SECONDS)).isTrue();
				final var enteringResult = workers.submit(() -> {
					resultAttempted.countDown();
					repository.saveRequirementResult(new LoeRequirementResult(requirementId, pupil.id(), 4, ""));
				});
				assertThat(resultAttempted.await(5, TimeUnit.SECONDS)).isTrue();
				assertThatThrownBy(() -> enteringResult.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
				releaseDesign.countDown();
				editing.get(5, TimeUnit.SECONDS);
				assertThatThrownBy(() -> enteringResult.get(5, TimeUnit.SECONDS))
						.isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
			} finally {
				releaseDesign.countDown();
			}
		}
		assertThat(repository.hasResultsForExam(exam.id())).isFalse();
		assertThat(repository.findTasksByExamId(exam.id()).getFirst().maxPoints()).isEqualTo(10);
	}

	@Test
	void anInFlightFirstResultSerializesAndInvalidatesAConcurrentContentEdit() throws Exception {
		final Exam exam = exam();
		final var initial = initial(exam, 20);
		final int task = id(initial, "task");
		final var eh = eh(exam, "ready-eh", List.of(requirement(LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT,
				null, "requirement", task, null, "Requirement", 20, false, null)));
		final int requirementId = id(eh, "requirement");
		final Pupil pupil = pupils.save(new Pupil(null, "Concurrent", "ContentEdit" + exam.id(), Lifecycle.ACTIVE));
		final long originalRevision = guard.revision(exam.id());
		final CountDownLatch resultLocked = new CountDownLatch(1);
		final CountDownLatch releaseResult = new CountDownLatch(1);
		final CountDownLatch editAttempted = new CountDownLatch(1);
		try (var workers = Executors.newFixedThreadPool(2)) {
			try {
				final var enteringResult = workers.submit(() -> guard.write(exam.id(), null, () -> {
					repository.saveRequirementResult(new LoeRequirementResult(requirementId, pupil.id(), 4, ""));
					resultLocked.countDown();
					await(releaseResult);
					return null;
				}));
				assertThat(resultLocked.await(5, TimeUnit.SECONDS)).isTrue();
				final var editing = workers.submit(() -> {
					editAttempted.countDown();
					return authoring.applyContentChanges(exam.id(), originalRevision, "concurrent-footer", List.of(footer("Stale footer")));
				});
				assertThat(editAttempted.await(5, TimeUnit.SECONDS)).isTrue();
				assertThatThrownBy(() -> editing.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
				releaseResult.countDown();
				enteringResult.get(5, TimeUnit.SECONDS);
				assertThatThrownBy(() -> editing.get(5, TimeUnit.SECONDS))
						.isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ExamDesignConflictException.class);
			} finally {
				releaseResult.countDown();
			}
		}
		assertThat(repository.hasResultsForExam(exam.id())).isTrue();
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEqualTo("Original footer");
		assertThat(jdbc.queryForObject("select count(*) from exam_design_mutation where exam_id=:id and request_id='concurrent-footer'",
				Map.of("id", exam.id()), Integer.class)).isZero();
		assertThatThrownBy(() -> apply(exam, "current-but-locked", List.of(footer("Not permitted"))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("locked");
	}

	@Test
	void guardedRollbackRestoresContentAndRevisionAndLeavesLaterWritesAvailable() {
		final Exam exam = exam();
		final long revision = guard.revision(exam.id());
		assertThatThrownBy(() -> guard.write(exam.id(), revision, () -> {
			repository.saveExamFooterMarkdown(exam.id(), "Transient content");
			throw new IllegalStateException("Rollback requested");
		})).isInstanceOf(IllegalStateException.class).hasMessage("Rollback requested");
		assertThat(repository.findExamFooterMarkdown(exam.id())).isEmpty();
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
		final var saved = apply(exam, "after-rollback", List.of(footer("Saved content")));
		assertThat(saved.changes().footerMarkdown()).isEqualTo("Saved content");
		assertThat(saved.revision()).isGreaterThan(revision);
	}

	@Test
	void movesTheLastImageReferenceBetweenMaterialsInOneBatchAndRestoresItOnFailure() {
		final Exam exam = exam();
		final var initial = initial(exam, 6);
		final int sourceId = id(initial, "material");
		final int partId = id(initial, "part");
		final var added = apply(exam, "image-target", List.of(create(Type.CREATE_MATERIAL, "target", partId, null, "Target", "", null)));
		final int targetId = id(added, "target");
		final String imageId = "batchimg";
		final LoeMaterialImage image = new LoeMaterialImage(imageId, ExamMutationExecutor.fingerprint("batch-image-fixture", exam.id()),
				"image/png", 2, 1, 3, new byte[] { 1, 2, 3 });
		final LoeMaterial source = repository.findMaterialsByExamId(exam.id()).stream().filter(item -> item.id() == sourceId).findFirst().orElseThrow();
		repository.saveMaterialWithImages(new LoeMaterial(sourceId, partId, source.title(), "![Quelle](img:" + imageId + ")", source.sortOrder()), List.of(image));
		final var moved = apply(exam, "move-image", List.of(update(Type.UPDATE_MATERIAL, sourceId, null, "", null),
				update(Type.UPDATE_MATERIAL, targetId, null, "![Verschobene Quelle ![Nur Text](img:placeholder)](img:" + imageId + ")", null)));
		assertThat(repository.findMaterialImagesByIds(List.of(imageId))).hasSize(1);
		assertThat(moved.changes().materials().stream().filter(item -> item.id() == targetId).findFirst().orElseThrow().descriptionMarkdown())
				.contains("img:" + imageId);
		final long revision = guard.revision(exam.id());
		assertThatThrownBy(() -> apply(exam, "move-missing-image", List.of(update(Type.UPDATE_MATERIAL, targetId, null, "", null),
				update(Type.UPDATE_MATERIAL, sourceId, null, "![Fehlende Quelle](img:missing1)", null))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
		assertThat(repository.findMaterialImagesByIds(List.of(imageId))).hasSize(1);
		assertThat(repository.findMaterialsByExamId(exam.id()).stream().filter(item -> item.id() == targetId).findFirst().orElseThrow().descriptionMarkdown())
				.contains("img:" + imageId);
	}

	private static void await(final CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test transaction");
		} catch (final InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Test transaction interrupted", exception);
		}
	}

	private Exam exam() {
		final int year = NEXT_YEAR.getAndIncrement();
		final GradingScale scale = scales.save(new GradingScale(null, "Authoring " + year, 20, Lifecycle.ACTIVE));
		final Course course = courses.save(new Course(null, SchoolClass.CLS_10A,
				subjects.findActive().stream().filter(subject -> subject.name().equals("Englisch")).findFirst().orElseThrow(),
				new SchoolYear(year), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, scale.id()));
		return exams.save(new Exam(null, course.id(), "Authoring " + year, LocalDate.of(year, 9, 1)));
	}

	private ExamAuthoringResult initial(final Exam exam, final int points) {
		return apply(exam, "initial", List.of(
				create(Type.CREATE_PART, "part", null, null, "Klausurteil A", null, null),
				create(Type.CREATE_CATEGORY, "category", null, "part", "Inhalt", "Category source", null),
				create(Type.CREATE_TASK, "task", null, "category", "Aufgabe 1", "Task source", points),
				create(Type.CREATE_MATERIAL, "material", null, "part", "Quelle 1", "Material source", null),
				footer("Original footer")));
	}

	private ExamAuthoringResult apply(final Exam exam, final String request, final List<ExamContentCommand> commands) {
		return authoring.applyContentChanges(exam.id(), guard.revision(exam.id()), request, commands);
	}

	private ExamAuthoringResult eh(final Exam exam, final String request, final List<LevelOfExpectationsCommand> commands) {
		return authoring.applyLevelOfExpectationsChanges(exam.id(), guard.revision(exam.id()), request, commands);
	}

	private static int id(final ExamAuthoringResult result, final String key) {
		return result.created().stream().filter(created -> key.equals(created.clientKey())).findFirst().orElseThrow().id();
	}

	private int receipts(final Exam exam) {
		return jdbc.queryForObject("select count(*) from exam_design_mutation where exam_id=:id", Map.of("id", exam.id()), Integer.class);
	}

	private static ExamContentCommand create(final Type type, final String key, final Integer parentId,
			final String parentKey, final String title, final String markdown, final Integer points) {
		return new ExamContentCommand(type, null, key, parentId, parentKey, title, markdown, points, null, null);
	}

	private static ExamContentCommand update(final Type type, final int id, final String title, final String markdown, final Integer points) {
		return new ExamContentCommand(type, id, null, null, null, title, markdown, points, null, null);
	}

	private static ExamContentCommand delete(final Type type, final int id, final Boolean acknowledge) {
		return new ExamContentCommand(type, id, null, null, null, null, null, null, null, acknowledge);
	}

	private static ExamContentCommand reorder(final Type type, final Integer parentId, final List<ExamNodeReference> order) {
		return new ExamContentCommand(type, null, null, parentId, null, null, null, null, order, null);
	}

	private static ExamContentCommand footer(final String markdown) {
		return new ExamContentCommand(Type.SET_FOOTER, null, null, null, null, null, markdown, null, null, null);
	}

	private static LevelOfExpectationsCommand requirement(final LevelOfExpectationsCommand.Type type, final Integer id,
			final String key, final Integer taskId, final String taskKey, final String markdown, final Integer points,
			final Boolean bonus, final List<ExamNodeReference> order) {
		return new LevelOfExpectationsCommand(type, id, key, taskId, taskKey, markdown, points, bonus, order, null);
	}

	private static ExamNodeReference ref(final int id) {
		return new ExamNodeReference(id, null);
	}
}
