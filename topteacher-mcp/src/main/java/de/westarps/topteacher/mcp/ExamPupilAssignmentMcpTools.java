package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.mcp.ExamMcpTools.PupilView;
import de.westarps.topteacher.mcp.ExamPupilWriter.AssignmentResult;

/**
 * MCP operation for assigning existing course pupils to an exam.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamPupilAssignmentMcpTools {

	private final ExamPupilWriter writer;

	public ExamPupilAssignmentMcpTools(final ExamPupilWriter writer) {
		this.writer = Objects.requireNonNull(writer, "writer");
	}

	@McpTool(name = "assign_pupils_to_exam", title = "Assign pupils to an exam",
			description = "Assign existing active pupils to an exam in an active course. Pupil IDs must come from list_course_pupils for the exam's course. Archived pupils and pupils outside the course can not be assigned. Pupils already assigned are harmless no-ops. The batch is written in one transaction and never creates pupil records.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamPupilAssignmentResult assignPupilsToExam(
			@McpToolParam(description = "Exam ID obtained from list_exams.") final int examId, @McpToolParam(
					description = "Active pupil IDs obtained from list_course_pupils. Duplicate IDs are ignored.") final List<Integer> pupilIds) {
		final List<Integer> validatedPupilIds = ExamWriteMcpTools.pupilIds(pupilIds, false);
		final AssignmentResult assignment = writer.assign(examId, validatedPupilIds);
		final ExamPupilAssignmentStatus status = assignment.assignedPupils().isEmpty()
				? ExamPupilAssignmentStatus.UNCHANGED
				: ExamPupilAssignmentStatus.ASSIGNED;
		final String message = assignment.assignedPupils().isEmpty()
				? "Every requested pupil was already assigned. No database changes were made."
				: "Pupils were assigned to the exam in one transaction.";
		return new ExamPupilAssignmentResult(status, message, examId,
				assignment.assignedPupils().stream().map(ExamMcpTools::pupilView).toList(),
				assignment.alreadyAssignedPupils().stream().map(ExamMcpTools::pupilView).toList());
	}

	public enum ExamPupilAssignmentStatus {
		ASSIGNED,
		UNCHANGED
	}

	public record ExamPupilAssignmentResult(ExamPupilAssignmentStatus status, String message, int examId,
			List<PupilView> assignedPupils, List<PupilView> alreadyAssignedPupils) {

		public ExamPupilAssignmentResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			assignedPupils = List.copyOf(Objects.requireNonNull(assignedPupils, "assignedPupils"));
			alreadyAssignedPupils = List.copyOf(Objects.requireNonNull(alreadyAssignedPupils, "alreadyAssignedPupils"));
		}
	}
}
