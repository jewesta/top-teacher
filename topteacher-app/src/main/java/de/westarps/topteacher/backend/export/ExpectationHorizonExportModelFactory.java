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
import de.westarps.topteacher.model.eh.EhCategory;
import de.westarps.topteacher.model.eh.EhPart;
import de.westarps.topteacher.model.eh.EhRequirement;
import de.westarps.topteacher.model.eh.EhRequirementResult;
import de.westarps.topteacher.model.eh.EhTask;
import de.westarps.topteacher.model.eh.ExamNoteSection;

@Component
public class ExpectationHorizonExportModelFactory {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd. MM. yyyy");

	private final Sanitizer sanitizer;

	public ExpectationHorizonExportModelFactory(final Sanitizer sanitizer) {
		this.sanitizer = sanitizer;
	}

	public ExpectationHorizonExportModel createPupilModel(final ExpectationHorizonExportData data) {
		return createPupilModel(data, MarkdownView.PUPIL);
	}

	public ExpectationHorizonExportModel createPupilModel(final ExpectationHorizonExportData data,
			final MarkdownView markdownView) {
		final MarkdownView view = markdownView == null ? MarkdownView.PUPIL : markdownView;
		final Map<Integer, List<EhCategory>> categoriesByPartId = groupByParentId(
				sorted(data.categories(), Comparator.comparingInt(EhCategory::sortOrder).thenComparing(EhCategory::id,
						Comparator.nullsLast(Integer::compareTo))),
				EhCategory::partId);
		final Map<Integer, List<EhTask>> tasksByCategoryId = groupByParentId(
				sorted(data.tasks(), Comparator.comparingInt(EhTask::sortOrder).thenComparing(EhTask::id,
						Comparator.nullsLast(Integer::compareTo))),
				EhTask::categoryId);
		final Map<Integer, List<EhRequirement>> requirementsByTaskId = groupByParentId(sorted(data.requirements(),
				Comparator.comparingInt(EhRequirement::sortOrder).thenComparing(EhRequirement::id,
						Comparator.nullsLast(Integer::compareTo))),
				EhRequirement::taskId);
		final Map<Integer, EhRequirementResult> resultsByRequirementId = mapById(data.requirementResults(),
				EhRequirementResult::requirementId);

		final List<Part> parts = sorted(data.parts(),
				Comparator.comparingInt(EhPart::sortOrder).thenComparing(EhPart::id,
						Comparator.nullsLast(Integer::compareTo))).stream()
				.map(part -> createPart(part, categoriesByPartId, tasksByCategoryId, requirementsByTaskId,
						resultsByRequirementId, view))
				.toList();
		final List<NoteSection> notes = sorted(data.noteSections(),
				Comparator.comparingInt(ExamNoteSection::sortOrder).thenComparing(ExamNoteSection::id,
						Comparator.nullsLast(Integer::compareTo))).stream()
				.map(noteSection -> new NoteSection(noteSection.title(),
						sanitizer.markdownToHtml(noteSection.descriptionMarkdown(), view)))
				.toList();

		return new ExpectationHorizonExportModel(data.course(), data.exam(), data.pupil(), data.gradingScale(),
				data.gradingScaleRanges(), PointSummary.sum(parts, Part::points), parts, notes);
	}

	private Part createPart(final EhPart part, final Map<Integer, List<EhCategory>> categoriesByPartId,
			final Map<Integer, List<EhTask>> tasksByCategoryId,
			final Map<Integer, List<EhRequirement>> requirementsByTaskId,
			final Map<Integer, EhRequirementResult> resultsByRequirementId, final MarkdownView view) {
		final List<Category> categories = categoriesByPartId.getOrDefault(part.id(), List.of()).stream()
				.map(category -> createCategory(category, tasksByCategoryId, requirementsByTaskId,
						resultsByRequirementId, view))
				.toList();
		return new Part(part.title(), PointSummary.sum(categories, Category::points), categories);
	}

	private Category createCategory(final EhCategory category, final Map<Integer, List<EhTask>> tasksByCategoryId,
			final Map<Integer, List<EhRequirement>> requirementsByTaskId,
			final Map<Integer, EhRequirementResult> resultsByRequirementId, final MarkdownView view) {
		final List<Task> tasks = tasksByCategoryId.getOrDefault(category.id(), List.of()).stream()
				.map(task -> createTask(task, requirementsByTaskId, resultsByRequirementId, view)).toList();
		return new Category(category.title(), sanitizer.markdownToHtml(category.descriptionMarkdown(), view),
				PointSummary.sum(tasks, Task::points), tasks);
	}

	private Task createTask(final EhTask task, final Map<Integer, List<EhRequirement>> requirementsByTaskId,
			final Map<Integer, EhRequirementResult> resultsByRequirementId, final MarkdownView view) {
		final List<EhRequirement> requirements = requirementsByTaskId.getOrDefault(task.id(), List.of());
		final List<Requirement> exportRequirements = new ArrayList<>();
		for (int index = 0; index < requirements.size(); index++) {
			exportRequirements.add(createRequirement(requirements.get(index), resultsByRequirementId, view,
					index + 1));
		}
		return new Task(task.title(), PointSummary.sum(exportRequirements, Requirement::points), exportRequirements);
	}

	private Requirement createRequirement(final EhRequirement requirement,
			final Map<Integer, EhRequirementResult> resultsByRequirementId, final MarkdownView view,
			final int number) {
		final EhRequirementResult result = resultsByRequirementId.get(requirement.id());
		final int achievedPoints = result == null ? 0 : result.points();
		return new Requirement(number, sanitizer.markdownToHtml(requirement.descriptionMarkdown(), view),
				requirement.maxPoints(), requirement.bonus(), achievedPoints, result == null ? "" : result.comment());
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

	public record ExpectationHorizonExportData(Course course, Exam exam, Pupil pupil, GradingScale gradingScale,
			List<GradingScaleRange> gradingScaleRanges, List<EhPart> parts, List<EhCategory> categories,
			List<EhTask> tasks, List<EhRequirement> requirements, List<EhRequirementResult> requirementResults,
			List<ExamNoteSection> noteSections) {

		public ExpectationHorizonExportData {
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
			noteSections = copy(noteSections);
		}
	}

	public record ExpectationHorizonExportModel(Course course, Exam exam, Pupil pupil, GradingScale gradingScale,
			List<GradingScaleRange> gradingScaleRanges, PointSummary points, List<Part> parts,
			List<NoteSection> noteSections) {

		public ExpectationHorizonExportModel {
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
			return maxPoints == 0 ? "( )" : "(" + maxPoints + ")";
		}

		public String achievedPointsDisplayName() {
			if (!bonus) {
				return String.valueOf(achievedPoints);
			}
			return achievedPoints == 0 ? "" : "(" + achievedPoints + ")";
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
