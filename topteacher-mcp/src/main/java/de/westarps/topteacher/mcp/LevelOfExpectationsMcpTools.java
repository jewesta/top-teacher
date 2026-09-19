package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.CourseMcpTools.CourseView;
import de.westarps.topteacher.mcp.ExamMcpTools.ExamView;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.loe.ExamNoteSection;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;

/**
 * MCP operations for reading and initially creating levels of expectations.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class LevelOfExpectationsMcpTools {

	private static final int MAXIMUM_PARTS = 20;
	private static final int MAXIMUM_CATEGORIES_PER_PART = 50;
	private static final int MAXIMUM_TASKS_PER_CATEGORY = 100;
	private static final int MAXIMUM_REQUIREMENTS_PER_TASK = 200;
	private static final int MAXIMUM_TOTAL_REQUIREMENTS = 1_000;
	private static final int MAXIMUM_NOTE_SECTIONS = 50;
	private static final int MAXIMUM_TITLE_LENGTH = 500;
	private static final int MAXIMUM_MARKDOWN_LENGTH = 50_000;

	private final CourseRepository courses;
	private final ExamRepository exams;
	private final GradingScaleRepository gradingScales;
	private final LevelOfExpectationsRepository levelOfExpectations;

	public LevelOfExpectationsMcpTools(final CourseRepository courses, final ExamRepository exams,
			final GradingScaleRepository gradingScales, final LevelOfExpectationsRepository levelOfExpectations) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.exams = Objects.requireNonNull(exams, "exams");
		this.gradingScales = Objects.requireNonNull(gradingScales, "gradingScales");
		this.levelOfExpectations = Objects.requireNonNull(levelOfExpectations, "levelOfExpectations");
	}

	@McpTool(name = "get_level_of_expectations", title = "Get a level of expectations",
			description = "Get an exam's complete level-of-expectations design, criteria, notes, point totals, and grading scale.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public LevelOfExpectationsView getLevelOfExpectations(
			@McpToolParam(description = "Exam ID returned by list_exams.") final int examId) {
		final Exam exam = requireExam(examId);
		final Course course = requireCourse(exam.courseId());
		final List<LoePart> parts = levelOfExpectations.findPartsByExamId(examId);
		final List<LoeCategory> categories = levelOfExpectations.findCategoriesByExamId(examId);
		final List<LoeTask> tasks = levelOfExpectations.findTasksByExamId(examId);
		final List<LoeRequirement> requirements = levelOfExpectations.findRequirementsByExamId(examId);
		final List<LoeCriterion> criteria = levelOfExpectations.findActiveCriteriaByExamId(examId);
		final List<PartView> partViews = parts.stream()
				.map(part -> partView(part, categories, tasks, requirements, criteria)).toList();
		final List<NoteSectionView> noteViews = levelOfExpectations.findNoteSectionsByExamId(examId).stream()
				.map(LevelOfExpectationsMcpTools::noteView).toList();
		final GradingScaleView gradingScale = gradingScaleView(exam.gradingScaleId());
		final boolean correctionMode = levelOfExpectations.hasResultsForExam(examId);
		return new LevelOfExpectationsView(CourseMcpTools.courseView(course),
				ExamMcpTools.examView(exam, exams.findNumberById(examId).orElse(null), correctionMode), gradingScale,
				pointSummary(requirements, gradingScale), correctionMode, partViews, noteViews);
	}

	@Transactional
	@McpTool(name = "create_level_of_expectations", title = "Create a level of expectations",
			description = "Create a complete nested level of expectations and optional notes for a blank exam. Existing designs or notes are never overwritten. List order becomes display order. Use [label](eh:key) Markdown links to define criteria.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = false, openWorldHint = false))
	public LevelOfExpectationsView createLevelOfExpectations(@McpToolParam(
			description = "Exam ID returned by list_exams. The exam must have a blank design.") final int examId,
			@McpToolParam(
					description = "Ordered nested parts, categories, tasks, and requirements.") final List<PartDraft> parts,
			@McpToolParam(required = false,
					description = "Optional ordered note sections. Omit for no notes.") final List<NoteSectionDraft> noteSections) {
		requireExam(examId);
		assertBlankDesign(examId);
		validateDraft(parts, noteSections);

		for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
			final PartDraft partDraft = parts.get(partIndex);
			final LoePart part = levelOfExpectations.savePart(new LoePart(null, examId, partDraft.title(), partIndex));
			for (int categoryIndex = 0; categoryIndex < partDraft.categories().size(); categoryIndex++) {
				final CategoryDraft categoryDraft = partDraft.categories().get(categoryIndex);
				final LoeCategory category = levelOfExpectations.saveCategory(new LoeCategory(null, part.id(),
						categoryDraft.title(), markdown(categoryDraft.descriptionMarkdown()), categoryIndex));
				for (int taskIndex = 0; taskIndex < categoryDraft.tasks().size(); taskIndex++) {
					final TaskDraft taskDraft = categoryDraft.tasks().get(taskIndex);
					final LoeTask task = levelOfExpectations
							.saveTask(new LoeTask(null, category.id(), taskDraft.title(), taskIndex));
					for (int requirementIndex = 0; requirementIndex < taskDraft.requirements()
							.size(); requirementIndex++) {
						final RequirementDraft requirementDraft = taskDraft.requirements().get(requirementIndex);
						levelOfExpectations.saveRequirement(
								new LoeRequirement(null, task.id(), markdown(requirementDraft.descriptionMarkdown()),
										requirementDraft.maxPoints(), requirementDraft.bonus(), requirementIndex));
					}
				}
			}
		}

		final List<NoteSectionDraft> notes = noteSections == null ? List.of() : noteSections;
		for (int noteIndex = 0; noteIndex < notes.size(); noteIndex++) {
			final NoteSectionDraft note = notes.get(noteIndex);
			levelOfExpectations.saveNoteSection(
					new ExamNoteSection(null, examId, note.title(), markdown(note.descriptionMarkdown()), noteIndex));
		}
		return getLevelOfExpectations(examId);
	}

	private void assertBlankDesign(final int examId) {
		if (!levelOfExpectations.findPartsByExamId(examId).isEmpty()
				|| !levelOfExpectations.findNoteSectionsByExamId(examId).isEmpty()
				|| levelOfExpectations.hasResultsForExam(examId)) {
			throw new IllegalStateException(
					"Exam " + examId + " already has a level of expectations, notes, or saved results.");
		}
	}

	private static void validateDraft(final List<PartDraft> parts, final List<NoteSectionDraft> noteSections) {
		boundedList(parts, "parts", MAXIMUM_PARTS);
		if (parts.isEmpty()) {
			throw new IllegalArgumentException("parts must not be empty");
		}
		final List<NoteSectionDraft> notes = noteSections == null ? List.of() : noteSections;
		boundedList(notes, "noteSections", MAXIMUM_NOTE_SECTIONS);

		int requirementCount = 0;
		for (final PartDraft part : parts) {
			boundedText(part.title(), "part title", MAXIMUM_TITLE_LENGTH, false);
			boundedList(part.categories(), "part categories", MAXIMUM_CATEGORIES_PER_PART);
			for (final CategoryDraft category : part.categories()) {
				boundedText(category.title(), "category title", MAXIMUM_TITLE_LENGTH, false);
				boundedText(category.descriptionMarkdown(), "category descriptionMarkdown", MAXIMUM_MARKDOWN_LENGTH,
						true);
				boundedList(category.tasks(), "category tasks", MAXIMUM_TASKS_PER_CATEGORY);
				for (final TaskDraft task : category.tasks()) {
					boundedText(task.title(), "task title", MAXIMUM_TITLE_LENGTH, false);
					boundedList(task.requirements(), "task requirements", MAXIMUM_REQUIREMENTS_PER_TASK);
					for (final RequirementDraft requirement : task.requirements()) {
						boundedText(requirement.descriptionMarkdown(), "requirement descriptionMarkdown",
								MAXIMUM_MARKDOWN_LENGTH, true);
						if (requirement.maxPoints() < 0) {
							throw new IllegalArgumentException("requirement maxPoints must not be negative");
						}
						requirementCount++;
					}
				}
			}
		}
		if (requirementCount > MAXIMUM_TOTAL_REQUIREMENTS) {
			throw new IllegalArgumentException(
					"level of expectations must not contain more than " + MAXIMUM_TOTAL_REQUIREMENTS + " requirements");
		}
		for (final NoteSectionDraft note : notes) {
			boundedText(note.title(), "note section title", MAXIMUM_TITLE_LENGTH, false);
			boundedText(note.descriptionMarkdown(), "note section descriptionMarkdown", MAXIMUM_MARKDOWN_LENGTH, true);
		}
	}

	private static void boundedList(final List<?> values, final String name, final int maximumSize) {
		Objects.requireNonNull(values, name + " must not be null");
		if (values.size() > maximumSize) {
			throw new IllegalArgumentException(name + " must not contain more than " + maximumSize + " entries");
		}
	}

	private static void boundedText(final String value, final String name, final int maximumLength,
			final boolean nullable) {
		if (value == null) {
			if (nullable) {
				return;
			}
			throw new IllegalArgumentException(name + " must not be null");
		}
		if (!nullable && value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		if (value.length() > maximumLength) {
			throw new IllegalArgumentException(name + " must not exceed " + maximumLength + " characters");
		}
	}

	private Course requireCourse(final int courseId) {
		return courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
	}

	private Exam requireExam(final int examId) {
		return exams.findById(examId).orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
	}

	private GradingScaleView gradingScaleView(final Integer gradingScaleId) {
		if (gradingScaleId == null) {
			return null;
		}
		final GradingScale gradingScale = gradingScales.findById(gradingScaleId).orElse(null);
		if (gradingScale == null) {
			return null;
		}
		return new GradingScaleView(gradingScale.id(), gradingScale.name(), gradingScale.maxPoints(),
				gradingScale.lifecycle().name(), gradingScales.findRangesByGradingScaleId(gradingScale.id()).stream()
						.map(LevelOfExpectationsMcpTools::gradingRangeView).toList());
	}

	private static PartView partView(final LoePart part, final List<LoeCategory> categories, final List<LoeTask> tasks,
			final List<LoeRequirement> requirements, final List<LoeCriterion> criteria) {
		final List<CategoryView> categoryViews = categories.stream()
				.filter(category -> category.partId().equals(part.id()))
				.map(category -> categoryView(category, tasks, requirements, criteria)).toList();
		return new PartView(part.id(), part.title(), part.sortOrder(), categoryViews);
	}

	private static CategoryView categoryView(final LoeCategory category, final List<LoeTask> tasks,
			final List<LoeRequirement> requirements, final List<LoeCriterion> criteria) {
		final List<TaskView> taskViews = tasks.stream().filter(task -> task.categoryId().equals(category.id()))
				.map(task -> taskView(task, requirements, criteria)).toList();
		return new CategoryView(category.id(), category.title(), category.descriptionMarkdown(), category.sortOrder(),
				taskViews);
	}

	private static TaskView taskView(final LoeTask task, final List<LoeRequirement> requirements,
			final List<LoeCriterion> criteria) {
		final List<RequirementView> requirementViews = requirements.stream()
				.filter(requirement -> requirement.taskId().equals(task.id()))
				.map(requirement -> requirementView(requirement, criteria)).toList();
		return new TaskView(task.id(), task.title(), task.sortOrder(), requirementViews);
	}

	private static RequirementView requirementView(final LoeRequirement requirement,
			final List<LoeCriterion> criteria) {
		final List<CriterionView> criterionViews = criteria.stream()
				.filter(criterion -> criterion.requirementId().equals(requirement.id()))
				.map(LevelOfExpectationsMcpTools::criterionView).toList();
		return new RequirementView(requirement.id(), requirement.descriptionMarkdown(), requirement.maxPoints(),
				requirement.bonus(), requirement.sortOrder(), criterionViews);
	}

	private static PointSummaryView pointSummary(final List<LoeRequirement> requirements,
			final GradingScaleView gradingScale) {
		final int regularMaximum = requirements.stream().filter(requirement -> !requirement.bonus())
				.mapToInt(LoeRequirement::maxPoints).sum();
		final int bonusMaximum = requirements.stream().filter(LoeRequirement::bonus).mapToInt(LoeRequirement::maxPoints)
				.sum();
		final Integer gradingScaleMaximum = gradingScale == null ? null : gradingScale.maxPoints();
		return new PointSummaryView(regularMaximum, bonusMaximum, gradingScaleMaximum,
				gradingScaleMaximum != null && regularMaximum == gradingScaleMaximum);
	}

	private static GradingScaleRangeView gradingRangeView(final GradingScaleRange range) {
		return new GradingScaleRangeView(range.gradeLevel().name(), range.gradeLevel().getDisplayName(),
				range.gradeLevel().getShortName(), range.minPoints(), range.maxPoints());
	}

	private static CriterionView criterionView(final LoeCriterion criterion) {
		return new CriterionView(criterion.id(), criterion.criterionKey(), criterion.label(), criterion.sortOrder());
	}

	private static NoteSectionView noteView(final ExamNoteSection note) {
		return new NoteSectionView(note.id(), note.title(), note.descriptionMarkdown(), note.sortOrder());
	}

	private static String markdown(final String value) {
		return value == null ? "" : value;
	}

	public record LevelOfExpectationsView(CourseView course, ExamView exam, GradingScaleView gradingScale,
			PointSummaryView points, boolean correctionMode, List<PartView> parts, List<NoteSectionView> noteSections) {

		public LevelOfExpectationsView {
			Objects.requireNonNull(course, "course");
			Objects.requireNonNull(exam, "exam");
			Objects.requireNonNull(points, "points");
			parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
			noteSections = List.copyOf(Objects.requireNonNull(noteSections, "noteSections"));
		}
	}

	public record GradingScaleView(int id, String name, int maxPoints, String lifecycle,
			List<GradingScaleRangeView> ranges) {

		public GradingScaleView {
			ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
		}
	}

	public record GradingScaleRangeView(String gradeLevel, String displayName, String shortName, int minPoints,
			int maxPoints) {
	}

	public record PointSummaryView(int regularMaximum, int bonusMaximum, Integer gradingScaleMaximum,
			boolean regularMaximumMatchesGradingScale) {
	}

	public record PartView(int id, String title, int sortOrder, List<CategoryView> categories) {

		public PartView {
			categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
		}
	}

	public record CategoryView(int id, String title, String descriptionMarkdown, int sortOrder, List<TaskView> tasks) {

		public CategoryView {
			tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
		}
	}

	public record TaskView(int id, String title, int sortOrder, List<RequirementView> requirements) {

		public TaskView {
			requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
		}
	}

	public record RequirementView(int id, String descriptionMarkdown, int maxPoints, boolean bonus, int sortOrder,
			List<CriterionView> criteria) {

		public RequirementView {
			criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
		}
	}

	public record CriterionView(int id, String key, String label, int sortOrder) {
	}

	public record NoteSectionView(int id, String title, String descriptionMarkdown, int sortOrder) {
	}

	public record PartDraft(String title, List<CategoryDraft> categories) {

		public PartDraft {
			categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
		}
	}

	public record CategoryDraft(String title, String descriptionMarkdown, List<TaskDraft> tasks) {

		public CategoryDraft {
			tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
		}
	}

	public record TaskDraft(String title, List<RequirementDraft> requirements) {

		public TaskDraft {
			requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
		}
	}

	public record RequirementDraft(String descriptionMarkdown, int maxPoints, boolean bonus) {
	}

	public record NoteSectionDraft(String title, String descriptionMarkdown) {
	}
}
