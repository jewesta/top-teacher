package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.westarps.topteacher.backend.authoring.ExamAuthoringService;
import de.westarps.topteacher.backend.authoring.ExamAuthoringService.AuthoringLimits;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ExamDefinitionView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.GradingScaleView;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.loe.ExamAuthoringResult;
import de.westarps.topteacher.model.loe.ExamContentCommand;
import de.westarps.topteacher.model.loe.LevelOfExpectationsCommand;

/** Pupil-facing exam authoring; requirements are owned by the separate EH boundary. */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamDesignMcpTools {

	private final ExamDesignMcpSupport support;
	private final ExamAuthoringService authoring;
	private final CourseRepository courses;
	private final GradingScaleRepository gradingScales;

	public ExamDesignMcpTools(final ExamDesignMcpSupport support, final ExamAuthoringService authoring,
			final CourseRepository courses, final GradingScaleRepository gradingScales) {
		this.support = Objects.requireNonNull(support, "support");
		this.authoring = Objects.requireNonNull(authoring, "authoring");
		this.courses = Objects.requireNonNull(courses, "courses");
		this.gradingScales = Objects.requireNonNull(gradingScales, "gradingScales");
	}

	@McpTool(name = "get_exam", title = "Get a pupil-facing exam definition",
			description = "Read metadata, ordered Aufgaben and separate part materials, closing text, task budgets, image metadata, revision, edit permissions and readiness findings. Does not return pupils, EH content, results or image bytes. Read get_markdown_guide before authoring unfamiliar TT Markdown.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamDefinitionView getExam(@McpToolParam(description = "Exam ID from list_exams.") final int examId) {
		return support.read(examId, support::examView);
	}

	@McpTool(name = "get_exam_authoring_options", title = "Get exam authoring options",
			description = "List active grading scales with ranges and supported authoring commands. Optionally show the active course's inherited grading scale. Archived scales are not offered for new assignments.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public AuthoringOptions getExamAuthoringOptions(@McpToolParam(required = false,
			description = "Optional active course ID. Omit for active grading scales across the installation.") final Integer courseId) {
		McpAccessContext.accessLevel();
		final Course course = courseId == null ? null : courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		if (course != null && course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived courses are historical and cannot receive new exams.");
		}
		return new AuthoringOptions(courseId, course == null ? null : course.gradingScaleId(),
				gradingScales.findActive().stream().map(support::gradingScaleView).toList(),
				List.of(ExamContentCommand.Type.values()).stream().map(Enum::name).toList(),
				List.of(LevelOfExpectationsCommand.Type.values()).stream().map(Enum::name).toList(), ExamAuthoringService.limits());
	}

	@McpTool(name = "apply_exam_content_changes", title = "Apply exam content changes",
			description = "Atomically create, update, explicitly delete or reorder parts, categories, Aufgaben and materials, or SET_FOOTER. Build initial content or add only a new subtree using clientKey/parentKey; existing nodes are never implicitly replaced. Updates use stable IDs; omitted fields are unchanged and empty Markdown clears text. Sibling reorders list every surviving sibling once by ID or new clientKey. Cascading deletion requires acknowledgeDeletion=true. No EH is created unless explicitly supplied in optional requirementChanges against existing tasks. Use current revision and a unique requestId; retry identical requests with the same key. Read get_markdown_guide for Material-only img: and EH-only crit: declarations. Results lock content, materials and footer.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public ExamAuthoringResult applyExamContentChanges(
			@McpToolParam(description = "Target exam ID.") final int examId,
			@McpToolParam(description = "Latest exam-design revision from get_exam or an authoring result.") final long expectedRevision,
			@McpToolParam(description = "Unique mutation key. Reuse only for an identical request retry.") final String requestId,
			@McpToolParam(description = "Ordered typed commands. Creation parents use parentId or earlier parentKey. Read get_exam_authoring_options for command types.") final List<ExamContentCommand> commands,
			@McpToolParam(required = false, description = "Optional explicit EH changes in the same transaction, for example adjusting existing requirements while lowering a task budget. Omit for content-only authoring.") final List<LevelOfExpectationsCommand> requirementChanges) {
		McpAccessContext.accessLevel();
		return authoring.applyContentChanges(examId, expectedRevision, requestId, commands,
				requirementChanges == null ? List.of() : requirementChanges);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record AuthoringOptions(@Nullable Integer courseId, @Nullable Integer defaultGradingScaleId,
			List<GradingScaleView> gradingScales, List<String> contentCommandTypes,
			List<String> levelOfExpectationsCommandTypes, AuthoringLimits limits) {
	}
}
