package de.westarps.topteacher.mcp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.mcp.CourseMcpTools.CourseView;
import de.westarps.topteacher.mcp.CourseRosterWriter.CreatedCourseRoster;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictResolution;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictView;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilDraft;
import de.westarps.topteacher.mcp.PupilRosterConflictResolver.ResolutionOutcome;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

/**
 * MCP operation for atomically creating a course and resolving its pupil
 * roster.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CourseRosterMcpTools {

	private static final int MAXIMUM_PUPILS = 200;
	private final CourseRepository courses;
	private final SubjectRepository subjects;
	private final GradingScaleRepository gradingScales;
	private final PupilRosterConflictResolver conflictResolver;
	private final CourseRosterWriter writer;

	public CourseRosterMcpTools(final CourseRepository courses, final SubjectRepository subjects,
			final GradingScaleRepository gradingScales, final PupilRosterConflictResolver conflictResolver,
			final CourseRosterWriter writer) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.subjects = Objects.requireNonNull(subjects, "subjects");
		this.gradingScales = Objects.requireNonNull(gradingScales, "gradingScales");
		this.conflictResolver = Objects.requireNonNull(conflictResolver, "conflictResolver");
		this.writer = Objects.requireNonNull(writer, "writer");
	}

	@McpTool(name = "create_course_with_pupils", title = "Create a course with its pupils",
			description = "Create one active course and its pupil roster atomically. Call get_course_creation_options first. Exact active pupil-name matches and names repeated in the roster require an explicit REUSE, CREATE, or SKIP decision. Archived pupils are historical: they are ignored for matching and cannot be newly assigned. If the client supports MCP elicitation, TopTeacher asks there; otherwise this tool returns NEEDS_RESOLUTION without changing the database and must be called again with resolutions.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = false, openWorldHint = false))
	public CourseRosterResult createCourseWithPupils(final McpSyncRequestContext context, @McpToolParam(
			description = "Course definition using IDs from get_course_creation_options.") final CourseDraft course,
			@McpToolParam(
					description = "Roster entries. entryKey must be unique and stable across a NEEDS_RESOLUTION retry.") final List<PupilDraft> roster,
			@McpToolParam(required = false,
					description = "Conflict decisions from a previous NEEDS_RESOLUTION result. Omit on the first call.") final List<PupilConflictResolution> resolutions) {
		final Course courseToCreate = course(course);
		final List<PupilDraft> normalizedRoster = validateRoster(roster);
		final ResolutionOutcome resolution = conflictResolver.resolve(context, normalizedRoster, resolutions);
		if (resolution.cancelled()) {
			return cancelled(resolution.conflicts());
		}
		if (!resolution.conflicts().isEmpty()) {
			return needsResolution(resolution.conflicts());
		}
		if (resolution.resolvedPupils().isEmpty()) {
			return allSkipped(resolution.skippedEntryKeys());
		}

		final CreatedCourseRoster created = writer.create(courseToCreate, resolution.resolvedPupils());
		return new CourseRosterResult(CourseRosterStatus.CREATED,
				"Course and pupil roster were created in one transaction.",
				List.of(CourseMcpTools.courseView(created.course())),
				created.pupils().stream().map(CourseRosterMcpTools::pupilView).toList(), List.of(),
				resolution.skippedEntryKeys());
	}

	private Course course(final CourseDraft draft) {
		Objects.requireNonNull(draft, "course must not be null");
		final Subject subject = subjects.findById(draft.subjectId())
				.orElseThrow(() -> new IllegalArgumentException("Subject does not exist: " + draft.subjectId()));
		if (subject.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Subject is not active: " + draft.subjectId());
		}
		final GradingScale gradingScale = gradingScales.findById(draft.gradingScaleId()).orElseThrow(
				() -> new IllegalArgumentException("Grading scale does not exist: " + draft.gradingScaleId()));
		if (gradingScale.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Grading scale is not active: " + draft.gradingScaleId());
		}
		final SchoolYear schoolYear = new SchoolYear(draft.calendarYear());
		courses.findByNaturalKey(draft.schoolClass(), subject.id(), schoolYear, draft.coursePeriod())
				.ifPresent(existing -> {
					throw new IllegalStateException(
							"Course already exists: " + existing.id() + " (" + existing.getDisplayName() + ")");
				});
		return new Course(null, draft.schoolClass(), subject, schoolYear, draft.coursePeriod(), Lifecycle.ACTIVE,
				gradingScale.id());
	}

	private static List<PupilDraft> validateRoster(final List<PupilDraft> roster) {
		Objects.requireNonNull(roster, "roster must not be null");
		if (roster.isEmpty()) {
			throw new IllegalArgumentException("roster must not be empty");
		}
		if (roster.size() > MAXIMUM_PUPILS) {
			throw new IllegalArgumentException("roster must not contain more than " + MAXIMUM_PUPILS + " entries");
		}
		final Set<String> entryKeys = new LinkedHashSet<>();
		for (final PupilDraft pupil : roster) {
			Objects.requireNonNull(pupil, "roster entry must not be null");
			if (!entryKeys.add(pupil.entryKey())) {
				throw new IllegalArgumentException("roster entryKey must be unique: " + pupil.entryKey());
			}
		}
		return List.copyOf(roster);
	}

	private static RosterPupilView pupilView(final Pupil pupil) {
		return new RosterPupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name());
	}

	private static CourseRosterResult needsResolution(final List<PupilConflictView> conflicts) {
		return new CourseRosterResult(CourseRosterStatus.NEEDS_RESOLUTION,
				"No database changes were made. Ask the user to choose one allowed action for each conflict, then call this tool again with resolutions.",
				List.of(), List.of(), conflicts, List.of());
	}

	private static CourseRosterResult cancelled(final List<PupilConflictView> conflicts) {
		return new CourseRosterResult(CourseRosterStatus.CANCELLED,
				"The user cancelled or declined pupil conflict resolution. No database changes were made.", List.of(),
				List.of(), conflicts, List.of());
	}

	private static CourseRosterResult allSkipped(final List<String> skippedEntryKeys) {
		return new CourseRosterResult(CourseRosterStatus.CANCELLED,
				"All roster entries were skipped. No database changes were made.", List.of(), List.of(), List.of(),
				skippedEntryKeys);
	}

	public enum CourseRosterStatus {
		CREATED,
		NEEDS_RESOLUTION,
		CANCELLED
	}

	public record CourseDraft(SchoolClass schoolClass, int subjectId, int calendarYear, CoursePeriod coursePeriod,
			int gradingScaleId) {

		public CourseDraft {
			Objects.requireNonNull(schoolClass, "schoolClass must not be null");
			Objects.requireNonNull(coursePeriod, "coursePeriod must not be null");
		}
	}

	public record CourseRosterResult(CourseRosterStatus status, String message, List<CourseView> createdCourses,
			List<RosterPupilView> assignedPupils, List<PupilConflictView> conflicts,
			List<String> skippedRosterEntryKeys) {

		public CourseRosterResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			createdCourses = List.copyOf(Objects.requireNonNull(createdCourses, "createdCourses"));
			assignedPupils = List.copyOf(Objects.requireNonNull(assignedPupils, "assignedPupils"));
			conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
			skippedRosterEntryKeys = List
					.copyOf(Objects.requireNonNull(skippedRosterEntryKeys, "skippedRosterEntryKeys"));
		}
	}

	public record RosterPupilView(int id, String name, String surname, String lifecycle) {
	}

}
