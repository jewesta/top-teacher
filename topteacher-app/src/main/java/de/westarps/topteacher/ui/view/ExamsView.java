package de.westarps.topteacher.ui.view;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.ExpectationHorizonRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.ExamNotesEditor;
import de.westarps.topteacher.ui.component.ExpectationHorizonEditor;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;

@Route(value = "exams", layout = MainLayout.class)
public class ExamsView extends AbstractMasterDataView<Exam> {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final ExpectationHorizonEditor expectationHorizonEditor;
	private final ExamNotesEditor examNotesEditor;
	private final ComboBox<Course> courseFilter = new ComboBox<>("Kurs");
	private final TextField title = new TextField("Titel");
	private final DatePicker date = new DatePicker("Datum");
	private final Button saveButton = new Button("Speichern");
	private final Button newButton = new Button("Neu");
	private final Span multiSelectionSummary = new Span();

	private Course selectedCourse;
	private Exam selectedExam;
	private Tab expectationHorizonTab;
	private Tab notesTab;

	public ExamsView(final CourseRepository courseRepository, final ExamRepository examRepository,
			final ExpectationHorizonRepository expectationHorizonRepository) {
		super("Klausuren", "tt-exams-view", new MultiSelectionGrid<>(Exam.class, false));
		this.courseRepository = courseRepository;
		this.examRepository = examRepository;
		this.expectationHorizonEditor = new ExpectationHorizonEditor(expectationHorizonRepository);
		this.examNotesEditor = new ExamNotesEditor(expectationHorizonRepository);

		configureCourseFilter();
		configureEditors();
		initializeView();
		refreshCourseFilter();
		clearSingleEditor();
	}

	@Override
	protected void configureGrid(final MultiSelectionGrid<Exam> grid) {
		grid.addColumn(Exam::title).setHeader("Titel").setAutoWidth(true);
		grid.addColumn(exam -> DATE_FORMATTER.format(exam.date())).setHeader("Datum").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		final FormLayout form = new FormLayout(title, date);
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

		final HorizontalLayout buttons = new HorizontalLayout(saveButton, newButton);
		buttons.setSpacing(true);

		final VerticalLayout editor = new VerticalLayout(form, buttons);
		editor.addClassNames("tt-editor", "tt-exam-editor");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
	}

	@Override
	protected Component createMultiSelectEditor() {
		multiSelectionSummary.addClassName("tt-selection-summary");

		final VerticalLayout editor = new VerticalLayout(multiSelectionSummary);
		editor.addClassNames("tt-editor", "tt-exam-bulk-editor");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
	}

	@Override
	protected String getSearchText(final Exam exam) {
		return String.join(" ", String.valueOf(exam.id()), exam.title(), DATE_FORMATTER.format(exam.date()),
				exam.date().toString());
	}

	@Override
	protected Component createListToolbarPrefix() {
		return courseFilter;
	}

	@Override
	protected double getSplitterPosition() {
		return 30;
	}

	@Override
	protected String getContextAreaMinWidth() {
		return "34rem";
	}

	@Override
	protected void onEditorModeChanged(final EditorMode editorMode, final List<Exam> selectedItems) {
		if (editorMode == EditorMode.MULTI_SELECT) {
			removeExamContextTabs();
			showMultiSelectEditor(selectedItems);
			return;
		}

		final Exam exam = selectedItems.isEmpty() ? null : selectedItems.get(0);
		showSingleSelectEditor(exam);
		updateExamContextTabs(exam);
	}

	private void configureCourseFilter() {
		courseFilter.setItemLabelGenerator(Course::getDisplayName);
		courseFilter.setRequiredIndicatorVisible(true);
		courseFilter.setWidth("24rem");
		courseFilter.setMaxWidth("100%");
		courseFilter.addValueChangeListener(event -> {
			selectedCourse = event.getValue();
			clearSelection();
			clearSingleEditor();
			removeExamContextTabs();
			refreshGrid();
		});
	}

	private void configureEditors() {
		title.setRequiredIndicatorVisible(true);
		date.setLocale(Locale.GERMANY);
		date.setI18n(germanDatePickerI18n());
		date.setRequiredIndicatorVisible(true);

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveExam());

		newButton.addClickListener(event -> {
			clearSelection();
			clearSingleEditor();
			removeExamContextTabs();
		});
	}

	private void refreshCourseFilter() {
		final List<Course> activeCourses = courseRepository.findActive();
		courseFilter.setItems(activeCourses);
		if (activeCourses.isEmpty()) {
			selectedCourse = null;
			setGridItems(List.of());
			updateEditorEnabled();
			return;
		}

		if (selectedCourse == null
				|| activeCourses.stream().noneMatch(course -> course.id().equals(selectedCourse.id()))) {
			courseFilter.setValue(activeCourses.get(0));
		} else {
			refreshGrid();
		}
	}

	private void showSingleSelectEditor(final Exam exam) {
		selectedExam = exam;
		if (exam == null) {
			clearSingleEditor();
			return;
		}

		title.setValue(exam.title());
		date.setValue(exam.date());
		updateEditorEnabled();
	}

	private void showMultiSelectEditor(final List<Exam> exams) {
		selectedExam = null;
		multiSelectionSummary.setText(exams.size() + " Klausuren ausgewählt");
		updateEditorEnabled();
	}

	private void saveExam() {
		if (selectedCourse == null) {
			Notification.show("Bitte wählen Sie einen Kurs aus.");
			return;
		}

		final String trimmedTitle = title.getValue().trim();
		if (trimmedTitle.isBlank() || date.getValue() == null) {
			Notification.show("Titel und Datum sind erforderlich.");
			return;
		}

		final Integer id = selectedExam == null ? null : selectedExam.id();
		final Integer courseId = selectedExam == null ? selectedCourse.id() : selectedExam.courseId();
		examRepository.save(new Exam(id, courseId, trimmedTitle, date.getValue()));

		refreshGrid();
		clearSingleEditor();
		removeExamContextTabs();
	}

	private void refreshGrid() {
		if (selectedCourse == null) {
			setGridItems(List.of());
			return;
		}

		setGridItems(examRepository.findByCourseId(selectedCourse.id()));
	}

	private void clearSingleEditor() {
		selectedExam = null;
		title.clear();
		date.clear();
		updateEditorEnabled();
	}

	private void updateExamContextTabs(final Exam exam) {
		if (exam == null) {
			removeExamContextTabs();
			return;
		}

		if (expectationHorizonTab == null) {
			expectationHorizonTab = getContextTabs().add("EH", expectationHorizonEditor);
			notesTab = getContextTabs().add("Notizen", examNotesEditor);
		}

		expectationHorizonEditor.setExam(exam);
		examNotesEditor.setExam(exam);
	}

	private void removeExamContextTabs() {
		if (expectationHorizonTab == null) {
			expectationHorizonEditor.setExam(null);
			examNotesEditor.setExam(null);
			return;
		}

		if (getContextTabs().getSelectedTab() == expectationHorizonTab
				|| getContextTabs().getSelectedTab() == notesTab) {
			getContextTabs().setSelectedIndex(0);
		}
		getContextTabs().remove(expectationHorizonTab);
		getContextTabs().remove(notesTab);
		expectationHorizonTab = null;
		notesTab = null;
		expectationHorizonEditor.setExam(null);
		examNotesEditor.setExam(null);
	}

	private void updateEditorEnabled() {
		final boolean enabled = selectedCourse != null;
		title.setEnabled(enabled);
		date.setEnabled(enabled);
		saveButton.setEnabled(enabled);
		newButton.setEnabled(enabled);
	}

	private DatePickerI18n germanDatePickerI18n() {
		return new DatePickerI18n()
				.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
						"September", "Oktober", "November", "Dezember"))
				.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"))
				.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")).setFirstDayOfWeek(1)
				.setDateFormat("dd.MM.yyyy").setToday("Heute").setCancel("Abbrechen")
				.setBadInputErrorMessage("Bitte geben Sie ein gültiges Datum ein.")
				.setRequiredErrorMessage("Datum ist erforderlich.");
	}
}
