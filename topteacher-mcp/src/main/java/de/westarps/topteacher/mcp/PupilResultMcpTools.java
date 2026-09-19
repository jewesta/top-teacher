package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.ExamMcpTools.PupilView;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePointRules;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;

/**
 * MCP operations for reading pupil results.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class PupilResultMcpTools {

	private final ExamRepository exams;
	private final GradingScaleRepository gradingScales;
	private final LevelOfExpectationsRepository levelOfExpectations;
	private final PupilRepository pupils;

	public PupilResultMcpTools(final ExamRepository exams, final GradingScaleRepository gradingScales,
			final LevelOfExpectationsRepository levelOfExpectations, final PupilRepository pupils) {
		this.exams = Objects.requireNonNull(exams, "exams");
		this.gradingScales = Objects.requireNonNull(gradingScales, "gradingScales");
		this.levelOfExpectations = Objects.requireNonNull(levelOfExpectations, "levelOfExpectations");
		this.pupils = Objects.requireNonNull(pupils, "pupils");
	}

	@McpTool(name = "get_pupil_result", title = "Get one pupil's exam result",
			description = "Get saved requirement points, comments, criterion states, totals, and grade for one pupil and exam.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public PupilResultView getPupilResult(
			@McpToolParam(description = "Exam ID returned by list_exams.") final int examId,
			@McpToolParam(description = "Pupil ID returned by list_exam_pupils.") final int pupilId) {
		final Exam exam = exams.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		if (!exams.hasPupil(examId, pupilId)) {
			throw new IllegalArgumentException("Pupil " + pupilId + " is not assigned to exam " + examId + ".");
		}
		final Pupil pupil = pupils.findById(pupilId)
				.orElseThrow(() -> new IllegalArgumentException("Pupil does not exist: " + pupilId));
		final List<LoeRequirement> requirements = levelOfExpectations.findRequirementsByExamId(examId);
		final List<LoeCriterion> criteria = levelOfExpectations.findActiveCriteriaByExamId(examId);
		final Map<Integer, LoeRequirementResult> requirementResults = levelOfExpectations
				.findRequirementResultsByExamAndPupil(examId, pupilId).stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, Function.identity()));
		final Map<Integer, LoeCriterionResult> criterionResults = levelOfExpectations
				.findCriterionResultsByExamAndPupil(examId, pupilId).stream()
				.collect(Collectors.toMap(LoeCriterionResult::criterionId, Function.identity()));
		final List<RequirementResultView> requirementViews = requirements.stream()
				.map(requirement -> requirementResultView(requirement, requirementResults.get(requirement.id())))
				.toList();
		final List<CriterionResultView> criterionViews = criteria.stream()
				.map(criterion -> criterionResultView(criterion, criterionResults.get(criterion.id()))).toList();
		return new PupilResultView(examId, ExamMcpTools.pupilView(pupil),
				resultSummary(exam, requirements, requirementResults), requirementViews, criterionViews);
	}

	private ResultSummaryView resultSummary(final Exam exam, final List<LoeRequirement> requirements,
			final Map<Integer, LoeRequirementResult> results) {
		final int regularPoints = requirements.stream().filter(requirement -> !requirement.bonus())
				.mapToInt(requirement -> achievedPoints(requirement, results)).sum();
		final int bonusPoints = requirements.stream().filter(LoeRequirement::bonus)
				.mapToInt(requirement -> achievedPoints(requirement, results)).sum();
		if (exam.gradingScaleId() == null) {
			return new ResultSummaryView(regularPoints, bonusPoints, null, "", false);
		}
		final GradingScale gradingScale = gradingScales.findById(exam.gradingScaleId()).orElse(null);
		if (gradingScale == null) {
			return new ResultSummaryView(regularPoints, bonusPoints, null, "", false);
		}
		final LoePointRules pointRules = new LoePointRules(gradingScale);
		if (!pointRules.regularMaxPointsMatch(requirements)) {
			return new ResultSummaryView(regularPoints, bonusPoints, null, "", false);
		}
		try {
			final int effectivePoints = pointRules.cappedAchievedTotal(requirements,
					requirement -> achievedPoints(requirement, results));
			final String grade = gradingScales.findRangesByGradingScaleId(gradingScale.id()).stream()
					.filter(range -> range.minPoints() <= effectivePoints && effectivePoints <= range.maxPoints())
					.findFirst().map(range -> range.gradeLevel().getShortName()).orElse("");
			return new ResultSummaryView(regularPoints, bonusPoints, effectivePoints, grade, !grade.isEmpty());
		} catch (final IllegalArgumentException invalidPoints) {
			return new ResultSummaryView(regularPoints, bonusPoints, null, "", false);
		}
	}

	private static int achievedPoints(final LoeRequirement requirement,
			final Map<Integer, LoeRequirementResult> results) {
		final LoeRequirementResult result = results.get(requirement.id());
		return result == null ? 0 : result.points();
	}

	private static RequirementResultView requirementResultView(final LoeRequirement requirement,
			final LoeRequirementResult result) {
		return new RequirementResultView(requirement.id(), result != null, result == null ? 0 : result.points(),
				result == null ? "" : result.comment());
	}

	private static CriterionResultView criterionResultView(final LoeCriterion criterion,
			final LoeCriterionResult result) {
		return new CriterionResultView(criterion.id(), criterion.requirementId(), criterion.criterionKey(),
				criterion.label(), result != null, result != null && result.achieved());
	}

	public record PupilResultView(int examId, PupilView pupil, ResultSummaryView summary,
			List<RequirementResultView> requirements, List<CriterionResultView> criteria) {

		public PupilResultView {
			Objects.requireNonNull(pupil, "pupil");
			Objects.requireNonNull(summary, "summary");
			requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
			criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
		}
	}

	public record ResultSummaryView(int regularPoints, int bonusPoints, Integer effectivePoints, String grade,
			boolean gradingAvailable) {
	}

	public record RequirementResultView(int requirementId, boolean saved, int points, String comment) {
	}

	public record CriterionResultView(int criterionId, int requirementId, String key, String label, boolean saved,
			boolean achieved) {
	}
}
