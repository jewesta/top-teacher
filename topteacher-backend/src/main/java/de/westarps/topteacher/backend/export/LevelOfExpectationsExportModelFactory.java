package de.westarps.topteacher.backend.export;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.export.Sanitizer.MarkdownView;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.ExamNoteSection;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@Component
public class LevelOfExpectationsExportModelFactory {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final Sanitizer sanitizer;

	public LevelOfExpectationsExportModelFactory(final Sanitizer sanitizer) {
		this.sanitizer = sanitizer;
	}

	public LevelOfExpectationsExportModel createPupilModel(final LevelOfExpectationsExportData data) {
		return createModel(data, MarkdownView.PUPIL, false);
	}

	public LevelOfExpectationsExportModel createTeacherModel(final LevelOfExpectationsExportData data) {
		return createModel(data, MarkdownView.TEACHER, true);
	}

	private LevelOfExpectationsExportModel createModel(final LevelOfExpectationsExportData data,
			final MarkdownView view, final boolean includeTeacherOnlyContent) {
		final Map<Integer, List<LoeCategory>> categoriesByPartId = groupByParentId(
				sorted(data.categories(), Comparator.comparingInt(LoeCategory::sortOrder).thenComparing(LoeCategory::id,
						Comparator.nullsLast(Integer::compareTo))),
				LoeCategory::partId);
		final Map<Integer, List<LoeTask>> tasksByCategoryId = groupByParentId(
				sorted(data.tasks(), Comparator.comparingInt(LoeTask::sortOrder).thenComparing(LoeTask::id,
						Comparator.nullsLast(Integer::compareTo))),
				LoeTask::categoryId);
		final Map<Integer, List<LoeRequirement>> requirementsByTaskId = groupByParentId(sorted(data.requirements(),
				Comparator.comparingInt(LoeRequirement::sortOrder).thenComparing(LoeRequirement::id,
						Comparator.nullsLast(Integer::compareTo))),
				LoeRequirement::taskId);
		final Map<Integer, List<LoeCriterion>> criteriaByRequirementId = groupByParentId(sorted(data.criteria(),
				Comparator.comparingInt(LoeCriterion::sortOrder).thenComparing(LoeCriterion::id,
						Comparator.nullsLast(Integer::compareTo))),
				LoeCriterion::requirementId);
		final Map<Integer, LoeRequirementResult> resultsByRequirementId = mapById(data.requirementResults(),
				LoeRequirementResult::requirementId);
		final Map<Integer, LoeCriterionResult> resultsByCriterionId = mapById(data.criterionResults(),
				LoeCriterionResult::criterionId);

		final List<Part> parts = sorted(data.parts(),
				Comparator.comparingInt(LoePart::sortOrder).thenComparing(LoePart::id,
						Comparator.nullsLast(Integer::compareTo))).stream()
				.map(part -> createPart(part, categoriesByPartId, tasksByCategoryId, requirementsByTaskId,
						criteriaByRequirementId, resultsByRequirementId, resultsByCriterionId, view,
						includeTeacherOnlyContent))
				.toList();
		final List<NoteSection> notes = includeTeacherOnlyContent ? sorted(data.noteSections(),
				Comparator.comparingInt(ExamNoteSection::sortOrder).thenComparing(ExamNoteSection::id,
						Comparator.nullsLast(Integer::compareTo))).stream()
				.map(noteSection -> new NoteSection(noteSection.title(),
						sanitizer.markdownToHtml(noteSection.descriptionMarkdown(), view)))
				.toList() : List.of();

		return new LevelOfExpectationsExportModel(data.course(), data.exam(), data.pupil(), data.gradingScale(),
				data.gradingScaleRanges(), PointSummary.sum(parts, Part::points), parts, notes);
	}

	private Part createPart(final LoePart part, final Map<Integer, List<LoeCategory>> categoriesByPartId,
			final Map<Integer, List<LoeTask>> tasksByCategoryId,
			final Map<Integer, List<LoeRequirement>> requirementsByTaskId,
			final Map<Integer, List<LoeCriterion>> criteriaByRequirementId,
			final Map<Integer, LoeRequirementResult> resultsByRequirementId,
			final Map<Integer, LoeCriterionResult> resultsByCriterionId, final MarkdownView view,
			final boolean includeTeacherOnlyContent) {
		final List<Category> categories = categoriesByPartId.getOrDefault(part.id(), List.of()).stream()
				.map(category -> createCategory(category, tasksByCategoryId, requirementsByTaskId,
						criteriaByRequirementId, resultsByRequirementId, resultsByCriterionId, view,
						includeTeacherOnlyContent))
				.toList();
		return new Part(part.title(), PointSummary.sum(categories, Category::points), categories);
	}

	private Category createCategory(final LoeCategory category, final Map<Integer, List<LoeTask>> tasksByCategoryId,
			final Map<Integer, List<LoeRequirement>> requirementsByTaskId,
			final Map<Integer, List<LoeCriterion>> criteriaByRequirementId,
			final Map<Integer, LoeRequirementResult> resultsByRequirementId,
			final Map<Integer, LoeCriterionResult> resultsByCriterionId, final MarkdownView view,
			final boolean includeTeacherOnlyContent) {
		final List<Task> tasks = tasksByCategoryId.getOrDefault(category.id(), List.of()).stream()
				.map(task -> createTask(task, requirementsByTaskId, criteriaByRequirementId, resultsByRequirementId,
						resultsByCriterionId, view, includeTeacherOnlyContent))
				.toList();
		return new Category(category.title(), sanitizer.markdownToHtml(category.descriptionMarkdown(), view),
				PointSummary.sum(tasks, Task::points), tasks);
	}

	private Task createTask(final LoeTask task, final Map<Integer, List<LoeRequirement>> requirementsByTaskId,
			final Map<Integer, List<LoeCriterion>> criteriaByRequirementId,
			final Map<Integer, LoeRequirementResult> resultsByRequirementId,
			final Map<Integer, LoeCriterionResult> resultsByCriterionId, final MarkdownView view,
			final boolean includeTeacherOnlyContent) {
		final List<LoeRequirement> requirements = requirementsByTaskId.getOrDefault(task.id(), List.of());
		final List<Requirement> exportRequirements = new ArrayList<>();
		for (int index = 0; index < requirements.size(); index++) {
			exportRequirements.add(createRequirement(requirements.get(index), criteriaByRequirementId,
					resultsByRequirementId, resultsByCriterionId, view, includeTeacherOnlyContent, index + 1));
		}
		return new Task(task.title(), PointSummary.sum(exportRequirements, Requirement::points), exportRequirements);
	}

	private Requirement createRequirement(final LoeRequirement requirement,
			final Map<Integer, List<LoeCriterion>> criteriaByRequirementId,
			final Map<Integer, LoeRequirementResult> resultsByRequirementId,
			final Map<Integer, LoeCriterionResult> resultsByCriterionId, final MarkdownView view,
			final boolean includeTeacherOnlyContent, final int number) {
		final LoeRequirementResult result = resultsByRequirementId.get(requirement.id());
		final int achievedPoints = result == null ? 0 : result.points();
		final String comment = includeTeacherOnlyContent && result != null ? result.comment() : "";
		return new Requirement(number, sanitizer.markdownToHtml(requirement.descriptionMarkdown(), view,
				criterionStatusByKey(criteriaByRequirementId.getOrDefault(requirement.id(), List.of()),
						resultsByCriterionId)), requirement.maxPoints(), requirement.bonus(), achievedPoints, comment);
	}

	private static Function<String, Boolean> criterionStatusByKey(final List<LoeCriterion> criteria,
			final Map<Integer, LoeCriterionResult> resultsByCriterionId) {
		final Map<String, Boolean> statusByKey = new HashMap<>();
		criteria.forEach(criterion -> {
			final LoeCriterionResult result = resultsByCriterionId.get(criterion.id());
			statusByKey.put(criterion.criterionKey(), result != null && result.achieved());
		});
		return key -> statusByKey.getOrDefault(key, false);
	}

	private static <T> List<T> sorted(final List<T> items, final Comparator<T> comparator) {
		return items.stream().sorted(comparator).toList();
	}

	private static <T> Map<Integer, List<T>> groupByParentId(final List<T> items,
			final Function<T, Integer> parentId) {
		final Map<Integer, List<T>> childrenByParentId = new HashMap<>();
		items.forEach(item -> childrenByParentId.computeIfAbsent(parentId.apply(item), ignored -> new ArrayList<>())
				.add(item));
		return childrenByParentId;
	}

	private static <T> Map<Integer, T> mapById(final List<T> items, final Function<T, Integer> id) {
		final Map<Integer, T> itemsById = new HashMap<>();
		items.forEach(item -> itemsById.put(id.apply(item), item));
		return itemsById;
	}

	public record LevelOfExpectationsExportData(Course course, Exam exam, Pupil pupil, GradingScale gradingScale,
			List<GradingScaleRange> gradingScaleRanges, List<LoePart> parts, List<LoeCategory> categories,
			List<LoeTask> tasks, List<LoeRequirement> requirements, List<LoeRequirementResult> requirementResults,
			List<LoeCriterion> criteria, List<LoeCriterionResult> criterionResults, List<ExamNoteSection> noteSections) {

		public LevelOfExpectationsExportData(final Course course, final Exam exam, final Pupil pupil,
				final GradingScale gradingScale, final List<GradingScaleRange> gradingScaleRanges,
				final List<LoePart> parts, final List<LoeCategory> categories, final List<LoeTask> tasks,
				final List<LoeRequirement> requirements, final List<LoeRequirementResult> requirementResults,
				final List<ExamNoteSection> noteSections) {
			this(course, exam, pupil, gradingScale, gradingScaleRanges, parts, categories, tasks, requirements,
					requirementResults, List.of(), List.of(), noteSections);
		}

		public LevelOfExpectationsExportData {
			course = Objects.requireNonNull(course, "course must not be null");
			exam = Objects.requireNonNull(exam, "exam must not be null");
			pupil = Objects.requireNonNull(pupil, "pupil must not be null");
			gradingScale = Objects.requireNonNull(gradingScale, "gradingScale must not be null");
			gradingScaleRanges = copy(gradingScaleRanges);
			parts = copy(parts);
			categories = copy(categories);
			tasks = copy(tasks);
			requirements = copy(requirements);
			requirementResults = copy(requirementResults);
			criteria = copy(criteria);
			criterionResults = copy(criterionResults);
			noteSections = copy(noteSections);
		}
	}

	public record LevelOfExpectationsExportModel(Course course, Exam exam, Pupil pupil, GradingScale gradingScale,
			List<GradingScaleRange> gradingScaleRanges, PointSummary points, List<Part> parts,
			List<NoteSection> noteSections) {

		public LevelOfExpectationsExportModel {
			course = Objects.requireNonNull(course, "course must not be null");
			exam = Objects.requireNonNull(exam, "exam must not be null");
			pupil = Objects.requireNonNull(pupil, "pupil must not be null");
			gradingScale = Objects.requireNonNull(gradingScale, "gradingScale must not be null");
			gradingScaleRanges = copy(gradingScaleRanges);
			points = Objects.requireNonNull(points, "points must not be null");
			parts = copy(parts);
			noteSections = copy(noteSections);
		}

		public String courseDisplayName() {
			return course.schoolClass().getDisplayName() + "_" + course.subject().getDisplayName();
		}

		public String examDateDisplayName() {
			return DATE_FORMAT.format(exam.date());
		}

		public String pupilDisplayName() {
			return pupil.name() + " " + pupil.surname();
		}

		public String totalGradeDisplayName() {
			final int effectiveAchievedPoints = points.effectiveAchievedPoints(gradingScale.maxPoints());
			return gradingScaleRanges.stream()
					.filter(range -> range.minPoints() <= effectiveAchievedPoints
							&& effectiveAchievedPoints <= range.maxPoints())
					.findFirst()
					.map(range -> range.gradeLevel().getDisplayName())
					.orElse("");
		}

		public List<GradingScaleTableRow> gradingScaleTableRows() {
			final List<GradingScaleRange> sortedRanges = gradingScaleRanges.stream()
					.sorted(Comparator.comparingInt((GradingScaleRange range) -> range.gradeLevel().getPoints())
							.reversed())
					.toList();
			final List<GradingScaleTableRow> rows = new ArrayList<>();
			for (int index = 0; index < sortedRanges.size(); index += 3) {
				rows.add(new GradingScaleTableRow(List.of(
						new GradingScaleTableCell(sortedRanges.get(index)),
						new GradingScaleTableCell(index + 1 < sortedRanges.size() ? sortedRanges.get(index + 1)
								: null),
						new GradingScaleTableCell(index + 2 < sortedRanges.size() ? sortedRanges.get(index + 2)
								: null))));
			}
			return rows;
		}
	}

	public record GradingScaleTableRow(List<GradingScaleTableCell> cells) {

		public GradingScaleTableRow {
			cells = copy(cells);
		}
	}

	public record GradingScaleTableCell(GradingScaleRange range) {

		public String minPointsDisplayName() {
			return range == null ? "" : String.valueOf(range.minPoints());
		}

		public String maxPointsDisplayName() {
			return range == null ? "" : String.valueOf(range.maxPoints());
		}

		public String pointRangeSeparatorDisplayName() {
			return range == null ? "" : "-";
		}

		public String gradeDisplayName() {
			return range == null ? "" : range.gradeLevel().getDisplayName();
		}
	}

	public record Part(String title, PointSummary points, List<Category> categories) {

		public Part {
			title = requireText(title, "title must not be blank");
			points = Objects.requireNonNull(points, "points must not be null");
			categories = copy(categories);
		}
	}

	public record Category(String title, SafeHtml description, PointSummary points, List<Task> tasks) {

		public Category {
			title = requireText(title, "title must not be blank");
			description = Objects.requireNonNull(description, "description must not be null");
			points = Objects.requireNonNull(points, "points must not be null");
			tasks = copy(tasks);
		}
	}

	public record Task(String title, PointSummary points, List<Requirement> requirements) {

		public Task {
			title = requireText(title, "title must not be blank");
			points = Objects.requireNonNull(points, "points must not be null");
			requirements = copy(requirements);
		}
	}

	public record Requirement(int number, SafeHtml description, int maxPoints, boolean bonus, int achievedPoints,
			String comment) {

		public Requirement {
			description = Objects.requireNonNull(description, "description must not be null");
			if (number < 1) {
				throw new IllegalArgumentException("number must be positive");
			}
			if (maxPoints < 0) {
				throw new IllegalArgumentException("maxPoints must not be negative");
			}
			if (achievedPoints < 0) {
				throw new IllegalArgumentException("achievedPoints must not be negative");
			}
			comment = comment == null ? "" : comment;
		}

		public PointSummary points() {
			return bonus ? new PointSummary(0, maxPoints, 0, achievedPoints)
					: new PointSummary(maxPoints, 0, achievedPoints, 0);
		}

		public String maxPointsDisplayName() {
			if (!bonus) {
				return String.valueOf(maxPoints);
			}
			return "(" + maxPoints + ")";
		}

		public String achievedPointsDisplayName() {
			if (!bonus) {
				return String.valueOf(achievedPoints);
			}
			return "(" + achievedPoints + ")";
		}
	}

	public record NoteSection(String title, SafeHtml description) {

		public NoteSection {
			title = requireText(title, "title must not be blank");
			description = Objects.requireNonNull(description, "description must not be null");
		}
	}

	public record PointSummary(int maxPoints, int bonusMaxPoints, int achievedPoints, int bonusAchievedPoints) {

		public PointSummary {
			if (maxPoints < 0 || bonusMaxPoints < 0 || achievedPoints < 0 || bonusAchievedPoints < 0) {
				throw new IllegalArgumentException("points must not be negative");
			}
		}

		public static <T> PointSummary sum(final List<T> items, final Function<T, PointSummary> points) {
			return items.stream().map(points).reduce(new PointSummary(0, 0, 0, 0), PointSummary::plus);
		}

		public PointSummary plus(final PointSummary other) {
			return new PointSummary(maxPoints + other.maxPoints, bonusMaxPoints + other.bonusMaxPoints,
					achievedPoints + other.achievedPoints, bonusAchievedPoints + other.bonusAchievedPoints);
		}

		public String maxDisplayName() {
			return displayName(maxPoints, bonusMaxPoints);
		}

		public String achievedDisplayName() {
			return displayName(achievedPoints, bonusAchievedPoints);
		}

		public int effectiveAchievedPoints(final int maxPoints) {
			if (maxPoints < 0) {
				throw new IllegalArgumentException("maxPoints must not be negative");
			}
			final int applicableBonusPoints = Math.min(bonusAchievedPoints, Math.max(0, maxPoints - achievedPoints));
			return achievedPoints + applicableBonusPoints;
		}

		private static String displayName(final int points, final int bonusPoints) {
			return bonusPoints == 0 ? String.valueOf(points) : points + " (+ " + bonusPoints + ")";
		}
	}

	private static <T> List<T> copy(final List<T> items) {
		return items == null ? List.of() : List.copyOf(items);
	}

	private static String requireText(final String value, final String message) {
		final String trimmedValue = Objects.requireNonNull(value, message).trim();
		if (trimmedValue.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return trimmedValue;
	}
}
