package de.westarps.topteacher.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.authoring.ExamCreationExecutor;
import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.authoring.ExamMutationExecutor;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.ExamMcpTools.ExamView;
import de.westarps.topteacher.mcp.ExamMcpTools.PupilView;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.ExamNumber;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

/**
 * MCP operations for creating exams and changing their user-editable metadata.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamWriteMcpTools {

	private static final int MAXIMUM_TITLE_LENGTH = 100;
	private static final int MAXIMUM_PUPILS = 200;

	private final CourseRepository courses;
	private final ExamRepository exams;
	private final GradingScaleRepository gradingScales;
	private final LevelOfExpectationsRepository levelOfExpectations;
	private final ExamCreationExecutor creations;
	private final ExamMutationExecutor mutations;
	private final ExamDesignGuard guard;

	public ExamWriteMcpTools(final CourseRepository courses, final ExamRepository exams,
			final GradingScaleRepository gradingScales, final LevelOfExpectationsRepository levelOfExpectations,
			final ExamCreationExecutor creations, final ExamMutationExecutor mutations, final ExamDesignGuard guard) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.exams = Objects.requireNonNull(exams, "exams");
		this.gradingScales = Objects.requireNonNull(gradingScales, "gradingScales");
		this.levelOfExpectations = Objects.requireNonNull(levelOfExpectations, "levelOfExpectations");
		this.creations = Objects.requireNonNull(creations, "creations");
		this.mutations = Objects.requireNonNull(mutations, "mutations");
		this.guard = Objects.requireNonNull(guard, "guard");
	}

	@McpTool(name = "create_exam", title = "Create an exam",
			description = "Create an empty exam shell for an active course, then author content with apply_exam_content_changes and EH with apply_level_of_expectations_changes. Read get_markdown_guide before authoring. The grading scale defaults to the course scale and becomes immutable. originalExamId creates a makeup exam. Omitted initialPupilIds selects all active course pupils for a main exam, or those outside the original for a makeup exam; an empty list selects none. requestId is a course-scoped retry key: reuse only for an identical retry, never for another creation. No pupils or EH are implicitly created.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamWriteResult createExam(
			@McpToolParam(description = "Active course ID obtained from list_courses.") final int courseId,
			@McpToolParam(description = "Exam title, unique within the course, at most 100 characters.") final String title,
			@McpToolParam(description = "Exam date in ISO format YYYY-MM-DD.") final String date,
			@McpToolParam(required = false,
					description = "Existing grading-scale ID. Omit to inherit the course grading scale.") final Integer gradingScaleId,
			@McpToolParam(required = false,
					description = "Main exam ID from list_exams when creating a makeup exam. Omit for a main exam.") final Integer originalExamId,
			@McpToolParam(required = false,
					description = "Initial active pupil IDs from list_course_pupils. Omit to use TopTeacher's default selection; supply an empty list for no pupils.") final List<Integer> initialPupilIds,
			@McpToolParam(description = "Unique retry key, 1-100 letters, digits, dots, underscores, colons or hyphens.") final String requestId) {
		McpAccessContext.accessLevel();
		title(title);
		date(date);
		if (initialPupilIds != null) {
			pupilIds(initialPupilIds, true);
		}
		final var creation = creations.execute(courseId, requestId, "CREATE_EXAM",
				ExamMutationExecutor.fingerprint(courseId, title, date, gradingScaleId, originalExamId, initialPupilIds),
				() -> createShell(courseId, title, date, gradingScaleId, originalExamId, initialPupilIds).id());
		return guard.read(creation.examId(), () -> result(ExamWriteStatus.CREATED,
				"The exam was created with its initial pupil assignments.", exams.findById(creation.examId()).orElseThrow(),
				creation.mutationRevision(), creation.replayed()));
	}

	private Exam createShell(final int courseId, final String title, final String date, final Integer gradingScaleId,
			final Integer originalExamId, final List<Integer> initialPupilIds) {
		final Course course = activeCourse(courseId);
		final String validatedTitle = title(title);
		if (exams.existsByCourseIdAndTitle(courseId, validatedTitle)) {
			throw new IllegalArgumentException(
					"An exam with this title already exists in course " + courseId + ": " + validatedTitle);
		}
		final LocalDate validatedDate = date(date);
		final Integer resolvedGradingScaleId = gradingScaleId(course, gradingScaleId);
		final Exam originalExam = originalExam(course, originalExamId);
		final List<Integer> resolvedPupilIds = initialPupilIds == null ? defaultPupilIds(course, originalExam)
				: validateInitialPupilIds(course, initialPupilIds);

		return exams.save(new Exam(null, course.id(), validatedTitle, validatedDate,
				originalExam == null ? null : originalExam.id(), resolvedGradingScaleId), resolvedPupilIds);
	}

	@McpTool(name = "update_exam", title = "Update an exam title or date",
			description = "Change the title, date, or both for an exam belonging to an active course. Omitted fields remain unchanged. Course, grading scale, makeup-exam relationship, pupil assignments, level of expectations, and results are never changed. Existing TopTeacher date rules for main and makeup exams remain enforced.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public ExamWriteResult updateExam(@McpToolParam(description = "Exam ID obtained from list_exams.") final int examId,
			@McpToolParam(required = false,
					description = "New title, at most 100 characters. Omit to keep the current title.") final String title,
			@McpToolParam(required = false,
				description = "New date in ISO format YYYY-MM-DD. Omit to keep the current date.") final String date,
			@McpToolParam(description = "Current exam revision from get_exam.") final long expectedRevision,
			@McpToolParam(description = "Unique retry key; reuse only for an identical retry.") final String requestId) {
		McpAccessContext.accessLevel();
		if (title == null && date == null) {
			throw new IllegalArgumentException("title or date must be supplied");
		}
		if (title != null) {
			title(title);
		}
		if (date != null) {
			date(date);
		}
		final var mutation = mutations.execute(examId, expectedRevision, requestId, "UPDATE_EXAM",
				ExamMutationExecutor.fingerprint("UPDATE_EXAM", examId, expectedRevision, title, date),
				() -> updateMetadata(examId, title, date),
				ExamWriteStatus::name, ExamWriteStatus::valueOf);
		return guard.read(examId, () -> result(mutation.metadata(), "The requested exam metadata is saved.",
				exams.findById(examId).orElseThrow(), mutation.revision(), mutation.replayed()));
	}

	private ExamWriteStatus updateMetadata(final int examId, final String title, final String date) {
		final Exam current = examInActiveCourse(examId);
		final String updatedTitle = title == null ? current.title() : title(title);
		final LocalDate updatedDate = date == null ? current.date() : date(date);
		if (!updatedTitle.equals(current.title()) && exams.existsByCourseIdAndTitle(current.courseId(), updatedTitle)) {
			throw new IllegalArgumentException(
					"An exam with this title already exists in course " + current.courseId() + ": " + updatedTitle);
		}
		if (updatedTitle.equals(current.title()) && updatedDate.equals(current.date())) {
			return ExamWriteStatus.UNCHANGED;
		}

		final Exam updated = new Exam(current.id(), current.courseId(), updatedTitle, updatedDate,
				current.originalExamId(), current.gradingScaleId());
		exams.save(updated);
		return ExamWriteStatus.UPDATED;
	}

	@McpTool(name = "duplicate_exam", title = "Duplicate an exam design",
			description = "Create a new main exam in the source exam's active course. Copies ordered parts, categories, tasks, material with shared immutable image references, footer, EH requirements and criteria using new node IDs. Never copies results, source pupil assignments or makeup relationships. Uses the source grading scale. initialPupilIds is required: explicitly select active course pupils or supply an empty list for none. Source expectedRevision prevents copying a stale design; course-scoped requestId makes identical retries safe. Source results do not prevent copying its design.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamWriteResult duplicateExam(
			@McpToolParam(description = "Source exam ID from get_exam.") final int sourceExamId,
			@McpToolParam(description = "Current source exam revision from get_exam.") final long expectedRevision,
			@McpToolParam(description = "Unique new title, at most 100 characters, within the source course.") final String title,
			@McpToolParam(description = "New exam date in ISO format YYYY-MM-DD.") final String date,
			@McpToolParam(description = "Explicit active course pupil IDs; an empty list selects none. Source assignments are not copied.") final List<Integer> initialPupilIds,
			@McpToolParam(description = "Unique course-scoped retry key; reuse only for an identical retry.") final String requestId) {
		McpAccessContext.accessLevel();
		Objects.requireNonNull(initialPupilIds, "initialPupilIds must be supplied explicitly");
		title(title);
		date(date);
		pupilIds(initialPupilIds, true);
		final Exam source = exams.findById(sourceExamId)
				.orElseThrow(() -> new IllegalArgumentException("Source exam does not exist: " + sourceExamId));
		final var creation = creations.execute(source.courseId(), requestId, "DUPLICATE_EXAM",
				ExamMutationExecutor.fingerprint(sourceExamId, expectedRevision, title, date, initialPupilIds),
				() -> guard.write(sourceExamId, expectedRevision, () -> {
					final Exam current = exams.findById(sourceExamId).orElseThrow();
					final Exam copy = createShell(current.courseId(), title, date, current.gradingScaleId(), null,
							initialPupilIds);
					levelOfExpectations.copyDesign(current.id(), copy.id());
					return copy.id();
				}));
		return guard.read(creation.examId(), () -> result(ExamWriteStatus.CREATED,
				"The academic exam design was copied without results.", exams.findById(creation.examId()).orElseThrow(),
				creation.mutationRevision(), creation.replayed()));
	}

	private Course activeCourse(final int courseId) {
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course can not receive new or updated exams: " + courseId);
		}
		return course;
	}

	private Exam examInActiveCourse(final int examId) {
		final Exam exam = exams.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		activeCourse(exam.courseId());
		return exam;
	}

	private Integer gradingScaleId(final Course course, final Integer requestedGradingScaleId) {
		if (requestedGradingScaleId == null || requestedGradingScaleId.equals(course.gradingScaleId())) {
			return course.gradingScaleId();
		}
		final GradingScale gradingScale = gradingScales.findById(requestedGradingScaleId).orElseThrow(
				() -> new IllegalArgumentException("Grading scale does not exist: " + requestedGradingScaleId));
		if (gradingScale.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException(
					"Archived grading scale can not be assigned to a new exam: " + requestedGradingScaleId);
		}
		return gradingScale.id();
	}

	private Exam originalExam(final Course course, final Integer originalExamId) {
		if (originalExamId == null) {
			return null;
		}
		final Exam original = exams.findById(originalExamId)
				.orElseThrow(() -> new IllegalArgumentException("Original exam does not exist: " + originalExamId));
		if (!original.courseId().equals(course.id())) {
			throw new IllegalArgumentException("A makeup exam must belong to the same course as its original exam.");
		}
		if (original.isMakeupExam()) {
			throw new IllegalArgumentException("A makeup exam can not be the original exam of another makeup exam.");
		}
		return original;
	}

	private List<Integer> defaultPupilIds(final Course course, final Exam originalExam) {
		final Set<Integer> originalPupilIds = originalExam == null ? Set.of()
				: exams.findPupils(originalExam.id()).stream().map(Pupil::id)
						.collect(java.util.stream.Collectors.toSet());
		return courses.findPupils(course.id()).stream().filter(pupil -> pupil.lifecycle() == Lifecycle.ACTIVE)
				.filter(pupil -> !originalPupilIds.contains(pupil.id())).map(Pupil::id).toList();
	}

	private List<Integer> validateInitialPupilIds(final Course course, final List<Integer> initialPupilIds) {
		final List<Integer> validatedPupilIds = pupilIds(initialPupilIds, true);
		final Map<Integer, Pupil> activeCoursePupils = new LinkedHashMap<>();
		courses.findPupils(course.id()).stream().filter(pupil -> pupil.lifecycle() == Lifecycle.ACTIVE)
				.forEach(pupil -> activeCoursePupils.put(pupil.id(), pupil));
		final List<Integer> invalidPupilIds = validatedPupilIds.stream()
				.filter(pupilId -> !activeCoursePupils.containsKey(pupilId)).toList();
		if (!invalidPupilIds.isEmpty()) {
			throw new IllegalArgumentException(
					"Initial pupils must be active and assigned to course " + course.id() + ": " + invalidPupilIds);
		}
		return validatedPupilIds;
	}

	private ExamWriteResult result(final ExamWriteStatus status, final String message, final Exam exam,
			final long mutationRevision, final boolean replayed) {
		final ExamNumber number = exams.findNumberById(exam.id()).orElse(null);
		final ExamView examView = ExamMcpTools.examView(exam, number, levelOfExpectations.hasResultsForExam(exam.id()));
		final List<PupilView> pupilViews = exams.findPupils(exam.id()).stream().map(ExamMcpTools::pupilView).toList();
		return new ExamWriteResult(status, message, examView, pupilViews, guard.revision(exam.id()), mutationRevision,
				replayed);
	}

	private static String title(final String title) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		final String normalized = title.trim();
		if (normalized.length() > MAXIMUM_TITLE_LENGTH) {
			throw new IllegalArgumentException("title must not exceed " + MAXIMUM_TITLE_LENGTH + " characters");
		}
		return normalized;
	}

	private static LocalDate date(final String date) {
		if (date == null || date.isBlank()) {
			throw new IllegalArgumentException("date must not be blank");
		}
		if (date.length() != 10) {
			throw new IllegalArgumentException("date must use ISO format YYYY-MM-DD");
		}
		try {
			return LocalDate.parse(date);
		} catch (final DateTimeParseException invalidDate) {
			throw new IllegalArgumentException("date must use ISO format YYYY-MM-DD: " + date, invalidDate);
		}
	}

	static List<Integer> pupilIds(final List<Integer> pupilIds, final boolean emptyAllowed) {
		Objects.requireNonNull(pupilIds, "pupilIds must not be null");
		if (!emptyAllowed && pupilIds.isEmpty()) {
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

	public enum ExamWriteStatus {
		CREATED,
		UPDATED,
		UNCHANGED
	}

	public record ExamWriteResult(ExamWriteStatus status, String message, ExamView exam, List<PupilView> pupils,
			long revision, long mutationRevision, boolean replayed) {

		public ExamWriteResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			Objects.requireNonNull(exam, "exam");
			pupils = List.copyOf(Objects.requireNonNull(pupils, "pupils"));
		}
	}
}
