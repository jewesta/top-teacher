package de.westarps.topteacher.ui.component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.function.ValueProvider;

import de.westarps.topteacher.model.GradeLevel;

public class GradingScaleRangeGridGroup<T> extends HorizontalLayout {

	private static final int ROW_COUNT = 8;
	private static final String GRADE_POINTS_COLUMN_WIDTH = "7rem";
	private static final String GRADE_NAME_COLUMN_WIDTH = "4rem";
	private static final String RANGE_POINTS_COLUMN_WIDTH = "8rem";

	private final Grid<T> firstGrid;
	private final Grid<T> secondGrid;

	private GradingScaleRangeGridGroup(final Class<T> beanType, final Consumer<Grid<T>> configureColumns) {
		firstGrid = new Grid<>(Objects.requireNonNull(beanType, "Bean type can not be null"), false);
		secondGrid = new Grid<>(beanType, false);

		configureGrid(firstGrid, configureColumns);
		configureGrid(secondGrid, configureColumns);

		add(firstGrid, secondGrid);
		addClassName("tt-grading-scale-grid-group");
		setPadding(false);
		setSpacing(false);
		setWidthFull();
	}

	public static <T> GradingScaleRangeGridGroup<T> editable(final Class<T> beanType,
			final ValueProvider<T, GradeLevel> gradeLevelProvider, final ValueProvider<T, Integer> minPointsProvider,
			final ValueProvider<T, ?> maxPointsProvider, final IntSupplier maxPointsSupplier,
			final Predicate<T> readOnlyProvider, final BiConsumer<T, Integer> minPointsChanged) {
		Objects.requireNonNull(gradeLevelProvider, "Grade level provider can not be null");
		Objects.requireNonNull(minPointsProvider, "Min points provider can not be null");
		Objects.requireNonNull(maxPointsProvider, "Max points provider can not be null");
		Objects.requireNonNull(maxPointsSupplier, "Max points supplier can not be null");
		Objects.requireNonNull(readOnlyProvider, "Read-only provider can not be null");
		Objects.requireNonNull(minPointsChanged, "Min points change handler can not be null");
		return new GradingScaleRangeGridGroup<>(beanType, grid -> configureEditableColumns(grid, gradeLevelProvider,
				minPointsProvider, maxPointsProvider, maxPointsSupplier, readOnlyProvider, minPointsChanged));
	}

	public static <T> GradingScaleRangeGridGroup<T> passive(final Class<T> beanType,
			final ValueProvider<T, GradeLevel> gradeLevelProvider, final ValueProvider<T, ?> minPointsProvider,
			final ValueProvider<T, ?> maxPointsProvider) {
		Objects.requireNonNull(gradeLevelProvider, "Grade level provider can not be null");
		Objects.requireNonNull(minPointsProvider, "Min points provider can not be null");
		Objects.requireNonNull(maxPointsProvider, "Max points provider can not be null");
		return new GradingScaleRangeGridGroup<>(beanType,
				grid -> configurePassiveColumns(grid, gradeLevelProvider, minPointsProvider, maxPointsProvider));
	}

	public void setItems(final List<T> items) {
		final List<T> safeItems = List.copyOf(Objects.requireNonNull(items, "Items can not be null"));
		final int splitIndex = Math.min(ROW_COUNT, safeItems.size());
		firstGrid.setItems(safeItems.subList(0, splitIndex));
		secondGrid.setItems(safeItems.subList(splitIndex, safeItems.size()));
	}

	public void refreshAll() {
		firstGrid.getDataProvider().refreshAll();
		secondGrid.getDataProvider().refreshAll();
	}

	public Grid<T> firstGrid() {
		return firstGrid;
	}

	public Grid<T> secondGrid() {
		return secondGrid;
	}

	private static <T> void configureEditableColumns(final Grid<T> grid,
			final ValueProvider<T, GradeLevel> gradeLevelProvider, final ValueProvider<T, Integer> minPointsProvider,
			final ValueProvider<T, ?> maxPointsProvider, final IntSupplier maxPointsSupplier,
			final Predicate<T> readOnlyProvider, final BiConsumer<T, Integer> minPointsChanged) {
		addGradeColumns(grid, gradeLevelProvider);
		grid.addComponentColumn(row -> minPointsField(row, gradeLevelProvider, minPointsProvider, maxPointsSupplier,
				readOnlyProvider, minPointsChanged)).setHeader("ab Punkte").setTextAlign(ColumnTextAlign.CENTER)
				.setWidth(RANGE_POINTS_COLUMN_WIDTH).setFlexGrow(0);
		addMaxPointsColumn(grid, maxPointsProvider);
	}

	private static <T> void configurePassiveColumns(final Grid<T> grid,
			final ValueProvider<T, GradeLevel> gradeLevelProvider, final ValueProvider<T, ?> minPointsProvider,
			final ValueProvider<T, ?> maxPointsProvider) {
		addGradeColumns(grid, gradeLevelProvider);
		grid.addColumn(minPointsProvider).setHeader("ab Punkte").setTextAlign(ColumnTextAlign.CENTER)
				.setWidth(RANGE_POINTS_COLUMN_WIDTH).setFlexGrow(0);
		addMaxPointsColumn(grid, maxPointsProvider);
	}

	private static <T> void addGradeColumns(final Grid<T> grid, final ValueProvider<T, GradeLevel> gradeLevelProvider) {
		grid.addColumn(row -> gradeLevelProvider.apply(row).getPoints()).setHeader(gradePointsHeader())
				.setTextAlign(ColumnTextAlign.END).setWidth(GRADE_POINTS_COLUMN_WIDTH).setFlexGrow(0);
		grid.addColumn(row -> gradeLevelProvider.apply(row).getShortName()).setHeader("Note")
				.setWidth(GRADE_NAME_COLUMN_WIDTH).setFlexGrow(0);
	}

	private static <T> void addMaxPointsColumn(final Grid<T> grid, final ValueProvider<T, ?> maxPointsProvider) {
		grid.addColumn(maxPointsProvider).setHeader("bis Punkte").setTextAlign(ColumnTextAlign.END)
				.setWidth(RANGE_POINTS_COLUMN_WIDTH).setFlexGrow(0);
	}

	private static <T> Component minPointsField(final T row, final ValueProvider<T, GradeLevel> gradeLevelProvider,
			final ValueProvider<T, Integer> minPointsProvider, final IntSupplier maxPointsSupplier,
			final Predicate<T> readOnlyProvider, final BiConsumer<T, Integer> minPointsChanged) {
		final GradeLevel gradeLevel = gradeLevelProvider.apply(row);
		final IntegerField field = new IntegerField();
		field.setAriaLabel("Mindestpunktzahl für " + gradeLevel.getShortName());
		field.setMin(0);
		field.setMax(Math.max(0, maxPointsSupplier.getAsInt()));
		field.setStepButtonsVisible(true);
		field.setReadOnly(readOnlyProvider.test(row) || gradeLevel == GradeLevel.UNGENUEGEND);
		field.setValue(minPointsProvider.apply(row));
		field.addValueChangeListener(event -> minPointsChanged.accept(row, valueOrZero(event.getValue())));
		field.setWidth("7rem");
		return field;
	}

	private void configureGrid(final Grid<T> grid, final Consumer<Grid<T>> configureColumns) {
		Objects.requireNonNull(configureColumns, "Column configurator can not be null").accept(grid);
		grid.addClassName("tt-grading-scale-grid");
		grid.setItems(List.of());
		grid.setSelectionMode(Grid.SelectionMode.NONE);
		grid.setAllRowsVisible(true);
		grid.setWidthFull();
	}

	private static Component gradePointsHeader() {
		return new Span(new Text("Noten-"), new Html("<br>"), new Text("punkte"));
	}

	private static int valueOrZero(final Integer value) {
		return value == null ? 0 : value;
	}
}
