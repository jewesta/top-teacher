package de.topteacher.ui.view;

import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;

import de.topteacher.backend.repo.CourseRepository;
import de.topteacher.model.Course;
import de.topteacher.model.CoursePeriod;
import de.topteacher.model.Lifecycle;
import de.topteacher.model.Pupil;
import de.topteacher.model.SchoolClass;
import de.topteacher.model.SchoolYear;
import de.topteacher.model.Subject;
import de.topteacher.ui.MainLayout;
import de.topteacher.ui.component.MultiSelectionGrid;

@Route(value = "courses", layout = MainLayout.class)
public class CoursesView extends AbstractMasterDataView<Course> {

	private static final Comparator<AssignmentRow> ASSIGNMENT_ROW_ORDER = Comparator
			.comparing((AssignmentRow row) -> row.pupil().name(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(row -> row.pupil().surname(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(row -> row.pupil().id());

	private final CourseRepository courseRepository;
	private final ComboBox<SchoolClass> schoolClass = new ComboBox<>("School class");
	private final ComboBox<Subject> subject = new ComboBox<>("Subject");
	private final IntegerField calendarYear = new IntegerField("Calendar year");
	private final ComboBox<CoursePeriod> coursePeriod = new ComboBox<>("Period");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Lifecycle");
	private final Button saveButton = new Button("Save");
	private final Button newButton = new Button("New");
	private final Button archiveButton = new Button("Archive");
	private final Span multiSelectionSummary = new Span();
	private final ComboBox<Lifecycle> bulkLifecycle = new ComboBox<>("Lifecycle");
	private final Button applyLifecycleButton = new Button("Apply");
	private final TextField assignmentSearch = new TextField();
	private final Grid<AssignmentRow> assignmentGrid = new Grid<>(AssignmentRow.class, false);

	private Course selectedCourse;
	private List<Course> selectedCourses = List.of();
	private ListDataProvider<AssignmentRow> assignmentDataProvider;
	private Tab assignmentsTab;
	private Component assignmentsContent;

	public CoursesView(final CourseRepository courseRepository) {
		super("Courses", "tt-courses-view", new MultiSelectionGrid<>(Course.class, false));
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
		grid.addColumn(course -> course.schoolYear().getDisplayName()).setHeader("School year").setAutoWidth(true);
		grid.addColumn(course -> course.coursePeriod().getDisplayName()).setHeader("Period").setAutoWidth(true);
		grid.addColumn(course -> course.schoolClass().getDisplayName()).setHeader("Class").setAutoWidth(true);
		grid.addColumn(course -> course.subject().getDisplayName()).setHeader("Subject").setAutoWidth(true);
		grid.addColumn(course -> course.lifecycle().getDisplayName()).setHeader("Lifecycle").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		final FormLayout form = new FormLayout(schoolClass, subject, calendarYear, coursePeriod, lifecycle);
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("32rem", 2));

		final HorizontalLayout buttons = new HorizontalLayout(saveButton, newButton, archiveButton);
		buttons.setSpacing(true);

		final VerticalLayout editor = new VerticalLayout(form, buttons);
		editor.addClassNames("tt-editor", "tt-course-editor");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
	}

	@Override
	protected Component createMultiSelectEditor() {
		multiSelectionSummary.addClassName("tt-selection-summary");

		final FormLayout form = new FormLayout(bulkLifecycle);
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

		final HorizontalLayout buttons = new HorizontalLayout(applyLifecycleButton);
		buttons.setSpacing(true);

		final VerticalLayout editor = new VerticalLayout(multiSelectionSummary, form, buttons);
		editor.addClassNames("tt-editor", "tt-course-bulk-editor");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
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
		assignmentSearch.addClassName("tt-assignment-search");
		assignmentSearch.setClearButtonVisible(true);
		assignmentSearch.setPlaceholder("Search pupils");
		assignmentSearch.setPrefixComponent(VaadinIcon.SEARCH.create());
		assignmentSearch.setValueChangeMode(ValueChangeMode.EAGER);
		assignmentSearch.setWidthFull();
		assignmentSearch.addValueChangeListener(event -> applyAssignmentFilter());

		assignmentGrid.addColumn(row -> row.pupil().id()).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
		assignmentGrid.addColumn(row -> row.pupil().surname()).setHeader("Surname").setAutoWidth(true);
		assignmentGrid.addColumn(row -> row.pupil().name()).setHeader("Name").setAutoWidth(true);
		assignmentGrid.addComponentColumn(this::createAssignmentActionButton).setHeader("").setAutoWidth(true)
				.setFlexGrow(0).setFrozenToEnd(true).setTextAlign(ColumnTextAlign.CENTER)
				.setPartNameGenerator(row -> "tt-assignment-action-cell");
		assignmentGrid.addClassName("tt-assignment-grid");
		assignmentGrid.setSelectionMode(Grid.SelectionMode.NONE);
		assignmentGrid.setSizeFull();
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

	private Button createAssignmentActionButton(final AssignmentRow row) {
		final Button button = new Button();
		button.addThemeVariants(ButtonVariant.LUMO_SMALL);
		button.setIcon(row.assigned() ? VaadinIcon.MINUS_CIRCLE.create() : VaadinIcon.PLUS_CIRCLE.create());
		button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_PRIMARY,
				row.assigned() ? ButtonVariant.LUMO_ERROR : ButtonVariant.LUMO_SUCCESS);
		button.setTooltipText(row.assigned() ? "Remove pupil from course" : "Assign pupil to course");
		button.addClickListener(event -> {
			if (row.assigned()) {
				removePupil(row.pupil());
			} else {
				assignPupil(row.pupil());
			}
		});
		return button;
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
		multiSelectionSummary.setText(selectedCourses.size() + " courses selected");
		setBulkLifecycleValue(commonLifecycle(selectedCourses));
		updateBulkApplyButton();
	}

	private void saveCourse() {
		if (!hasRequiredCourseValues()) {
			Notification.show("School class, subject, calendar year, period and lifecycle are required.");
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
		Notification.show("Updated lifecycle for " + selectedCourses.size() + " courses.");
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
		setAssignmentRows(Stream.concat(assignedRows.stream(), availableRows.stream()).sorted(ASSIGNMENT_ROW_ORDER)
				.toList());
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
			assignmentsTab = getContextTabs().add("Assignments", assignmentsContent);
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
