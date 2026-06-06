package de.westarps.topteacher.ui.view;

import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.Route;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;
import de.westarps.topteacher.ui.component.QuickFilterField;

@Route(value = "courses", layout = MainLayout.class)
public class CoursesView extends AbstractMasterDataView<Course> {

	private static final Comparator<AssignmentRow> ASSIGNMENT_ROW_ORDER = Comparator
			.comparing((final AssignmentRow row) -> row.pupil().surname(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(row -> row.pupil().name(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(row -> row.pupil().id());

	private final CourseRepository courseRepository;
	private final ComboBox<SchoolClass> schoolClass = new ComboBox<>("Klasse");
	private final ComboBox<Subject> subject = new ComboBox<>("Fach");
	private final IntegerField calendarYear = new IntegerField("Startjahr");
	private final ComboBox<CoursePeriod> coursePeriod = new ComboBox<>("Zeitraum");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Status");
	private final Button saveButton = new Button("Speichern");
	private final Button newButton = new Button("Neu");
	private final Button archiveButton = new Button("Archivieren");
	private final Span multiSelectionSummary = new Span();
	private final ComboBox<Lifecycle> bulkLifecycle = new ComboBox<>("Status");
	private final Button applyLifecycleButton = new Button("Anwenden");
	private final QuickFilterField assignmentSearch = new QuickFilterField();
	private final Grid<AssignmentRow> assignmentGrid = new Grid<>(AssignmentRow.class, false);

	private Course selectedCourse;
	private List<Course> selectedCourses = List.of();
	private ListDataProvider<AssignmentRow> assignmentDataProvider;
	private Tab assignmentsTab;
	private Component assignmentsContent;

	public CoursesView(final CourseRepository courseRepository) {
		super("Kurse", "tt-courses-view", new MultiSelectionGrid<>(Course.class, false));
		this.courseRepository = courseRepository;

		configureEditors();
		configureAssignments();
		initializeView();
		refreshGrid();
		clearSingleEditor();
	}

	@Override
	protected void configureGrid(final MultiSelectionGrid<Course> grid) {
		grid.addColumn(Course::id).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
		grid.addColumn(course -> course.schoolYear().getDisplayName()).setHeader("Schuljahr").setAutoWidth(true);
		grid.addColumn(course -> course.coursePeriod().getDisplayName()).setHeader("Zeitraum").setAutoWidth(true);
		grid.addColumn(course -> course.schoolClass().getDisplayName()).setHeader("Klasse").setAutoWidth(true);
		grid.addColumn(course -> course.subject().getDisplayName()).setHeader("Fach").setAutoWidth(true);
		grid.addColumn(course -> course.lifecycle().getDisplayName()).setHeader("Status").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		return AbstractFormEditor.responsive("tt-course-editor",
				List.of(schoolClass, subject, calendarYear, coursePeriod, lifecycle),
				List.of(saveButton, newButton, archiveButton));
	}

	@Override
	protected Component createMultiSelectEditor() {
		multiSelectionSummary.addClassName("tt-selection-summary");
		return AbstractFormEditor.singleColumn("tt-course-bulk-editor", List.of(multiSelectionSummary),
				List.of(bulkLifecycle), List.of(applyLifecycleButton));
	}

	@Override
	protected String getSearchText(final Course course) {
		return String.join(" ", String.valueOf(course.id()), String.valueOf(course.schoolYear().getCalendarYear()),
				course.schoolYear().getDisplayName(), course.coursePeriod().getDisplayName(),
				course.coursePeriod().name(), course.schoolClass().getDisplayName(), course.schoolClass().name(),
				course.subject().getDisplayName(), course.subject().name(), course.lifecycle().getDisplayName(),
				course.lifecycle().name());
	}

	@Override
	protected void onEditorModeChanged(final EditorMode editorMode, final List<Course> selectedItems) {
		if (editorMode == EditorMode.MULTI_SELECT) {
			selectedCourse = null;
			removeAssignmentsTab();
			showMultiSelectEditor(selectedItems);
			return;
		}

		final Course course = selectedItems.isEmpty() ? null : selectedItems.get(0);
		showSingleSelectEditor(course);
		updateAssignmentsTab(course);
	}

	private void configureEditors() {
		schoolClass.setItems(SchoolClass.values());
		schoolClass.setItemLabelGenerator(SchoolClass::getDisplayName);
		schoolClass.setRequiredIndicatorVisible(true);

		subject.setItems(Subject.values());
		subject.setItemLabelGenerator(Subject::getDisplayName);
		subject.setRequiredIndicatorVisible(true);

		calendarYear.setMin(1900);
		calendarYear.setMax(9998);
		calendarYear.setStepButtonsVisible(true);
		calendarYear.setRequiredIndicatorVisible(true);

		coursePeriod.setItems(CoursePeriod.values());
		coursePeriod.setItemLabelGenerator(CoursePeriod::getDisplayName);
		coursePeriod.setRequiredIndicatorVisible(true);

		lifecycle.setItems(Lifecycle.values());
		lifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		lifecycle.setRequiredIndicatorVisible(true);

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveCourse());

		newButton.addClickListener(event -> {
			clearSelection();
			clearSingleEditor();
			removeAssignmentsTab();
		});

		archiveButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		archiveButton.addClickListener(event -> archiveSelectedCourse());

		bulkLifecycle.setItems(Lifecycle.values());
		bulkLifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		bulkLifecycle.setClearButtonVisible(true);
		bulkLifecycle.addValueChangeListener(event -> updateBulkApplyButton());

		applyLifecycleButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		applyLifecycleButton.addClickListener(event -> applyLifecycleToSelectedCourses());
		updateBulkApplyButton();
	}

	private void configureAssignments() {
		assignmentSearch.setPlaceholder("Schüler suchen");
		assignmentSearch.addValueChangeListener(event -> applyAssignmentFilter());

		assignmentGrid.addColumn(row -> row.pupil().id()).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
		assignmentGrid.addColumn(row -> row.pupil().surname()).setHeader("Nachname").setAutoWidth(true);
		assignmentGrid.addColumn(row -> row.pupil().name()).setHeader("Vorname").setAutoWidth(true);
		assignmentGrid.addComponentColumn(this::createAssignmentCheckbox).setHeader(createAssignmentHeader())
				.setAutoWidth(true).setFlexGrow(0).setFrozenToEnd(true).setTextAlign(ColumnTextAlign.CENTER);
		assignmentGrid.addClassName("tt-assignment-grid");
		assignmentGrid.setSelectionMode(Grid.SelectionMode.NONE);
		assignmentGrid.setSizeFull();
	}

	private Component createAssignmentHeader() {
		final Icon icon = VaadinIcon.INFO_CIRCLE_O.create();
		icon.addClassName("tt-assignment-header-icon");
		icon.getElement().setAttribute("aria-label", "Zugeordnet");
		Tooltip.forComponent(icon).withText("Zugeordnet");
		return icon;
	}

	private Component createAssignmentsContent() {
		final VerticalLayout layout = new VerticalLayout(assignmentSearch, assignmentGrid);
		layout.addClassName("tt-course-assignments");
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setSizeFull();
		layout.expand(assignmentGrid);
		return layout;
	}

	private Checkbox createAssignmentCheckbox(final AssignmentRow row) {
		final Checkbox checkbox = new Checkbox(row.assigned());
		checkbox.setAriaLabel(row.assigned() ? "Schüler aus Kurs entfernen" : "Schüler dem Kurs zuordnen");
		checkbox.getElement().setAttribute("title",
				row.assigned() ? "Schüler aus Kurs entfernen" : "Schüler dem Kurs zuordnen");
		checkbox.addValueChangeListener(event -> {
			if (!event.isFromClient()) {
				return;
			}

			if (event.getValue()) {
				assignPupil(row.pupil());
			} else {
				removePupil(row.pupil());
			}
		});
		return checkbox;
	}

	private void showSingleSelectEditor(final Course course) {
		selectedCourses = List.of();
		selectedCourse = course;
		if (course == null) {
			clearSingleEditor();
			return;
		}

		schoolClass.setValue(course.schoolClass());
		subject.setValue(course.subject());
		calendarYear.setValue(course.schoolYear().getCalendarYear());
		coursePeriod.setValue(course.coursePeriod());
		lifecycle.setValue(course.lifecycle());
		updateArchiveButton();
	}

	private void showMultiSelectEditor(final List<Course> courses) {
		selectedCourse = null;
		selectedCourses = List.copyOf(courses);
		multiSelectionSummary.setText(selectedCourses.size() + " Kurse ausgewählt");
		setBulkLifecycleValue(commonLifecycle(selectedCourses));
		updateBulkApplyButton();
	}

	private void saveCourse() {
		if (!hasRequiredCourseValues()) {
			Notification.show("Klasse, Fach, Startjahr, Zeitraum und Status sind erforderlich.");
			return;
		}

		final Integer id = selectedCourse == null ? null : selectedCourse.id();
		courseRepository.save(new Course(id, schoolClass.getValue(), subject.getValue(),
				new SchoolYear(calendarYear.getValue()), coursePeriod.getValue(), lifecycle.getValue()));

		refreshGrid();
		clearSingleEditor();
		removeAssignmentsTab();
	}

	private boolean hasRequiredCourseValues() {
		return schoolClass.getValue() != null && subject.getValue() != null && calendarYear.getValue() != null
				&& calendarYear.getValue() >= 1900 && calendarYear.getValue() <= 9998 && coursePeriod.getValue() != null
				&& lifecycle.getValue() != null;
	}

	private void archiveSelectedCourse() {
		if (selectedCourse == null) {
			return;
		}

		courseRepository.archive(selectedCourse.id());
		refreshGrid();
		clearSingleEditor();
		removeAssignmentsTab();
	}

	private void applyLifecycleToSelectedCourses() {
		final Lifecycle selectedLifecycle = bulkLifecycle.getValue();
		if (selectedCourses.isEmpty() || selectedLifecycle == null) {
			return;
		}

		selectedCourses.forEach(course -> courseRepository.save(new Course(course.id(), course.schoolClass(),
				course.subject(), course.schoolYear(), course.coursePeriod(), selectedLifecycle)));
		Notification.show("Status für " + selectedCourses.size() + " Kurse aktualisiert.");
		refreshGrid();
	}

	private void assignPupil(final Pupil pupil) {
		if (selectedCourse == null) {
			return;
		}

		courseRepository.assignPupil(selectedCourse.id(), pupil.id());
		refreshAssignments();
	}

	private void removePupil(final Pupil pupil) {
		if (selectedCourse == null) {
			return;
		}

		courseRepository.removePupil(selectedCourse.id(), pupil.id());
		refreshAssignments();
	}

	private void refreshGrid() {
		setGridItems(courseRepository.findAll());
	}

	private void refreshAssignments() {
		if (selectedCourse == null) {
			setAssignmentRows(List.of());
			return;
		}

		final List<AssignmentRow> assignedRows = courseRepository.findPupils(selectedCourse.id()).stream()
				.map(pupil -> new AssignmentRow(pupil, true)).toList();
		final List<AssignmentRow> availableRows = courseRepository.findAssignablePupils(selectedCourse.id()).stream()
				.map(pupil -> new AssignmentRow(pupil, false)).toList();
		setAssignmentRows(
				Stream.concat(assignedRows.stream(), availableRows.stream()).sorted(ASSIGNMENT_ROW_ORDER).toList());
	}

	private void updateAssignmentsTab(final Course course) {
		if (course == null) {
			removeAssignmentsTab();
			return;
		}

		if (assignmentsTab == null) {
			if (assignmentsContent == null) {
				assignmentsContent = createAssignmentsContent();
			}
			assignmentsTab = getContextTabs().add("Schüler", assignmentsContent);
		}
		refreshAssignments();
	}

	private void removeAssignmentsTab() {
		if (assignmentsTab == null) {
			refreshAssignments();
			return;
		}

		if (getContextTabs().getSelectedTab() == assignmentsTab) {
			getContextTabs().setSelectedIndex(0);
		}
		getContextTabs().remove(assignmentsTab);
		assignmentsTab = null;
		refreshAssignments();
	}

	private void clearSingleEditor() {
		selectedCourse = null;
		schoolClass.clear();
		subject.clear();
		calendarYear.setValue(Year.now().getValue());
		coursePeriod.setValue(CoursePeriod.FULL_YEAR);
		lifecycle.setValue(Lifecycle.ACTIVE);
		updateArchiveButton();
	}

	private void updateArchiveButton() {
		archiveButton.setEnabled(selectedCourse != null && selectedCourse.lifecycle() == Lifecycle.ACTIVE);
	}

	private Lifecycle commonLifecycle(final List<Course> courses) {
		if (courses.isEmpty()) {
			return null;
		}

		final Lifecycle firstLifecycle = courses.get(0).lifecycle();
		return courses.stream().allMatch(course -> course.lifecycle() == firstLifecycle) ? firstLifecycle : null;
	}

	private void setBulkLifecycleValue(final Lifecycle value) {
		if (value == null) {
			bulkLifecycle.clear();
			return;
		}
		bulkLifecycle.setValue(value);
	}

	private void updateBulkApplyButton() {
		applyLifecycleButton.setEnabled(!selectedCourses.isEmpty() && bulkLifecycle.getValue() != null);
	}

	private void setAssignmentRows(final List<AssignmentRow> rows) {
		assignmentDataProvider = DataProvider.ofCollection(rows);
		assignmentGrid.setItems(assignmentDataProvider);
		applyAssignmentFilter();
	}

	private void applyAssignmentFilter() {
		if (assignmentDataProvider == null) {
			return;
		}

		final String searchValue = assignmentSearch.getValue().trim().toLowerCase();
		assignmentDataProvider.setFilter(row -> matchesAssignmentFilter(row, searchValue));
	}

	private boolean matchesAssignmentFilter(final AssignmentRow row, final String searchValue) {
		if (searchValue.isBlank()) {
			return row.assigned();
		}

		final String pupilText = String
				.join(" ", String.valueOf(row.pupil().id()), row.pupil().surname(), row.pupil().name()).toLowerCase();
		return pupilText.contains(searchValue);
	}

	private record AssignmentRow(Pupil pupil, boolean assigned) {
	}
}
