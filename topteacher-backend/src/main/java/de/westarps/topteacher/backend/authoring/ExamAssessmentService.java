package de.westarps.topteacher.backend.authoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePointRules;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.RequirementAssessment;

/** Saves one pupil's targeted awards atomically under the shared exam lock. */
@Service
public class ExamAssessmentService {

	private final ExamRepository exams;
	private final GradingScaleRepository scales;
	private final PupilRepository pupils;
	private final LevelOfExpectationsRepository repository;
	private final ExamMutationExecutor mutations;
	public ExamAssessmentService(final ExamRepository exams, final GradingScaleRepository scales,
			final PupilRepository pupils, final LevelOfExpectationsRepository repository,
			final ExamMutationExecutor mutations) {
		this.exams = exams;
		this.scales = scales;
		this.pupils = pupils;
		this.repository = repository;
		this.mutations = mutations;
	}

	@Transactional
	public AssessmentMutation save(final int examId, final int pupilId, final long expectedRevision,
			final String requestId, final List<RequirementAssessment> assessments) {
		if (assessments == null || assessments.isEmpty() || assessments.size() > 500) {
			throw new IllegalArgumentException("assessments must contain 1 to 500 requirement updates");
		}
		final List<RequirementAssessment> changes = List.copyOf(assessments);
		for (final var change : changes) {
			if (change.requirementId() == null || change.requirementId() <= 0
					|| change.criterionAwards() != null && change.criterionAwards().size() > 500) {
				throw new IllegalArgumentException("Assessments need a positive requirementId and at most 500 criterion awards");
			}
			if (change.comment() != null && change.comment().length() > 2000) {
				throw new IllegalArgumentException("comment must not exceed 2000 characters");
			}
		}
		final var execution = mutations.execute(examId, expectedRevision, requestId, "SAVE_PUPIL_RESULT",
				ExamMutationExecutor.fingerprint("SAVE_PUPIL_RESULT", examId, pupilId, expectedRevision, changes), () -> {
					apply(examId, pupilId, changes);
					return pupilId;
				}, Object::toString, Integer::valueOf);
		return new AssessmentMutation(repository.designRevision(examId), execution.revision(), execution.replayed());
	}

	private void apply(final int examId, final int pupilId, final List<RequirementAssessment> changes) {
		if (!exams.hasPupil(examId, pupilId) || pupils.findById(pupilId)
				.filter(pupil -> pupil.lifecycle() == Lifecycle.ACTIVE).isEmpty()) {
			throw new IllegalArgumentException("Result writes require an active pupil assigned to this exam");
		}
		final var exam = exams.findById(examId).orElseThrow();
		final var scale = scales.findById(exam.gradingScaleId())
				.orElseThrow(() -> new IllegalStateException("The exam needs a grading scale"));
		final List<LoeRequirement> requirements = repository.findRequirementsByExamId(examId);
		if (!new LoePointRules(scale).validDesign(repository.findTasksByExamId(examId), requirements)) {
			throw new IllegalStateException("Result entry requires complete task and regular requirement allocations");
		}
		final Map<Integer, LoeRequirement> requirementsById = requirements.stream()
				.collect(Collectors.toMap(LoeRequirement::id, Function.identity()));
		final Map<Integer, LoeCriterion> criteriaById = repository.findActiveCriteriaByExamId(examId).stream()
				.collect(Collectors.toMap(LoeCriterion::id, Function.identity()));
		final Map<Integer, LoeRequirementResult> savedRequirements = repository
				.findRequirementResultsByExamAndPupil(examId, pupilId).stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, Function.identity()));
		final Map<Integer, Integer> criterionUnits = repository.findCriterionResultsByExamAndPupil(examId, pupilId)
				.stream().collect(Collectors.toMap(LoeCriterionResult::criterionId, LoeCriterionResult::pointUnits));
		final Set<Integer> touched = new HashSet<>();
		final List<LoeCriterionResult> awards = new ArrayList<>();
		final List<LoeRequirementResult> results = new ArrayList<>();
		for (final RequirementAssessment change : changes) {
			Objects.requireNonNull(change, "assessment");
			final LoeRequirement requirement = requirementsById.get(change.requirementId());
			if (requirement == null || !touched.add(requirement.id())) {
				throw new IllegalArgumentException("requirementId must be unique and belong to this exam");
			}
			if (change.freePointUnits() == null && change.criterionAwards() == null && change.comment() == null) {
				throw new IllegalArgumentException("An assessment must supply free points, criterion awards or a comment");
			}
			final Set<Integer> awarded = new HashSet<>();
			for (final var award : change.criterionAwards() == null
					? List.<RequirementAssessment.CriterionAward>of() : change.criterionAwards()) {
				final LoeCriterion criterion = criteriaById.get(award.criterionId());
				if (criterion == null || !criterion.requirementId().equals(requirement.id())
						|| !awarded.add(criterion.id()) || award.pointUnits() < 0
						|| award.pointUnits() > criterion.pointUnits()) {
					throw new IllegalArgumentException("Criterion awards must be unique, within their value, and belong to the requirement");
				}
				criterionUnits.put(criterion.id(), award.pointUnits());
				awards.add(new LoeCriterionResult(criterion.id(), pupilId, award.pointUnits()));
			}
			final List<LoeCriterion> criteria = criteriaById.values().stream()
					.filter(criterion -> criterion.requirementId().equals(requirement.id())).toList();
			final LoeRequirementResult saved = savedRequirements.get(requirement.id());
			final int savedFree = saved == null ? 0 : criteria.isEmpty() ? saved.pointUnits() : saved.adjustmentUnits();
			final int free = change.freePointUnits() == null ? savedFree : change.freePointUnits();
			final long raw = (long) free + criteria.stream()
					.mapToLong(criterion -> criterionUnits.getOrDefault(criterion.id(), 0)).sum();
			if (free < 0 || raw > (long) requirement.maxPoints() * 2) {
				throw new IllegalArgumentException("Free points plus criterion awards exceed requirement " + requirement.id());
			}
			results.add(new LoeRequirementResult(requirement.id(), pupilId, Math.toIntExact(raw),
					criteria.isEmpty() ? 0 : free,
					change.comment() == null ? saved == null ? "" : saved.comment() : change.comment()));
		}
		/* Validate the whole request before persisting even its first result. */
		awards.forEach(repository::saveCriterionResult);
		results.forEach(repository::saveRequirementResult);
	}

	public record AssessmentMutation(long revision, long mutationRevision, boolean replayed) {
	}
}
