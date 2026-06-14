package de.westarps.topteacher.ui.view;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.FormBinders;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;

@Order(7)
@UIScope
@SpringComponent
public class GradingScaleSettingsTab extends SplitListDetailView<GradingScale> implements SettingsTab {

	private static final int DEFAULT_MAX_POINTS = 100;
	private static final Map<GradeLevel, Integer> DEFAULT_MIN_POINTS = defaultMinPoints();

	private final GradingScaleRepository gradingScaleRepository;
	private final TextField name = new TextField("Name");
	private final IntegerField maxPoints = new IntegerField("Maximalpunktzahl");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Status");
	private final Grid<RangeFormData> rangeGrid = new Grid<>(RangeFormData.class, false);
	private final Binder<GradingScaleFormData> binder = new Binder<>();
	private final Button newButton = new Button("Neu");
	private final Button saveButton = new Button();
	private final Button archiveButton = new Button("Archivieren");
	private final Span selectionSummary = new Span();
	private final Span multiSelectionSummary = new Span();
	private final Span lockMessage = new Span(
			"Dieser Notenschlüssel wird bereits von Klausuren verwendet und kann nicht mehr geändert werden.");

	private GradingScale selectedGradingScale;
	private List<RangeFormData> rangeRows = List.of();
	private boolean selectedLocked;

	public GradingScaleSettingsTab(final GradingScaleRepository gradingScaleRepository) {
		super("Notenschlüssel", "tt-grading-scale-settings-content",
				new MultiSelectionGrid<>(GradingScale.class, false));
		this.gradingScaleRepository = gradingScaleRepository;

		addClassNames("tt-settings-content", "tt-settings-master-data-content");
		configureRangeGrid();
		configureEditor();
		initializeView();
		refreshGrid();
		clearEditor();
	}

	@Override
	public String label() {
		return "Notenschlüssel";
	}

	@Override
	public Component content() {
		return this;
	}

	@Override
	protected void configureGrid(final MultiSelectionGrid<GradingScale> grid) {
		grid.addColumn(GradingScale::name).setHeader("Name").setAutoWidth(true);
		grid.addColumn(GradingScale::maxPoints).setHeader("Punkte").setAutoWidth(true)
				.setTextAlign(ColumnTextAlign.END);
		grid.addColumn(gradingScale -> gradingScale.lifecycle().getDisplayName()).setHeader("Status")
				.setAutoWidth(true);
		grid.addColumn(this::usedLabel).setHeader("Verwendet").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		final VerticalLayout rangeContent = new VerticalLayout(createEditorForm(), rangeGrid);
		rangeContent.addClassName("tt-designer-content");
		rangeContent.setPadding(false);
		rangeContent.setSpacing(false);
		rangeContent.setSizeFull();

		final VerticalLayout editorContent = new VerticalLayout(createEditorToolbar(), rangeContent);
		editorContent.addClassNames("tt-designer", "tt-grading-scale-settings-editor-content");
		editorContent.setSizeFull();
		editorContent.setPadding(false);
		editorContent.setSpacing(false);
		editorContent.expand(rangeContent);
		return editorContent;
	}

	@Override
	protected Component createMultiSelectEditor() {
		multiSelectionSummary.addClassName("tt-selection-summary");
		return AbstractFormEditor.contentOnly("tt-grading-scale-settings-bulk-editor", List.of(multiSelectionSummary));
	}

	@Override
	protected List<Component> createListToolbarComponents() {
		return List.of(newButton);
	}

	@Override
	protected String getEditorTabLabel() {
		return "Notenschlüssel";
	}

	@Override
	protected boolean isQuickFilterVisible() {
		return false;
	}

	@Override
	protected double getSplitterPosition() {
		return 38;
	}

	@Override
	protected String getListAreaMinWidth() {
		return "22rem";
	}

	@Override
	protected String getContextAreaMinWidth() {
		return "32rem";
	}

	@Override
	protected void onEditorModeChanged(final EditorMode editorMode, final List<GradingScale> selectedItems) {
		if (editorMode == EditorMode.MULTI_SELECT) {
			showMultiSelectEditor(selectedItems);
			return;
		}

		final GradingScale gradingScale = selectedItems.isEmpty() ? null : selectedItems.get(0);
		showGradingScale(gradingScale);
	}

	private Component createEditorToolbar() {
		selectionSummary.addClassName("tt-selection-summary");
		lockMessage.addClassName("tt-settings-description");

		final VerticalLayout state = new VerticalLayout(selectionSummary, lockMessage);
		state.addClassName("tt-grading-scale-settings-editor-state");
		state.setPadding(false);
		state.setSpacing(false);

		final HorizontalLayout actions = new HorizontalLayout(saveButton, archiveButton);
		actions.addClassNames("tt-editor-actions", "tt-grading-scale-settings-actions");
		actions.setAlignItems(Alignment.END);
		actions.setPadding(false);
		actions.setSpacing(true);

		final HorizontalLayout toolbar = new HorizontalLayout(state, actions);
		toolbar.addClassNames("tt-designer-toolbar", "tt-grading-scale-settings-detail-toolbar");
		toolbar.setAlignItems(Alignment.START);
		toolbar.setPadding(false);
		toolbar.setSpacing(false);
		toolbar.setWidthFull();
		toolbar.setFlexGrow(1, state);
		toolbar.setFlexGrow(0, actions);
		return toolbar;
	}

	private Component createEditorForm() {
		final FormLayout form = new FormLayout(name, maxPoints, lifecycle);
		form.addClassName("tt-grading-scale-settings-form");
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("32rem", 3));
		form.setWidthFull();
		return form;
	}

	private void configureRangeGrid() {
		rangeGrid.addColumn(row -> row.getGradeLevel().getPoints()).setHeader("Notenpunkte")
				.setTextAlign(ColumnTextAlign.END).setWidth("8rem").setFlexGrow(0);
		rangeGrid.addColumn(row -> row.getGradeLevel().getDisplayName()).setHeader("Note").setWidth("12rem")
				.setFlexGrow(0);
		rangeGrid.addComponentColumn(this::minPointsField).setHeader("ab Punkte").setTextAlign(ColumnTextAlign.CENTER)
				.setWidth("8rem").setFlexGrow(0);
		rangeGrid.addColumn(this::maxPointsDisplayName).setHeader("bis Punkte").setTextAlign(ColumnTextAlign.END)
				.setWidth("8rem").setFlexGrow(0);
		rangeGrid.setSelectionMode(Grid.SelectionMode.NONE);
		rangeGrid.setAllRowsVisible(true);
		rangeGrid.setWidthFull();
	}

	private void configureEditor() {
		name.setClearButtonVisible(true);
		name.setValueChangeMode(ValueChangeMode.EAGER);
		name.setRequiredIndicatorVisible(true);

		maxPoints.setMin(0);
		maxPoints.setStepButtonsVisible(true);
		maxPoints.setRequiredIndicatorVisible(true);
		maxPoints.addValueChangeListener(event -> refreshRangeGrid());

		lifecycle.setItems(Lifecycle.values());
		lifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		lifecycle.setRequiredIndicatorVisible(true);

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveGradingScale());

		archiveButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		archiveButton.addClickListener(event -> archiveSelectedGradingScale());

		newButton.addClickListener(event -> {
			clearSelection();
			clearEditor();
		});

		binder.forField(name).asRequired("Name ist erforderlich.")
				.withConverter(GradingScaleSettingsTab::trim, value -> value)
				.bind(GradingScaleFormData::getName, GradingScaleFormData::setName);
		binder.forField(maxPoints).asRequired("Maximalpunktzahl ist erforderlich.")
				.withValidator(value -> value == null || value >= 0, "Maximalpunktzahl darf nicht negativ sein.")
				.bind(GradingScaleFormData::getMaxPoints, GradingScaleFormData::setMaxPoints);
		binder.forField(lifecycle).asRequired("Status ist erforderlich.").bind(GradingScaleFormData::getLifecycle,
				GradingScaleFormData::setLifecycle);
	}

	private void showGradingScale(final GradingScale gradingScale) {
		selectedGradingScale = gradingScale;
		if (gradingScale == null) {
			clearEditor();
			return;
		}

		rangeRows = rowsFor(gradingScale);
		rangeGrid.setItems(rangeRows);
		readEditor(new GradingScaleFormData(gradingScale.name(), gradingScale.maxPoints(), gradingScale.lifecycle()));
		updateEditorState();
	}

	private void showMultiSelectEditor(final List<GradingScale> gradingScales) {
		selectedGradingScale = null;
		selectedLocked = false;
		multiSelectionSummary.setText(gradingScales.size() + " Notenschlüssel ausgewählt");
	}

	private void saveGradingScale() {
		final GradingScaleFormData formData = new GradingScaleFormData();
		if (!binder.writeBeanIfValid(formData) || !validateRangeRows(formData.getMaxPoints())) {
			return;
		}

		final Integer id = selectedGradingScale == null ? null : selectedGradingScale.id();
		try {
			gradingScaleRepository.saveWithRanges(
					new GradingScale(id, formData.getName(), formData.getMaxPoints(), formData.getLifecycle()),
					currentRanges(id, formData.getMaxPoints()));
		} catch (final IllegalArgumentException exception) {
			Notification.show(exception.getMessage());
			return;
		}

		refreshGrid();
		clearSelection();
		clearEditor();
		Notification.show("Notenschlüssel gespeichert.");
	}

	private void archiveSelectedGradingScale() {
		if (selectedGradingScale == null) {
			return;
		}

		try {
			gradingScaleRepository.saveWithRanges(
					new GradingScale(selectedGradingScale.id(), selectedGradingScale.name(),
							selectedGradingScale.maxPoints(), Lifecycle.INACTIVE),
					gradingScaleRepository.findRangesByGradingScaleId(selectedGradingScale.id()));
		} catch (final IllegalArgumentException exception) {
			Notification.show(exception.getMessage());
			return;
		}

		refreshGrid();
		clearSelection();
		clearEditor();
		Notification.show("Notenschlüssel archiviert.");
	}

	private void refreshGrid() {
		setGridItems(gradingScaleRepository.findAll());
	}

	private void clearEditor() {
		selectedGradingScale = null;
		selectedLocked = false;
		rangeRows = defaultRows();
		rangeGrid.setItems(rangeRows);
		readEditor(new GradingScaleFormData("", DEFAULT_MAX_POINTS, Lifecycle.ACTIVE));
		updateEditorState();
	}

	private void readEditor(final GradingScaleFormData formData) {
		binder.readBean(formData);
		FormBinders.clearValidation(binder);
	}

	private void updateEditorState() {
		final boolean editMode = selectedGradingScale != null;
		selectedLocked = editMode && gradingScaleRepository.isUsedByExam(selectedGradingScale.id());
		selectionSummary.setText(editMode ? "Notenschlüssel bearbeiten" : "Neuer Notenschlüssel");
		saveButton.setText(editMode ? "Speichern" : "Anlegen");
		saveButton.setEnabled(!selectedLocked);
		archiveButton.setVisible(editMode && selectedGradingScale.lifecycle() == Lifecycle.ACTIVE && !selectedLocked);
		name.setReadOnly(selectedLocked);
		maxPoints.setReadOnly(selectedLocked);
		lifecycle.setVisible(editMode);
		lifecycle.setReadOnly(selectedLocked);
		lockMessage.setVisible(selectedLocked);
		refreshRangeGrid();
	}

	private Component minPointsField(final RangeFormData row) {
		final IntegerField field = new IntegerField();
		field.setAriaLabel("Mindestpunktzahl für " + row.getGradeLevel().getDisplayName());
		field.setMin(0);
		field.setMax(valueOrZero(maxPoints.getValue()));
		field.setStepButtonsVisible(true);
		field.setReadOnly(selectedLocked || row.getGradeLevel() == GradeLevel.UNGENUEGEND);
		field.setValue(row.getMinPoints());
		field.addValueChangeListener(event -> {
			row.setMinPoints(valueOrZero(event.getValue()));
			refreshRangeGrid();
		});
		field.setWidth("7rem");
		return field;
	}

	private String maxPointsDisplayName(final RangeFormData row) {
		final int derivedMaxPoints = derivedMaxPoints(row, valueOrZero(maxPoints.getValue()));
		return derivedMaxPoints < 0 ? "" : String.valueOf(derivedMaxPoints);
	}

	private int derivedMaxPoints(final RangeFormData row, final int scaleMaxPoints) {
		if (row.getGradeLevel() == GradeLevel.SEHR_GUT_PLUS) {
			return scaleMaxPoints;
		}
		return rangeRows.stream()
				.filter(candidate -> candidate.getGradeLevel().getPoints() == row.getGradeLevel().getPoints() + 1)
				.findFirst().map(candidate -> candidate.getMinPoints() - 1).orElse(row.getMinPoints());
	}

	private List<GradingScaleRange> currentRanges(final Integer gradingScaleId, final int scaleMaxPoints) {
		final int scaleId = gradingScaleId == null ? 0 : gradingScaleId;
		return rangeRows.stream().map(row -> new GradingScaleRange(null, scaleId, row.getGradeLevel(),
				row.getMinPoints(), derivedMaxPoints(row, scaleMaxPoints))).toList();
	}

	private boolean validateRangeRows(final int scaleMaxPoints) {
		for (final RangeFormData row : rangeRows) {
			final int derivedMaxPoints = derivedMaxPoints(row, scaleMaxPoints);
			if (row.getMinPoints() < 0 || derivedMaxPoints < row.getMinPoints() || derivedMaxPoints > scaleMaxPoints) {
				Notification.show("Die Punktebereiche müssen lückenlos von 0 bis zur Maximalpunktzahl reichen.");
				return false;
			}
		}
		return true;
	}

	private List<RangeFormData> rowsFor(final GradingScale gradingScale) {
		final Map<GradeLevel, GradingScaleRange> existingRanges = new EnumMap<>(GradeLevel.class);
		gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id())
				.forEach(range -> existingRanges.put(range.gradeLevel(), range));

		final List<RangeFormData> rows = new ArrayList<>();
		for (final GradeLevel gradeLevel : GradeLevel.values()) {
			final GradingScaleRange existingRange = existingRanges.get(gradeLevel);
			final int minPoints = existingRange == null ? DEFAULT_MIN_POINTS.get(gradeLevel)
					: existingRange.minPoints();
			rows.add(new RangeFormData(gradeLevel, gradeLevel == GradeLevel.UNGENUEGEND ? 0 : minPoints));
		}
		return rows;
	}

	private List<RangeFormData> defaultRows() {
		final List<RangeFormData> rows = new ArrayList<>();
		for (final GradeLevel gradeLevel : GradeLevel.values()) {
			rows.add(new RangeFormData(gradeLevel, DEFAULT_MIN_POINTS.get(gradeLevel)));
		}
		return rows;
	}

	private void refreshRangeGrid() {
		rangeGrid.getDataProvider().refreshAll();
	}

	private String usedLabel(final GradingScale gradingScale) {
		return gradingScaleRepository.isUsedByExam(gradingScale.id()) ? "ja" : "nein";
	}

	private static Map<GradeLevel, Integer> defaultMinPoints() {
		final Map<GradeLevel, Integer> values = new EnumMap<>(GradeLevel.class);
		values.put(GradeLevel.SEHR_GUT_PLUS, 95);
		values.put(GradeLevel.SEHR_GUT, 90);
		values.put(GradeLevel.SEHR_GUT_MINUS, 85);
		values.put(GradeLevel.GUT_PLUS, 80);
		values.put(GradeLevel.GUT, 75);
		values.put(GradeLevel.GUT_MINUS, 70);
		values.put(GradeLevel.BEFRIEDIGEND_PLUS, 65);
		values.put(GradeLevel.BEFRIEDIGEND, 60);
		values.put(GradeLevel.BEFRIEDIGEND_MINUS, 55);
		values.put(GradeLevel.AUSREICHEND_PLUS, 50);
		values.put(GradeLevel.AUSREICHEND, 45);
		values.put(GradeLevel.AUSREICHEND_MINUS, 40);
		values.put(GradeLevel.MANGELHAFT_PLUS, 34);
		values.put(GradeLevel.MANGELHAFT, 27);
		values.put(GradeLevel.MANGELHAFT_MINUS, 20);
		values.put(GradeLevel.UNGENUEGEND, 0);
		return values;
	}

	private static String trim(final String value) {
		return value == null ? "" : value.trim();
	}

	private static int valueOrZero(final Integer value) {
		return value == null ? 0 : value;
	}

	private static final class GradingScaleFormData {

		private String name = "";
		private Integer maxPoints = DEFAULT_MAX_POINTS;
		private Lifecycle lifecycle = Lifecycle.ACTIVE;

		private GradingScaleFormData() {
		}

		private GradingScaleFormData(final String name, final Integer maxPoints, final Lifecycle lifecycle) {
			this.name = trim(name);
			this.maxPoints = maxPoints;
			this.lifecycle = lifecycle;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = trim(name);
		}

		public Integer getMaxPoints() {
			return maxPoints;
		}

		public void setMaxPoints(final Integer maxPoints) {
			this.maxPoints = maxPoints;
		}

		public Lifecycle getLifecycle() {
			return lifecycle;
		}

		public void setLifecycle(final Lifecycle lifecycle) {
			this.lifecycle = lifecycle;
		}
	}

	private static final class RangeFormData {

		private final GradeLevel gradeLevel;
		private int minPoints;

		private RangeFormData(final GradeLevel gradeLevel, final int minPoints) {
			this.gradeLevel = gradeLevel;
			this.minPoints = minPoints;
		}

		public GradeLevel getGradeLevel() {
			return gradeLevel;
		}

		public int getMinPoints() {
			return minPoints;
		}

		public void setMinPoints(final int minPoints) {
			this.minPoints = minPoints;
		}
	}
}
