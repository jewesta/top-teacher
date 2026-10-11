package de.westarps.topteacher.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.authoring.ExamAssessmentService;
import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.loe.RequirementAssessment;

@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class PupilResultWriteMcpTools {

	private final ExamAssessmentService assessments;
	private final PupilResultMcpTools results;
	private final ExamDesignGuard guard;

	public PupilResultWriteMcpTools(final ExamAssessmentService assessments, final PupilResultMcpTools results,
			final ExamDesignGuard guard) {
		this.assessments = assessments;
		this.results = results;
		this.guard = guard;
	}

	@McpTool(name = "save_pupil_result", title = "Save one pupil's exam result",
			description = "Atomically save targeted requirement assessments by stable IDs, in either access mode. One pointUnit equals half a point (2 units = 1 point). freePointUnits is the total free-point component, not a delta; criterionAwards updates only listed criteria. Omitted fields and requirements retain saved values; an empty comment clears it. Free points plus criterion awards must fit each requirement. Result entry requires complete task/regular requirement allocations and an active assigned pupil. The first saved result, even zero points, locks pupil-facing content. Use revision from get_exam and a unique requestId; identical retries replay without applying again.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public ResultWriteView savePupilResult(
			@McpToolParam(description = "Exam ID from get_exam.") final int examId,
			@McpToolParam(description = "Existing assigned pupil ID from list_exam_pupils.") final int pupilId,
			@McpToolParam(description = "Current exam revision from get_exam.") final long expectedRevision,
			@McpToolParam(description = "Unique retry key, 1-100 letters, digits, dots, underscores, colons or hyphens.") final String requestId,
			@McpToolParam(description = "1-500 targeted assessments. Criterion IDs and requirement IDs come from get_level_of_expectations.") final List<RequirementAssessment> assessments) {
		McpAccessContext.accessLevel();
		final var mutation = this.assessments.save(examId, pupilId, expectedRevision, requestId, assessments);
		return guard.read(examId, () -> new ResultWriteView(guard.revision(examId), mutation.mutationRevision(),
				mutation.replayed(), results.getPupilResult(examId, pupilId)));
	}

	public record ResultWriteView(long revision, long mutationRevision, boolean replayed,
			PupilResultMcpTools.PupilResultView result) {
	}
}
