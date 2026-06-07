package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoePointRules;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.SpreadsheetGrid;

public class ExamEvaluationViewer extends AbstractDesigner {

	private static final String RESULT_COLUMN_WIDTH = "5.5rem";
	private static final String SPACER_COLUMN_WIDTH = RESULT_COLUMN_WIDTH;

	private final CourseRepository courseRepository;
	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final SpreadsheetGrid<EvaluationRow> grid = new SpreadsheetGrid<>(EvaluationRow.class, false);

	private Exam exam;
	private List<LoePart> parts = List.of();
	private List<LoeCategory> categories = List.of();
	private List<LoeTask> tasks = List.of();
	private List<LoeRequirement> requirements = List.of();
	private List<GradingScaleRange> gradingScaleRanges = List.of();
	private LoePointRules pointRules;

	public ExamEvaluationViewer(final CourseRepository courseRepository,
			final LevelOfExpectationsRepository levelOfExpectationsRepository,
			final GradingScaleRepository gradingScaleRepository) {
		super("tt-exam-evaluation-viewer");
		this.courseRepository = courseRepository;
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
		this.gradingScaleRepository = gradingScaleRepository;

		grid.setSelectionMode(Grid.SelectionMode.NONE);
		grid.setSizeFull();
	}

	public void setExam(final Exam exam) {
		this.exam = exam;
		refresh();
	}

	private void refresh() {
		resetDesigner();
		grid.removeAllColumns();
		grid.setItems(List.of());

		if (exam == null) {
			showDesignerMessage(emptyState("Bitte wählen Sie eine Klausur aus."));
			return;
		}

		final Course course = courseRepository.findById(exam.courseId()).orElse(null);
		if (course == null) {
			showDesignerMessage(emptyState("Der Kurs dieser Klausur wurde nicht gefunden."));
			return;
		}

		loadStructure();
		if (requirements.isEmpty()) {
			showDesignerMessage(emptyState("Noch kein Erwartungshorizont angelegt."));
			return;
		}

		final List<Pupil> pupils = courseRepository.findPupils(exam.courseId());
		if (pupils.isEmpty()) {
			showDesignerMessage(emptyState("Diesem Kurs sind keine Schüler zugeordnet."));
			return;
		}

		loadPointRules(course);
		configureGrid(aggregationColumns());
		grid.setItems(pupils.stream().map(this::evaluationRow).toList());

		toolbar().add(summary(pupils));
		content().add(grid);
		content().expand(grid);
		showDesigner();
	}

	private void loadStructure() {
		parts = levelOfExpectationsRepository.findPartsByExamId(exam.id());
		categories = levelOfExpectationsRepository.findCategoriesByExamId(exam.id());
		tasks = levelOfExpectationsRepository.findTasksByExamId(exam.id());
		requirements = levelOfExpectationsRepository.findRequirementsByExamId(exam.id());
	}

	private void loadPointRules(final Course course) {
		if (course.gradingScaleId() == null) {
			pointRules = null;
			gradingScaleRanges = List.of();
			return;
		}
		final GradingScale gradingScale = gradingScaleRepository.findById(course.gradingScaleId()).orElse(null);
		if (gradingScale == null) {
			pointRules = null;
			gradingScaleRanges = List.of();
			return;
		}
		pointRules = new LoePointRules(gradingScale);
		gradingScaleRanges = gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id());
	}

	private void configureGrid(final List<AggregationColumn> aggregationColumns) {
		grid.addSpreadsheetColumn(EvaluationRow::pupilName, "Schüler").setFrozen(true).setAutoWidth(true)
				.setFlexGrow(0);
		grid.addSpreadsheetColumn(row -> pointsDisplayName(row.pointsFor(requirements), hasBonus(requirements)),
				"Gesamt").setTextAlign(ColumnTextAlign.CENTER).setFrozen(true).setWidth(RESULT_COLUMN_WIDTH)
				.setFlexGrow(0);
		grid.addSpreadsheetColumn(this::gradeDisplayName, "Note").setTextAlign(ColumnTextAlign.CENTER)
				.setFrozen(true).setWidth("8rem").setFlexGrow(0);
		aggregationColumns.forEach(aggregationColumn -> grid
				.addSpreadsheetColumn(row -> pointsDisplayName(row.pointsFor(aggregationColumn.requirements()),
						hasBonus(aggregationColumn.requirements())), aggregationColumn.title())
				.setTextAlign(ColumnTextAlign.CENTER)
				.setWidth(RESULT_COLUMN_WIDTH).setFlexGrow(0));
		grid.addSpacerColumn(SPACER_COLUMN_WIDTH);
	}

	private EvaluationRow evaluationRow(final Pupil pupil) {
		final Map<Integer, Integer> achievedPointsByRequirementId = levelOfExpectationsRepository
				.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()).stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, LoeRequirementResult::points));
		return new EvaluationRow(pupil, achievedPointsByRequirementId);
	}

	private List<AggregationColumn> aggregationColumns() {
		final List<AggregationColumn> columns = new ArrayList<>();
		parts.forEach(part -> {
			final List<LoeRequirement> partRequirements = categoriesFor(part).stream()
					.flatMap(category -> tasksFor(category).stream())
					.flatMap(task -> requirementsFor(task).stream()).toList();
			columns.add(new AggregationColumn(part.title(), partRequirements));

			categoriesFor(part).forEach(category -> {
				final List<LoeRequirement> categoryRequirements = tasksFor(category).stream()
						.flatMap(task -> requirementsFor(task).stream()).toList();
				columns.add(new AggregationColumn(category.title(), categoryRequirements));

				tasksFor(category).forEach(task -> columns.add(new AggregationColumn(task.title(),
						requirementsFor(task))));
			});
		});
		return collapseRedundantColumns(columns);
	}

	private List<AggregationColumn> collapseRedundantColumns(final List<AggregationColumn> columns) {
		final List<AggregationColumn> collapsedColumns = new ArrayList<>();
		columns.forEach(column -> {
			if (sameRequirements(column.requirements(), requirements)) {
				return;
			}
			if (!collapsedColumns.isEmpty()
					&& sameRequirements(collapsedColumns.getLast().requirements(), column.requirements())) {
				return;
			}
			collapsedColumns.add(column);
		});
		return collapsedColumns;
	}

	private String gradeDisplayName(final EvaluationRow row) {
		if (pointRules == null || !pointRules.regularMaxPointsMatch(requirements)) {
			return "";
		}
		final int effectivePoints;
		try {
			effectivePoints = pointRules.cappedAchievedTotal(requirements, row::achievedPointsFor);
		} catch (final IllegalArgumentException exception) {
			return "";
		}
		return gradingScaleRanges.stream()
				.filter(range -> range.minPoints() <= effectivePoints && effectivePoints <= range.maxPoints())
				.findFirst()
				.map(range -> range.gradeLevel().getDisplayName())
				.orElse("");
	}

	private List<LoeCategory> categoriesFor(final LoePart part) {
		return categories.stream().filter(category -> category.partId().equals(part.id()))
				.sorted(Comparator.comparingInt(LoeCategory::sortOrder).thenComparing(LoeCategory::id)).toList();
	}

	private List<LoeTask> tasksFor(final LoeCategory category) {
		return tasks.stream().filter(task -> task.categoryId().equals(category.id()))
				.sorted(Comparator.comparingInt(LoeTask::sortOrder).thenComparing(LoeTask::id)).toList();
	}

	private List<LoeRequirement> requirementsFor(final LoeTask task) {
		return requirements.stream().filter(requirement -> requirement.taskId().equals(task.id()))
				.sorted(Comparator.comparingInt(LoeRequirement::sortOrder).thenComparing(LoeRequirement::id)).toList();
	}

	private static Span summary(final List<Pupil> pupils) {
		final Span summary = new Span(pupils.size() + " Schüler");
		summary.addClassName("tt-evaluation-summary");
		return summary;
	}

	private static Span emptyState(final String text) {
		final Span emptyState = new Span(text);
		emptyState.addClassName("tt-empty-state");
		return emptyState;
	}

	private static boolean hasBonus(final List<LoeRequirement> requirements) {
		return requirements.stream().anyMatch(LoeRequirement::bonus);
	}

	private static boolean sameRequirements(final List<LoeRequirement> left, final List<LoeRequirement> right) {
		return requirementIds(left).equals(requirementIds(right));
	}

	private static List<Integer> requirementIds(final List<LoeRequirement> requirements) {
		return requirements.stream().map(LoeRequirement::id).sorted().toList();
	}

	private static String pointsDisplayName(final LoePoints points, final boolean showBonus) {
		if (!showBonus && points.bonus() == 0) {
			return String.valueOf(points.regular());
		}
		return points.regular() + " (+" + points.bonus() + ")";
	}

	private record AggregationColumn(String title, List<LoeRequirement> requirements) {
	}

	private record EvaluationRow(Pupil pupil, Map<Integer, Integer> achievedPointsByRequirementId) {

		String pupilName() {
			return pupil.surname() + ", " + pupil.name();
		}

		LoePoints pointsFor(final List<LoeRequirement> requirements) {
			return requirements.stream().map(this::pointsFor).reduce(new LoePoints(0, 0), LoePoints::plus);
		}

		int achievedPointsFor(final LoeRequirement requirement) {
			return achievedPointsByRequirementId.getOrDefault(requirement.id(), 0);
		}

		private LoePoints pointsFor(final LoeRequirement requirement) {
			final int points = achievedPointsFor(requirement);
			return requirement.bonus() ? new LoePoints(0, points) : new LoePoints(points, 0);
		}
	}
}
