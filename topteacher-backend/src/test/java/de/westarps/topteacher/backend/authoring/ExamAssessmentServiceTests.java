package de.westarps.topteacher.backend.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.RequirementAssessment;
import de.westarps.topteacher.model.loe.RequirementAssessment.CriterionAward;

@SpringBootTest
class ExamAssessmentServiceTests {

	@Autowired private ExamAssessmentService assessments;
	@Autowired private ExamDesignGuard guard;
	@Autowired private LevelOfExpectationsRepository levels;
	@Autowired private ExamRepository exams;
	@Autowired private CourseRepository courses;
	@Autowired private GradingScaleRepository scales;
	@Autowired private SubjectRepository subjects;
	@Autowired private PupilRepository pupils;
	@Autowired private JdbcTemplate jdbc;

	@Test
	void savesHalfPointCriterionAndFreeAwardsAndPreservesOmittedFields() {
		final Fixture fixture = fixture();
		final long revision = guard.revision(fixture.exam().id());
		final var saved = save(fixture, revision, "initial", List.of(
				new RequirementAssessment(fixture.criterionRequirement().id(), 1,
						List.of(new CriterionAward(fixture.firstCriterion().id(), 3),
								new CriterionAward(fixture.secondCriterion().id(), 2)), "Criterion comment"),
				new RequirementAssessment(fixture.freeRequirement().id(), 3, null, "Free comment")));

		assertThat(saved.replayed()).isFalse();
		assertThat(saved.revision()).isGreaterThan(revision).isEqualTo(saved.mutationRevision());
		assertThat(result(fixture, fixture.criterionRequirement().id())).satisfies(result -> {
			assertThat(result.pointUnits()).isEqualTo(6);
			assertThat(result.adjustmentUnits()).isEqualTo(1);
			assertThat(result.comment()).isEqualTo("Criterion comment");
		});
		assertThat(result(fixture, fixture.freeRequirement().id())).satisfies(result -> {
			assertThat(result.pointUnits()).isEqualTo(3);
			assertThat(result.adjustmentUnits()).isZero();
		});

		save(fixture, saved.revision(), "targeted", List.of(
				new RequirementAssessment(fixture.criterionRequirement().id(), null,
						List.of(new CriterionAward(fixture.firstCriterion().id(), 1)), null),
				new RequirementAssessment(fixture.freeRequirement().id(), null, null, "")));

		assertThat(result(fixture, fixture.criterionRequirement().id())).satisfies(result -> {
			assertThat(result.pointUnits()).isEqualTo(4);
			assertThat(result.adjustmentUnits()).isEqualTo(1);
			assertThat(result.comment()).isEqualTo("Criterion comment");
		});
		assertThat(result(fixture, fixture.freeRequirement().id())).satisfies(result -> {
			assertThat(result.pointUnits()).isEqualTo(3);
			assertThat(result.comment()).isEmpty();
		});
		assertThat(levels.findCriterionResultsByExamAndPupil(fixture.exam().id(), fixture.pupil().id()))
				.anySatisfy(result -> {
					assertThat(result.criterionId()).isEqualTo(fixture.secondCriterion().id());
					assertThat(result.pointUnits()).isEqualTo(2);
				});
	}

	@Test
	void replaysWithoutReapplyingAndReturnsCurrentAndOriginalMutationRevisions() {
		final Fixture fixture = fixture();
		final long originalRevision = guard.revision(fixture.exam().id());
		final List<RequirementAssessment> original = List.of(
				new RequirementAssessment(fixture.freeRequirement().id(), 1, null, "First"));
		final var first = save(fixture, originalRevision, "retry", original);
		final var second = save(fixture, first.revision(), "later", List.of(
				new RequirementAssessment(fixture.freeRequirement().id(), 3, null, "Later")));
		final var replay = save(fixture, originalRevision, "retry", original);

		assertThat(replay.replayed()).isTrue();
		assertThat(replay.revision()).isEqualTo(second.revision());
		assertThat(replay.mutationRevision()).isEqualTo(first.mutationRevision()).isLessThan(replay.revision());
		assertThat(result(fixture, fixture.freeRequirement().id()).pointUnits()).isEqualTo(3);
		assertThat(result(fixture, fixture.freeRequirement().id()).comment()).isEqualTo("Later");
		assertThat(receipts(fixture)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select result_metadata from exam_design_mutation where exam_id = ? and request_id = ?",
				String.class, fixture.exam().id(), "retry")).isEqualTo(fixture.pupil().id().toString());
		assertThatThrownBy(() -> save(fixture, originalRevision, "retry", List.of(
				new RequirementAssessment(fixture.freeRequirement().id(), 2, null, "Changed"))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different mutation");
		assertThat(guard.revision(fixture.exam().id())).isEqualTo(second.revision());
	}

	@Test
	void rejectsStaleWritesAndUnknownExamsWithoutResultsOrReceipts() {
		final Fixture fixture = fixture();
		final long oldRevision = guard.revision(fixture.exam().id());
		levels.saveExamFooterMarkdown(fixture.exam().id(), "User edit");
		final long revision = guard.revision(fixture.exam().id());
		assertThatThrownBy(() -> save(fixture, oldRevision, "stale", List.of(zero(fixture))))
				.isInstanceOf(ExamDesignConflictException.class);
		assertThatThrownBy(() -> assessments.save(Integer.MAX_VALUE, fixture.pupil().id(), 0, "unknown",
				List.of(zero(fixture)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
		assertUnchanged(fixture, revision);
	}

	@Test
	void rejectsUnassignedAndInactivePupilsWithoutPartialWrites() {
		final Fixture fixture = fixture();
		final Pupil unassigned = pupils.save(new Pupil(null, "Unassigned", UUID.randomUUID().toString(), Lifecycle.ACTIVE));
		courses.assignPupil(fixture.course().id(), unassigned.id());
		final long revision = guard.revision(fixture.exam().id());
		assertThatThrownBy(() -> assessments.save(fixture.exam().id(), unassigned.id(), revision, "unassigned",
				List.of(zero(fixture)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active pupil assigned");
		pupils.save(new Pupil(fixture.pupil().id(), fixture.pupil().name(), fixture.pupil().surname(), Lifecycle.INACTIVE));
		assertThatThrownBy(() -> save(fixture, revision, "inactive", List.of(zero(fixture))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active pupil assigned");
		assertUnchanged(fixture, revision);
	}

	@Test
	void rejectsForeignRequirementAndCriterionIdsAndRollsBackTheWholeBatch() {
		final Fixture fixture = fixture();
		final Fixture foreign = fixture();
		final long revision = guard.revision(fixture.exam().id());
		final List<RequirementAssessment> invalid = List.of(
				new RequirementAssessment(foreign.freeRequirement().id(), 1, null, null),
				new RequirementAssessment(fixture.criterionRequirement().id(), null,
						List.of(new CriterionAward(foreign.firstCriterion().id(), 1)), null),
				new RequirementAssessment(fixture.freeRequirement().id(), null,
						List.of(new CriterionAward(fixture.firstCriterion().id(), 1)), null));
		for (final RequirementAssessment change : invalid) {
			final RequirementAssessment prefix = new RequirementAssessment(
					change.requirementId().equals(fixture.freeRequirement().id()) ? fixture.criterionRequirement().id()
							: fixture.freeRequirement().id(), 0, null, "");
			assertThatThrownBy(() -> save(fixture, revision, UUID.randomUUID().toString(), List.of(prefix, change)))
					.isInstanceOf(IllegalArgumentException.class);
			assertUnchanged(fixture, revision);
		}
		assertThat(levels.findRequirementResultsByExamAndPupil(foreign.exam().id(), foreign.pupil().id())).isEmpty();
	}

	@Test
	void rejectsDuplicateRequirementAndCriterionAwardsAtomically() {
		final Fixture fixture = fixture();
		final long revision = guard.revision(fixture.exam().id());
		assertThatThrownBy(() -> save(fixture, revision, "duplicate-requirement", List.of(zero(fixture), zero(fixture))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requirementId must be unique");
		assertThatThrownBy(() -> save(fixture, revision, "duplicate-criterion", List.of(
				new RequirementAssessment(fixture.criterionRequirement().id(), null,
						List.of(new CriterionAward(fixture.firstCriterion().id(), 1),
								new CriterionAward(fixture.firstCriterion().id(), 2)), null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Criterion awards must be unique");
		assertUnchanged(fixture, revision);
	}

	@Test
	void enforcesAwardCommentAndBatchLimitsBeforePersistingAnyResult() {
		final Fixture fixture = fixture();
		final long revision = guard.revision(fixture.exam().id());
		final List<RequirementAssessment> invalid = List.of(
				new RequirementAssessment(fixture.criterionRequirement().id(), -1, null, null),
				new RequirementAssessment(fixture.freeRequirement().id(), 13, null, null),
				new RequirementAssessment(fixture.criterionRequirement().id(), null,
						List.of(new CriterionAward(fixture.firstCriterion().id(), 4)), null),
				new RequirementAssessment(fixture.criterionRequirement().id(), null,
						List.of(new CriterionAward(fixture.firstCriterion().id(), -1)), null),
				new RequirementAssessment(fixture.criterionRequirement().id(), 1,
						List.of(new CriterionAward(fixture.firstCriterion().id(), 3),
								new CriterionAward(fixture.secondCriterion().id(), 5)), null),
				new RequirementAssessment(fixture.criterionRequirement().id(), null, null, "x".repeat(2_001)),
				new RequirementAssessment(fixture.criterionRequirement().id(), null, null, null));
		for (final RequirementAssessment change : invalid) {
			assertThatThrownBy(() -> save(fixture, revision, UUID.randomUUID().toString(), List.of(change)))
					.isInstanceOf(IllegalArgumentException.class);
			assertUnchanged(fixture, revision);
		}
		assertThatThrownBy(() -> save(fixture, revision, "empty", List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> save(fixture, revision, "oversize", IntStream.range(0, 501)
				.mapToObj(index -> zero(fixture)).toList())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> save(fixture, revision, "oversize-awards", List.of(
				new RequirementAssessment(fixture.criterionRequirement().id(), null, IntStream.range(0, 501)
						.mapToObj(index -> new CriterionAward(fixture.firstCriterion().id(), 0)).toList(), null))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500 criterion awards");
		assertUnchanged(fixture, revision);
	}

	@Test
	void requiresCompleteTaskAndRequirementAllocationAndAnActiveCourse() {
		final Fixture incomplete = fixture();
		final LoeTask task = levels.findTasksByExamId(incomplete.exam().id()).getFirst();
		levels.saveTask(new LoeTask(task.id(), task.categoryId(), task.title(), task.descriptionMarkdown(), 3, task.sortOrder()));
		final long incompleteRevision = guard.revision(incomplete.exam().id());
		assertThatThrownBy(() -> save(incomplete, incompleteRevision, "incomplete", List.of(zero(incomplete))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("complete task");
		assertUnchanged(incomplete, incompleteRevision);
		final Fixture archived = fixture();
		final Course course = archived.course();
		courses.save(new Course(course.id(), course.schoolClass(), course.subject(), course.schoolYear(),
				course.coursePeriod(), Lifecycle.INACTIVE, course.gradingScaleId()));
		final long archivedRevision = guard.revision(archived.exam().id());
		assertThatThrownBy(() -> save(archived, archivedRevision, "archived", List.of(zero(archived))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Archivierte");
		assertUnchanged(archived, archivedRevision);
	}

	@Test
	void firstZeroPointResultLocksAllPupilContentButPermitsSafeRequirementTextCorrection() {
		final Fixture fixture = fixture();
		final var saved = save(fixture, guard.revision(fixture.exam().id()), "zero", List.of(zero(fixture)));
		assertThat(levels.hasResultsForExam(fixture.exam().id())).isTrue();
		assertThatThrownBy(() -> levels.saveExamFooterMarkdown(fixture.exam().id(), "Forbidden"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> levels.saveMaterial(new LoeMaterial(null, fixture.part().id(), "Forbidden", "", 0)))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> levels.savePart(new LoePart(null, fixture.exam().id(), "Forbidden", 1)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(guard.revision(fixture.exam().id())).isEqualTo(saved.revision());
		final LoeRequirement requirement = fixture.criterionRequirement();
		levels.saveRequirement(new LoeRequirement(requirement.id(), requirement.taskId(),
				"[Precise evidence](crit:1?points=1.5) [Reasoning](crit:2?points=2.5)", 4, false, requirement.sortOrder()));
		assertThat(levels.findActiveCriteriaByExamId(fixture.exam().id())).anySatisfy(criterion -> {
			assertThat(criterion.id()).isEqualTo(fixture.firstCriterion().id());
			assertThat(criterion.label()).isEqualTo("Precise evidence");
		});
		assertThat(result(fixture, fixture.freeRequirement().id()).pointUnits()).isZero();
	}

	private ExamAssessmentService.AssessmentMutation save(final Fixture fixture, final long revision,
			final String requestId, final List<RequirementAssessment> changes) {
		return assessments.save(fixture.exam().id(), fixture.pupil().id(), revision, requestId, changes);
	}

	private RequirementAssessment zero(final Fixture fixture) {
		return new RequirementAssessment(fixture.freeRequirement().id(), 0, null, "");
	}

	private LoeRequirementResult result(final Fixture fixture, final int requirementId) {
		return levels.findRequirementResultsByExamAndPupil(fixture.exam().id(), fixture.pupil().id()).stream()
				.filter(result -> result.requirementId() == requirementId).findFirst().orElseThrow();
	}

	private int receipts(final Fixture fixture) {
		return jdbc.queryForObject("select count(*) from exam_design_mutation where exam_id = ?", Integer.class,
				fixture.exam().id());
	}

	private void assertUnchanged(final Fixture fixture, final long revision) {
		assertThat(guard.revision(fixture.exam().id())).isEqualTo(revision);
		assertThat(receipts(fixture)).isZero();
		assertThat(levels.findRequirementResultsByExamAndPupil(fixture.exam().id(), fixture.pupil().id())).isEmpty();
		assertThat(levels.findCriterionResultsByExamAndPupil(fixture.exam().id(), fixture.pupil().id())).isEmpty();
	}

	private Fixture fixture() {
		final String unique = UUID.randomUUID().toString();
		final Subject subject = subjects.save(new Subject(null, "Assessment " + unique, Lifecycle.ACTIVE));
		final GradingScale scale = scales.save(new GradingScale(null, "Assessment " + unique, 10, Lifecycle.ACTIVE));
		final Course course = courses.save(new Course(null, SchoolClass.CLS_Q2, subject, new SchoolYear(2077),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, scale.id()));
		final Pupil pupil = pupils.save(new Pupil(null, "Assessment", unique, Lifecycle.ACTIVE));
		courses.assignPupil(course.id(), pupil.id());
		final Exam exam = exams.save(new Exam(null, course.id(), "Assessment", LocalDate.of(2077, 5, 1)), List.of(pupil.id()));
		final LoePart part = levels.savePart(new LoePart(null, exam.id(), "Part", 0));
		final LoeCategory category = levels.saveCategory(new LoeCategory(null, part.id(), "Tasks", "", 0));
		final LoeTask criterionTask = levels.saveTask(new LoeTask(null, category.id(), "Evidence", "", 4, 0));
		final LoeTask freeTask = levels.saveTask(new LoeTask(null, category.id(), "Explain", "", 6, 1));
		final LoeRequirement criterionRequirement = levels.saveRequirement(new LoeRequirement(null, criterionTask.id(),
				"[Evidence](crit:1?points=1.5) [Reasoning](crit:2?points=2.5)", 4, false, 0));
		final LoeRequirement freeRequirement = levels.saveRequirement(new LoeRequirement(null, freeTask.id(), "Explain", 6, false, 0));
		final List<LoeCriterion> criteria = levels.findActiveCriteriaByExamId(exam.id());
		return new Fixture(course, exam, pupil, part, criterionRequirement, freeRequirement,
				criteria.stream().filter(criterion -> criterion.criterionKey().equals("1")).findFirst().orElseThrow(),
				criteria.stream().filter(criterion -> criterion.criterionKey().equals("2")).findFirst().orElseThrow());
	}

	private record Fixture(Course course, Exam exam, Pupil pupil, LoePart part, LoeRequirement criterionRequirement,
			LoeRequirement freeRequirement, LoeCriterion firstCriterion, LoeCriterion secondCriterion) {
	}
}
