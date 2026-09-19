package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.mcp.ExamMcpTools.PupilView;
import de.westarps.topteacher.mcp.ExamPupilWriter.RemovalResult;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

/**
 * MCP operation for safely removing pupil assignments from an exam.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamPupilRemovalMcpTools {

	private static final List<String> ALLOWED_ACTIONS = List.of(LockedPupilAction.SKIP.name(),
			LockedPupilAction.CANCEL.name());

	private final CourseRepository courses;
	private final ExamRepository exams;
	private final ExamPupilWriter writer;

	public ExamPupilRemovalMcpTools(final CourseRepository courses, final ExamRepository exams,
			final ExamPupilWriter writer) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.exams = Objects.requireNonNull(exams, "exams");
		this.writer = Objects.requireNonNull(writer, "writer");
	}

	@McpTool(name = "remove_pupils_from_exam", title = "Remove pupils from an exam",
			description = "Remove pupil assignments from an exam in an active course using pupil IDs from list_exam_pupils. TopTeacher's existing integrity rule prevents removing pupils who already have results in the exam. If locked pupils are found, choose SKIP to keep them and remove the other requested pupils, or CANCEL to remove nobody. If the client supports MCP elicitation, TopTeacher asks there; otherwise this tool returns NEEDS_RESOLUTION without changing the database. Pupil records, course assignments, and results are never changed.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public ExamPupilRemovalResult removePupilsFromExam(final McpSyncRequestContext context,
			@McpToolParam(description = "Exam ID obtained from list_exams.") final int examId,
			@McpToolParam(
					description = "Pupil IDs obtained from list_exam_pupils. Duplicate IDs are ignored.") final List<Integer> pupilIds,
			@McpToolParam(required = false,
					description = "Decision from a previous NEEDS_RESOLUTION result. Use SKIP to keep locked pupils and remove the others, or CANCEL to remove nobody.") final LockedPupilAction lockedPupilAction) {
		examInActiveCourse(examId);
		final List<Integer> requestedPupilIds = ExamWriteMcpTools.pupilIds(pupilIds, false);
		final Map<Integer, Pupil> assignedPupils = new LinkedHashMap<>();
		exams.findPupils(examId).forEach(pupil -> assignedPupils.put(pupil.id(), pupil));
		final List<Pupil> requestedAssignedPupils = requestedPupilIds.stream().map(assignedPupils::get)
				.filter(Objects::nonNull).toList();
		final List<Integer> notAssignedPupilIds = requestedPupilIds.stream()
				.filter(pupilId -> !assignedPupils.containsKey(pupilId)).toList();
		final Map<Integer, String> removalLocks = exams.findPupilRemovalLocks(examId);
		final List<LockedExamPupilView> lockedPupils = requestedAssignedPupils.stream()
				.filter(pupil -> removalLocks.containsKey(pupil.id()))
				.map(pupil -> lockedPupilView(pupil, removalLocks.get(pupil.id()))).toList();

		final LockedPupilAction action = resolveAction(context, lockedPupils, lockedPupilAction);
		if (!lockedPupils.isEmpty() && action == null) {
			return result(ExamPupilRemovalStatus.NEEDS_RESOLUTION,
					"No database changes were made. Ask the user whether locked pupils should be skipped or the entire removal should be cancelled, then call this tool again with lockedPupilAction.",
					examId, List.of(), lockedPupils, notAssignedPupilIds);
		}
		if (action == LockedPupilAction.CANCEL) {
			return result(ExamPupilRemovalStatus.CANCELLED,
					"The user cancelled pupil removal. No database changes were made.", examId, List.of(), lockedPupils,
					notAssignedPupilIds);
		}

		final Set<Integer> lockedPupilIds = lockedPupils.stream().map(LockedExamPupilView::id)
				.collect(java.util.stream.Collectors.toSet());
		final List<Integer> removablePupilIds = requestedAssignedPupils.stream().map(Pupil::id)
				.filter(pupilId -> !lockedPupilIds.contains(pupilId)).toList();
		if (removablePupilIds.isEmpty()) {
			return result(ExamPupilRemovalStatus.UNCHANGED,
					"Every requested pupil was locked or already absent. No database changes were made.", examId,
					List.of(), lockedPupils, notAssignedPupilIds);
		}

		final RemovalResult removal = writer.remove(examId, removablePupilIds);
		final List<Integer> combinedNotAssignedPupilIds = new ArrayList<>(notAssignedPupilIds);
		removal.notAssignedPupilIds().stream().filter(pupilId -> !combinedNotAssignedPupilIds.contains(pupilId))
				.forEach(combinedNotAssignedPupilIds::add);
		final ExamPupilRemovalStatus status = removal.removedPupils().isEmpty() ? ExamPupilRemovalStatus.UNCHANGED
				: ExamPupilRemovalStatus.REMOVED;
		final String message = removal.removedPupils().isEmpty()
				? "Every removable pupil was already absent. No database changes were made."
				: lockedPupils.isEmpty() ? "Pupils were removed from the exam in one transaction."
						: "Unlocked pupils were removed from the exam in one transaction; locked pupils were kept.";
		return result(status, message, examId, removal.removedPupils(), lockedPupils, combinedNotAssignedPupilIds);
	}

	private Exam examInActiveCourse(final int examId) {
		final Exam exam = exams.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		final Course course = courses.findById(exam.courseId())
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course exam can not change pupil assignments: " + examId);
		}
		return exam;
	}

	private static LockedPupilAction resolveAction(final McpSyncRequestContext context,
			final List<LockedExamPupilView> lockedPupils, final LockedPupilAction suppliedAction) {
		if (lockedPupils.isEmpty() || suppliedAction != null) {
			return suppliedAction;
		}
		if (context == null || !context.elicitEnabled()) {
			return null;
		}
		final ElicitResult result = context
				.elicit(ElicitFormRequest.builder(elicitationMessage(lockedPupils), elicitationSchema()).build());
		if (result.action() != ElicitResult.Action.ACCEPT) {
			return LockedPupilAction.CANCEL;
		}
		final Object actionValue = result.content() == null ? null : result.content().get("action");
		if (actionValue == null) {
			throw new IllegalArgumentException("Elicitation response did not contain action");
		}
		try {
			return LockedPupilAction.valueOf(actionValue.toString().toUpperCase());
		} catch (final IllegalArgumentException invalidAction) {
			throw new IllegalArgumentException("Unsupported exam pupil removal action: " + actionValue, invalidAction);
		}
	}

	private static Map<String, Object> elicitationSchema() {
		final Map<String, Object> action = new LinkedHashMap<>();
		action.put("type", "string");
		action.put("title", "Entscheidung");
		action.put("description",
				"SKIP = gesperrte Schüler:innen in der Klausur lassen und die übrigen entfernen, CANCEL = niemanden entfernen");
		action.put("enum", ALLOWED_ACTIONS);
		final Map<String, Object> requestedSchema = new LinkedHashMap<>();
		requestedSchema.put("type", "object");
		requestedSchema.put("properties", Map.of("action", action));
		requestedSchema.put("required", List.of("action"));
		requestedSchema.put("additionalProperties", false);
		return requestedSchema;
	}

	private static String elicitationMessage(final List<LockedExamPupilView> lockedPupils) {
		final StringBuilder message = new StringBuilder(
				"Für einige Schüler:innen sind bereits Ergebnisse in dieser Klausur erfasst: ");
		for (int index = 0; index < lockedPupils.size(); index++) {
			if (index > 0) {
				message.append("; ");
			}
			final LockedExamPupilView pupil = lockedPupils.get(index);
			message.append(pupil.name()).append(' ').append(pupil.surname()).append(" (ID ").append(pupil.id())
					.append(')');
		}
		return message.append(". Wähle SKIP oder CANCEL.").toString();
	}

	private static ExamPupilRemovalResult result(final ExamPupilRemovalStatus status, final String message,
			final int examId, final List<Pupil> removedPupils, final List<LockedExamPupilView> lockedPupils,
			final List<Integer> notAssignedPupilIds) {
		return new ExamPupilRemovalResult(status, message, examId,
				removedPupils.stream().map(ExamMcpTools::pupilView).toList(), lockedPupils, notAssignedPupilIds);
	}

	private static LockedExamPupilView lockedPupilView(final Pupil pupil, final String reason) {
		return new LockedExamPupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name(), reason,
				ALLOWED_ACTIONS);
	}

	public enum LockedPupilAction {
		SKIP,
		CANCEL
	}

	public enum ExamPupilRemovalStatus {
		REMOVED,
		UNCHANGED,
		NEEDS_RESOLUTION,
		CANCELLED
	}

	public record LockedExamPupilView(int id, String name, String surname, String lifecycle, String reason,
			List<String> allowedActions) {

		public LockedExamPupilView {
			allowedActions = List.copyOf(Objects.requireNonNull(allowedActions, "allowedActions"));
		}
	}

	public record ExamPupilRemovalResult(ExamPupilRemovalStatus status, String message, int examId,
			List<PupilView> removedPupils, List<LockedExamPupilView> lockedPupils, List<Integer> notAssignedPupilIds) {

		public ExamPupilRemovalResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			removedPupils = List.copyOf(Objects.requireNonNull(removedPupils, "removedPupils"));
			lockedPupils = List.copyOf(Objects.requireNonNull(lockedPupils, "lockedPupils"));
			notAssignedPupilIds = List.copyOf(Objects.requireNonNull(notAssignedPupilIds, "notAssignedPupilIds"));
		}
	}
}
