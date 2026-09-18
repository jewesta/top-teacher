package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import de.westarps.topteacher.mcp.CourseMcpTools.CoursePupilView;
import de.westarps.topteacher.mcp.CourseMcpTools.CourseView;
import de.westarps.topteacher.mcp.CoursePupilRemovalWriter.RemovalResult;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

/**
 * MCP operation for safely removing pupils from an existing active course.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CoursePupilRemovalMcpTools {

	private static final int MAXIMUM_PUPILS = 200;
	private static final List<String> ALLOWED_ACTIONS = List.of(LockedPupilAction.SKIP.name(),
			LockedPupilAction.CANCEL.name());

	private final CourseRepository courses;
	private final CoursePupilRemovalWriter writer;

	public CoursePupilRemovalMcpTools(final CourseRepository courses, final CoursePupilRemovalWriter writer) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.writer = Objects.requireNonNull(writer, "writer");
	}

	@McpTool(name = "remove_pupils_from_course", title = "Remove pupils from an existing course",
			description = "Remove pupil assignments from an existing active course using pupil IDs from list_course_pupils. TopTeacher's existing integrity rule prevents removing any pupil assigned to an exam in this course, even when no results exist. If locked pupils are found, choose SKIP to keep them and remove the other requested pupils, or CANCEL to remove nobody. If the client supports MCP elicitation, TopTeacher asks there; otherwise this tool returns NEEDS_RESOLUTION without changing the database. Pupil records and exam assignments are never changed.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public CoursePupilRemovalResult removePupilsFromCourse(final McpSyncRequestContext context,
			@McpToolParam(description = "Active course ID obtained from list_courses.") final int courseId,
			@McpToolParam(
					description = "Pupil IDs obtained from list_course_pupils. Duplicate IDs are ignored.") final List<Integer> pupilIds,
			@McpToolParam(required = false,
					description = "Decision from a previous NEEDS_RESOLUTION result. Use SKIP to keep locked pupils and remove the others, or CANCEL to remove nobody.") final LockedPupilAction lockedPupilAction) {
		final Course course = activeCourse(courseId);
		final List<Integer> requestedPupilIds = validatePupilIds(pupilIds);
		final Map<Integer, Pupil> assignedPupils = assignedPupils(courseId);
		final List<Pupil> requestedAssignedPupils = requestedPupilIds.stream().map(assignedPupils::get)
				.filter(Objects::nonNull).toList();
		final List<Integer> notAssignedPupilIds = requestedPupilIds.stream()
				.filter(pupilId -> !assignedPupils.containsKey(pupilId)).toList();
		final Map<Integer, String> removalLocks = courses.findPupilRemovalLocks(courseId);
		final List<LockedCoursePupilView> lockedPupils = requestedAssignedPupils.stream()
				.filter(pupil -> removalLocks.containsKey(pupil.id()))
				.map(pupil -> lockedPupilView(pupil, removalLocks.get(pupil.id()))).toList();

		final LockedPupilAction action = resolveAction(context, lockedPupils, lockedPupilAction);
		if (!lockedPupils.isEmpty() && action == null) {
			return result(CoursePupilRemovalStatus.NEEDS_RESOLUTION,
					"No database changes were made. Ask the user whether locked pupils should be skipped or the entire removal should be cancelled, then call this tool again with lockedPupilAction.",
					course, List.of(), lockedPupils, notAssignedPupilIds);
		}
		if (action == LockedPupilAction.CANCEL) {
			return result(CoursePupilRemovalStatus.CANCELLED,
					"The user cancelled pupil removal. No database changes were made.", course, List.of(), lockedPupils,
					notAssignedPupilIds);
		}

		final Set<Integer> lockedPupilIds = lockedPupils.stream().map(LockedCoursePupilView::id)
				.collect(java.util.stream.Collectors.toSet());
		final List<Integer> removablePupilIds = requestedAssignedPupils.stream().map(Pupil::id)
				.filter(pupilId -> !lockedPupilIds.contains(pupilId)).toList();
		if (removablePupilIds.isEmpty()) {
			return result(CoursePupilRemovalStatus.UNCHANGED,
					"Every requested pupil was locked or already absent. No database changes were made.", course,
					List.of(), lockedPupils, notAssignedPupilIds);
		}

		final RemovalResult removed = writer.remove(courseId, removablePupilIds);
		final List<Integer> combinedNotAssignedPupilIds = new ArrayList<>(notAssignedPupilIds);
		removed.notAssignedPupilIds().stream().filter(pupilId -> !combinedNotAssignedPupilIds.contains(pupilId))
				.forEach(combinedNotAssignedPupilIds::add);
		final CoursePupilRemovalStatus status = removed.removedPupils().isEmpty() ? CoursePupilRemovalStatus.UNCHANGED
				: CoursePupilRemovalStatus.REMOVED;
		final String message = removed.removedPupils().isEmpty()
				? "Every removable pupil was already absent. No database changes were made."
				: lockedPupils.isEmpty() ? "Pupils were removed from the course in one transaction."
						: "Unlocked pupils were removed from the course in one transaction; locked pupils were kept.";
		return result(status, message, removed.course(), removed.removedPupils(), lockedPupils,
				combinedNotAssignedPupilIds);
	}

	private Course activeCourse(final int courseId) {
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course can not have pupil assignments removed: " + courseId);
		}
		return course;
	}

	private Map<Integer, Pupil> assignedPupils(final int courseId) {
		final Map<Integer, Pupil> assignedPupils = new LinkedHashMap<>();
		courses.findPupils(courseId).forEach(pupil -> assignedPupils.put(pupil.id(), pupil));
		return assignedPupils;
	}

	private static LockedPupilAction resolveAction(final McpSyncRequestContext context,
			final List<LockedCoursePupilView> lockedPupils, final LockedPupilAction suppliedAction) {
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
			throw new IllegalArgumentException("Unsupported course pupil removal action: " + actionValue,
					invalidAction);
		}
	}

	private static Map<String, Object> elicitationSchema() {
		final Map<String, Object> action = new LinkedHashMap<>();
		action.put("type", "string");
		action.put("title", "Entscheidung");
		action.put("description",
				"SKIP = gesperrte Schüler:innen im Kurs lassen und die übrigen entfernen, CANCEL = niemanden entfernen");
		action.put("enum", ALLOWED_ACTIONS);
		final Map<String, Object> requestedSchema = new LinkedHashMap<>();
		requestedSchema.put("type", "object");
		requestedSchema.put("properties", Map.of("action", action));
		requestedSchema.put("required", List.of("action"));
		requestedSchema.put("additionalProperties", false);
		return requestedSchema;
	}

	private static String elicitationMessage(final List<LockedCoursePupilView> lockedPupils) {
		final StringBuilder message = new StringBuilder(
				"Einige Schüler:innen sind bereits Klausuren dieses Kurses zugeordnet und können nicht entfernt werden: ");
		for (int index = 0; index < lockedPupils.size(); index++) {
			if (index > 0) {
				message.append("; ");
			}
			final LockedCoursePupilView pupil = lockedPupils.get(index);
			message.append(pupil.name()).append(' ').append(pupil.surname()).append(" (ID ").append(pupil.id())
					.append(')');
		}
		return message.append(". Wähle SKIP oder CANCEL.").toString();
	}

	private static List<Integer> validatePupilIds(final List<Integer> pupilIds) {
		Objects.requireNonNull(pupilIds, "pupilIds must not be null");
		if (pupilIds.isEmpty()) {
			throw new IllegalArgumentException("pupilIds must not be empty");
		}
		if (pupilIds.size() > MAXIMUM_PUPILS) {
			throw new IllegalArgumentException("pupilIds must not contain more than " + MAXIMUM_PUPILS + " entries");
		}
		final Set<Integer> uniquePupilIds = new LinkedHashSet<>();
		for (final Integer pupilId : pupilIds) {
			if (pupilId == null || pupilId <= 0) {
				throw new IllegalArgumentException("pupilIds must contain only positive integers");
			}
			uniquePupilIds.add(pupilId);
		}
		return List.copyOf(uniquePupilIds);
	}

	private static CoursePupilRemovalResult result(final CoursePupilRemovalStatus status, final String message,
			final Course course, final List<Pupil> removedPupils, final List<LockedCoursePupilView> lockedPupils,
			final List<Integer> notAssignedPupilIds) {
		return new CoursePupilRemovalResult(status, message, CourseMcpTools.courseView(course),
				removedPupils.stream().map(CoursePupilRemovalMcpTools::pupilView).toList(), lockedPupils,
				notAssignedPupilIds);
	}

	private static CoursePupilView pupilView(final Pupil pupil) {
		return new CoursePupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name());
	}

	private static LockedCoursePupilView lockedPupilView(final Pupil pupil, final String reason) {
		return new LockedCoursePupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name(), reason,
				ALLOWED_ACTIONS);
	}

	public enum LockedPupilAction {
		SKIP,
		CANCEL
	}

	public enum CoursePupilRemovalStatus {
		REMOVED,
		UNCHANGED,
		NEEDS_RESOLUTION,
		CANCELLED
	}

	public record LockedCoursePupilView(int id, String name, String surname, String lifecycle, String reason,
			List<String> allowedActions) {

		public LockedCoursePupilView {
			allowedActions = List.copyOf(Objects.requireNonNull(allowedActions, "allowedActions"));
		}
	}

	public record CoursePupilRemovalResult(CoursePupilRemovalStatus status, String message, CourseView course,
			List<CoursePupilView> removedPupils, List<LockedCoursePupilView> lockedPupils,
			List<Integer> notAssignedPupilIds) {

		public CoursePupilRemovalResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			Objects.requireNonNull(course, "course");
			removedPupils = List.copyOf(Objects.requireNonNull(removedPupils, "removedPupils"));
			lockedPupils = List.copyOf(Objects.requireNonNull(lockedPupils, "lockedPupils"));
			notAssignedPupilIds = List.copyOf(Objects.requireNonNull(notAssignedPupilIds, "notAssignedPupilIds"));
		}
	}
}
