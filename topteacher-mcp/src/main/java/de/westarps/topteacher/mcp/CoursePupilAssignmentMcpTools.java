package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.mcp.CourseMcpTools.CourseView;
import de.westarps.topteacher.mcp.CoursePupilAssignmentWriter.AssignmentResult;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.RosterPupilView;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictResolution;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictView;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilDraft;
import de.westarps.topteacher.mcp.PupilRosterConflictResolver.ResolutionOutcome;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

/**
 * MCP operation for adding pupils to an existing active course.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CoursePupilAssignmentMcpTools {

	private static final int MAXIMUM_PUPILS = 200;

	private final CourseRepository courses;
	private final PupilRosterConflictResolver conflictResolver;
	private final CoursePupilAssignmentWriter writer;

	public CoursePupilAssignmentMcpTools(final CourseRepository courses,
			final PupilRosterConflictResolver conflictResolver, final CoursePupilAssignmentWriter writer) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.conflictResolver = Objects.requireNonNull(conflictResolver, "conflictResolver");
		this.writer = Objects.requireNonNull(writer, "writer");
	}

	@McpTool(name = "assign_pupils_to_course", title = "Assign pupils to an existing course",
			description = "Add pupils to an existing active course selected by an ID from list_courses. New pupils are created as active records. Exact active pupil-name matches and names repeated in the request require REUSE, CREATE, or SKIP. A uniquely named active pupil already assigned to the course is treated as satisfied. Archived pupils and archived courses are historical and cannot receive new assignments. No pupils are removed and no course properties are changed.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = false, openWorldHint = false))
	public CoursePupilAssignmentResult assignPupilsToCourse(final McpSyncRequestContext context,
			@McpToolParam(description = "Active course ID obtained from list_courses.") final int courseId,
			@McpToolParam(
					description = "Pupils to assign. entryKey must be unique and stable across a NEEDS_RESOLUTION retry.") final List<PupilDraft> pupilDrafts,
			@McpToolParam(required = false,
					description = "Conflict decisions from a previous NEEDS_RESOLUTION result. Omit on the first call.") final List<PupilConflictResolution> resolutions) {
		final Course course = activeCourse(courseId);
		final List<PupilDraft> validatedDrafts = validateDrafts(pupilDrafts);
		final PreResolvedRoster preResolved = preResolveAlreadyAssigned(courseId, validatedDrafts);
		final ResolutionOutcome resolution = conflictResolver.resolve(context, preResolved.unresolvedDrafts(),
				resolutions);
		if (resolution.cancelled()) {
			return result(CoursePupilAssignmentStatus.CANCELLED,
					"The user cancelled or declined pupil conflict resolution. No database changes were made.", course,
					List.of(), preResolved.alreadyAssignedPupils(), resolution.conflicts(), List.of());
		}
		if (!resolution.conflicts().isEmpty()) {
			return result(CoursePupilAssignmentStatus.NEEDS_RESOLUTION,
					"No database changes were made. Ask the user to choose one allowed action for each conflict, then call this tool again with resolutions.",
					course, List.of(), preResolved.alreadyAssignedPupils(), resolution.conflicts(), List.of());
		}

		final List<ResolvedPupil> resolvedPupils = new ArrayList<>(preResolved.alreadyAssignedPupils().stream()
				.map(pupil -> new ResolvedPupil(pupil.id(), pupil.name(), pupil.surname())).toList());
		resolvedPupils.addAll(resolution.resolvedPupils());
		if (resolution.resolvedPupils().isEmpty()) {
			final CoursePupilAssignmentStatus status = preResolved.alreadyAssignedPupils().isEmpty()
					? CoursePupilAssignmentStatus.CANCELLED
					: CoursePupilAssignmentStatus.UNCHANGED;
			final String message = preResolved.alreadyAssignedPupils().isEmpty()
					? "All pupil entries were skipped. No database changes were made."
					: "Every requested pupil was already assigned or skipped. No database changes were made.";
			return result(status, message, course, List.of(), preResolved.alreadyAssignedPupils(), List.of(),
					resolution.skippedEntryKeys());
		}

		final AssignmentResult assigned = writer.assign(courseId, resolvedPupils);
		final CoursePupilAssignmentStatus status = assigned.assignedPupils().isEmpty()
				? CoursePupilAssignmentStatus.UNCHANGED
				: CoursePupilAssignmentStatus.UPDATED;
		final String message = assigned.assignedPupils().isEmpty()
				? "Every requested pupil was already assigned or skipped. No database changes were made."
				: "Pupils were created or assigned in one transaction.";
		return result(status, message, assigned.course(), assigned.assignedPupils(), assigned.alreadyAssignedPupils(),
				List.of(), resolution.skippedEntryKeys());
	}

	private Course activeCourse(final int courseId) {
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course can not receive new pupil assignments: " + courseId);
		}
		return course;
	}

	private PreResolvedRoster preResolveAlreadyAssigned(final int courseId, final List<PupilDraft> pupilDrafts) {
		final Map<NameKey, Long> nameCounts = pupilDrafts.stream().collect(Collectors.groupingBy(
				pupil -> new NameKey(pupil.name(), pupil.surname()), LinkedHashMap::new, Collectors.counting()));
		final Map<NameKey, Pupil> assignedByName = courses.findPupils(courseId).stream()
				.filter(pupil -> pupil.lifecycle() == Lifecycle.ACTIVE)
				.collect(Collectors.toMap(pupil -> new NameKey(pupil.name(), pupil.surname()), pupil -> pupil,
						(first, second) -> first, LinkedHashMap::new));
		final List<PupilDraft> unresolvedDrafts = new ArrayList<>();
		final LinkedHashMap<Integer, Pupil> alreadyAssignedPupils = new LinkedHashMap<>();
		for (final PupilDraft pupilDraft : pupilDrafts) {
			final NameKey name = new NameKey(pupilDraft.name(), pupilDraft.surname());
			final Pupil assigned = assignedByName.get(name);
			if (nameCounts.get(name) == 1 && assigned != null) {
				alreadyAssignedPupils.put(assigned.id(), assigned);
			} else {
				unresolvedDrafts.add(pupilDraft);
			}
		}
		return new PreResolvedRoster(unresolvedDrafts, List.copyOf(alreadyAssignedPupils.values()));
	}

	private static List<PupilDraft> validateDrafts(final List<PupilDraft> pupilDrafts) {
		Objects.requireNonNull(pupilDrafts, "pupils must not be null");
		if (pupilDrafts.isEmpty()) {
			throw new IllegalArgumentException("pupils must not be empty");
		}
		if (pupilDrafts.size() > MAXIMUM_PUPILS) {
			throw new IllegalArgumentException("pupils must not contain more than " + MAXIMUM_PUPILS + " entries");
		}
		final Set<String> entryKeys = new LinkedHashSet<>();
		for (final PupilDraft pupil : pupilDrafts) {
			Objects.requireNonNull(pupil, "pupil entry must not be null");
			if (!entryKeys.add(pupil.entryKey())) {
				throw new IllegalArgumentException("pupil entryKey must be unique: " + pupil.entryKey());
			}
		}
		return List.copyOf(pupilDrafts);
	}

	private static CoursePupilAssignmentResult result(final CoursePupilAssignmentStatus status, final String message,
			final Course course, final List<Pupil> assignedPupils, final List<Pupil> alreadyAssignedPupils,
			final List<PupilConflictView> conflicts, final List<String> skippedEntryKeys) {
		return new CoursePupilAssignmentResult(status, message, List.of(CourseMcpTools.courseView(course)),
				assignedPupils.stream().map(CoursePupilAssignmentMcpTools::pupilView).toList(),
				alreadyAssignedPupils.stream().map(CoursePupilAssignmentMcpTools::pupilView).toList(), conflicts,
				skippedEntryKeys);
	}

	private static RosterPupilView pupilView(final Pupil pupil) {
		return new RosterPupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name());
	}

	public enum CoursePupilAssignmentStatus {
		UPDATED,
		UNCHANGED,
		NEEDS_RESOLUTION,
		CANCELLED
	}

	public record CoursePupilAssignmentResult(CoursePupilAssignmentStatus status, String message,
			List<CourseView> courses, List<RosterPupilView> assignedPupils, List<RosterPupilView> alreadyAssignedPupils,
			List<PupilConflictView> conflicts, List<String> skippedEntryKeys) {

		public CoursePupilAssignmentResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			courses = List.copyOf(Objects.requireNonNull(courses, "courses"));
			assignedPupils = List.copyOf(Objects.requireNonNull(assignedPupils, "assignedPupils"));
			alreadyAssignedPupils = List.copyOf(Objects.requireNonNull(alreadyAssignedPupils, "alreadyAssignedPupils"));
			conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
			skippedEntryKeys = List.copyOf(Objects.requireNonNull(skippedEntryKeys, "skippedEntryKeys"));
		}
	}

	private record PreResolvedRoster(List<PupilDraft> unresolvedDrafts, List<Pupil> alreadyAssignedPupils) {

		private PreResolvedRoster {
			unresolvedDrafts = List.copyOf(unresolvedDrafts);
			alreadyAssignedPupils = List.copyOf(alreadyAssignedPupils);
		}
	}

	private record NameKey(String name, String surname) {
	}
}
