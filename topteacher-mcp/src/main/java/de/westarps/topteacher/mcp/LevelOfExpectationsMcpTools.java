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

import de.westarps.topteacher.backend.authoring.ExamAuthoringService;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.DesignDiagnostic;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ExamPermissions;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.PointSummaryView;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.loe.ExamAuthoringResult;
import de.westarps.topteacher.model.loe.LevelOfExpectationsCommand;
import de.westarps.topteacher.model.loe.LoeCategory;

/** EH requirements refer to the existing task tree; this boundary cannot author another hierarchy. */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class LevelOfExpectationsMcpTools {

	private final ExamDesignMcpSupport support;
	private final ExamAuthoringService authoring;

	public LevelOfExpectationsMcpTools(final ExamDesignMcpSupport support, final ExamAuthoringService authoring) {
		this.support = Objects.requireNonNull(support, "support");
		this.authoring = Objects.requireNonNull(authoring, "authoring");
	}

	@McpTool(name = "get_level_of_expectations", title = "Get a level of expectations",
			description = "Read EH requirements/criteria against existing task IDs, with read-only task context, revision, points, permissions and diagnostics. Content is authored only through apply_exam_content_changes; get_exam reads pupil-facing tasks/material/footer. Criterion keys and half-point units retain stable identities.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public LevelOfExpectationsView getLevelOfExpectations(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId) {
		return support.read(examId, snapshot -> {
			final Map<Integer, LoeCategory> categories = snapshot.categories().stream()
					.collect(Collectors.toMap(LoeCategory::id, Function.identity()));
			final List<TaskRequirementsView> tasks = snapshot.parts().stream()
					.flatMap(part -> snapshot.categories().stream().filter(category -> category.partId().equals(part.id())))
					.flatMap(category -> snapshot.tasks().stream().filter(task -> task.categoryId().equals(category.id())))
					.map(task -> {
				final LoeCategory category = categories.get(task.categoryId());
				return new TaskRequirementsView(task.id(), category.partId(), task.categoryId(), task.title(),
						task.descriptionMarkdown(), task.maxPoints(), task.sortOrder(),
						snapshot.requirements().stream().filter(requirement -> requirement.taskId().equals(task.id()))
								.map(requirement -> new RequirementView(requirement.id(), requirement.descriptionMarkdown(),
										requirement.maxPoints(), requirement.bonus(), requirement.sortOrder(),
										snapshot.criteria().stream().filter(criterion -> criterion.requirementId().equals(requirement.id()))
												.map(criterion -> new CriterionView(criterion.id(), criterion.criterionKey(),
														criterion.label(), criterion.pointUnits(), criterion.sortOrder())).toList()))
								.toList());
			}).toList();
			return new LevelOfExpectationsView(examId, snapshot.revision(), snapshot.permissions().correctionMode(),
					snapshot.permissions(), snapshot.points(), snapshot.diagnostics(), tasks);
		});
	}

	@McpTool(name = "apply_level_of_expectations_changes", title = "Apply level-of-expectations changes",
			description = "Atomically create, update, explicitly delete or reorder requirements against existing task IDs; no other hierarchy is created. Criteria derive from [label](crit:1?points=1.5) Markdown with stable positive-integer keys. Omitted fields stay unchanged; empty Markdown clears text. Reorder all surviving task requirements once by ID or new clientKey. Under-allocation produces diagnostics; over-allocation is rejected. Use current revision and a unique requestId, retrying identical requests with the same key. With results only corrections preserving criterion keys, points and other assessment semantics remain possible. Read get_markdown_guide with REQUIREMENT context.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public ExamAuthoringResult applyLevelOfExpectationsChanges(
			@McpToolParam(description = "Target exam ID.") final int examId,
			@McpToolParam(description = "Latest exam-design revision from get_exam, EH read or authoring result.") final long expectedRevision,
			@McpToolParam(description = "Unique mutation key. Reuse only for an identical request retry.") final String requestId,
			@McpToolParam(description = "Ordered typed requirement commands. Creation/reordering targets existing task IDs; criteria belong only in requirement Markdown.") final List<LevelOfExpectationsCommand> commands) {
		McpAccessContext.accessLevel();
		return authoring.applyLevelOfExpectationsChanges(examId, expectedRevision, requestId, commands);
	}

	public record LevelOfExpectationsView(int examId, long revision, boolean correctionMode,
			ExamPermissions permissions, PointSummaryView points, List<DesignDiagnostic> diagnostics,
			List<TaskRequirementsView> tasks) {
	}

	public record TaskRequirementsView(int taskId, int partId, int categoryId, String title,
			String descriptionMarkdown, int maxPoints, int sortOrder, List<RequirementView> requirements) {
	}

	public record RequirementView(int id, String descriptionMarkdown, int maxPoints, boolean bonus, int sortOrder,
			List<CriterionView> criteria) {
	}

	public record CriterionView(int id, String key, String label, int pointUnits, int sortOrder) {
	}
}
