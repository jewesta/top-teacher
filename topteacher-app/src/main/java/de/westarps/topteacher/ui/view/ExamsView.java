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
import com.vaadin.flow.component.dialog.Dialog;
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
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.ExamNotesEditor;
import de.westarps.topteacher.ui.component.ExamResultsEditor;
import de.westarps.topteacher.ui.component.ExpectationHorizonEditor;
import de.westarps.topteacher.ui.component.GradingScaleViewer;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;

@Route(value = "exams", layout = MainLayout.class)
public class ExamsView extends AbstractMasterDataView<Exam> {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final ExpectationHorizonRepository expectationHorizonRepository;
	private final ExpectationHorizonEditor expectationHorizonEditor;
	private final ExamNotesEditor examNotesEditor;
	private final ExamResultsEditor examResultsEditor;
	private final GradingScaleViewer gradingScaleViewer;
	private final ComboBox<Course> courseFilter = new ComboBox<>("Kurs");
	private final TextField title = new TextField("Titel");
	private final DatePicker date = new DatePicker("Datum");
	private final Button saveButton = new Button();
	private final Button duplicateButton = new Button("Duplizieren...");
	private final Dialog duplicateDialog = new Dialog();
	private final TextField duplicateTitle = new TextField("Titel");
	private final DatePicker duplicateDate = new DatePicker("Datum");
	private final ComboBox<Course> duplicateCourse = new ComboBox<>("Kurs");
	private final Span multiSelectionSummary = new Span();

	private Course selectedCourse;
	private Exam selectedExam;
	private Tab expectationHorizonTab;
	private Tab notesTab;
	private Tab resultsTab;
	private Tab gradingScaleTab;

	public ExamsView(final CourseRepository courseRepository, final ExamRepository examRepository,
			final ExpectationHorizonRepository expectationHorizonRepository,
			final GradingScaleRepository gradingScaleRepository) {
		super("Klausuren", "tt-exams-view", new MultiSelectionGrid<>(Exam.class, false));
		this.courseRepository = courseRepository;
		this.examRepository = examRepository;
		this.expectationHorizonRepository = expectationHorizonRepository;
		this.expectationHorizonEditor = new ExpectationHorizonEditor(expectationHorizonRepository);
		this.examNotesEditor = new ExamNotesEditor(expectationHorizonRepository);
		this.examResultsEditor = new ExamResultsEditor(courseRepository, expectationHorizonRepository);
		this.gradingScaleViewer = new GradingScaleViewer(gradingScaleRepository);

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
		return AbstractFormEditor.responsive("tt-exam-editor", List.of(title, date),
				List.of(saveButton, duplicateButton));
	}

	@Override
	protected Component createMultiSelectEditor() {
		multiSelectionSummary.addClassName("tt-selection-summary");
		return AbstractFormEditor.contentOnly("tt-exam-bulk-editor", List.of(multiSelectionSummary));
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
		courseFilter.addClassName("tt-toolbar-filter");
		courseFilter.setItemLabelGenerator(Course::getDisplayName);
		courseFilter.setRequiredIndicatorVisible(true);
		courseFilter.addValueChangeListener(event -> {
			courseFilter.setInvalid(false);
			selectedCourse = event.getValue();
			clearSelection();
			clearSingleEditor();
			removeExamContextTabs();
			refreshGrid();
		});
	}

	private void configureEditors() {
		title.setRequiredIndicatorVisible(true);
		title.addValueChangeListener(event -> title.setInvalid(false));
		date.setLocale(Locale.GERMANY);
		date.setI18n(germanDatePickerI18n());
		date.setRequiredIndicatorVisible(true);
		date.addValueChangeListener(event -> date.setInvalid(false));

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveExam());

		duplicateButton.addClickListener(event -> openDuplicateDialog());

		configureDuplicateDialog();
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
		clearSingleEditorValidation();
		updateEditorEnabled();
	}

	private void showMultiSelectEditor(final List<Exam> exams) {
		selectedExam = null;
		multiSelectionSummary.setText(exams.size() + " Klausuren ausgewählt");
		updateEditorEnabled();
	}

	private void saveExam() {
		final String trimmedTitle = title.getValue().trim();
		if (!validateSingleEditor(trimmedTitle)) {
			return;
		}

		final Integer id = selectedExam == null ? null : selectedExam.id();
		final Integer courseId = selectedExam == null ? selectedCourse.id() : selectedExam.courseId();
		examRepository.save(new Exam(id, courseId, trimmedTitle, date.getValue()));

		refreshGrid();
		clearSelection();
		clearSingleEditor();
		removeExamContextTabs();
	}

	private void configureDuplicateDialog() {
		duplicateDialog.setHeaderTitle("Klausur duplizieren");

		duplicateTitle.setRequiredIndicatorVisible(true);
		duplicateTitle.setWidthFull();
		duplicateTitle.addValueChangeListener(event -> duplicateTitle.setInvalid(false));

		duplicateDate.setLocale(Locale.GERMANY);
		duplicateDate.setI18n(germanDatePickerI18n());
		duplicateDate.setRequiredIndicatorVisible(true);
		duplicateDate.setWidthFull();
		duplicateDate.addValueChangeListener(event -> duplicateDate.setInvalid(false));

		duplicateCourse.setItemLabelGenerator(Course::getDisplayName);
		duplicateCourse.setRequiredIndicatorVisible(true);
		duplicateCourse.setWidthFull();
		duplicateCourse.addValueChangeListener(event -> duplicateCourse.setInvalid(false));

		final Button cancelButton = new Button("Abbrechen", event -> duplicateDialog.close());
		final Button applyButton = new Button("Duplizieren", event -> duplicateExam());
		applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		final HorizontalLayout actions = new HorizontalLayout(cancelButton, applyButton);
		actions.setPadding(false);
		actions.setSpacing(true);

		final VerticalLayout content = new VerticalLayout(duplicateTitle, duplicateDate, duplicateCourse, actions);
		content.setPadding(false);
		content.setSpacing(true);
		content.setWidth("28rem");
		duplicateDialog.add(content);
	}

	private void openDuplicateDialog() {
		if (selectedExam == null || selectedCourse == null) {
			return;
		}

		final List<Course> activeCourses = courseRepository.findActive();
		duplicateCourse.setItems(activeCourses);
		duplicateCourse.setValue(selectedCourse);
		duplicateTitle.setValue(nextAvailableTitle(selectedCourse.id(), selectedExam.title()));
		duplicateDate.setValue(selectedExam.date());
		clearDuplicateDialogValidation();
		duplicateDialog.open();
	}

	private void duplicateExam() {
		if (selectedExam == null) {
			return;
		}

		final Course targetCourse = duplicateCourse.getValue();
		final String trimmedTitle = duplicateTitle.getValue().trim();
		if (!validateDuplicateDialog(trimmedTitle, targetCourse)) {
			return;
		}

		final String duplicateExamTitle = nextAvailableTitle(targetCourse.id(), trimmedTitle);
		final Exam duplicatedExam = examRepository.save(new Exam(null, targetCourse.id(), duplicateExamTitle,
				duplicateDate.getValue()));
		expectationHorizonRepository.copyDesignAndNotes(selectedExam.id(), duplicatedExam.id());

		duplicateDialog.close();
		if (!targetCourse.id().equals(selectedCourse.id())) {
			courseFilter.setValue(targetCourse);
		} else {
			refreshGrid();
		}
		getGrid().select(duplicatedExam);
		Notification.show("Klausur dupliziert.");
	}

	private String nextAvailableTitle(final int courseId, final String baseTitle) {
		String candidate = baseTitle;
		int suffix = 1;
		while (examRepository.existsByCourseIdAndTitle(courseId, candidate)) {
			candidate = baseTitle + " #" + suffix;
			suffix++;
		}
		return candidate;
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
		clearSingleEditorValidation();
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
			resultsTab = getContextTabs().add("Ergebnisse", examResultsEditor);
			gradingScaleTab = getContextTabs().add("Notenschlüssel", gradingScaleViewer);
		}

		expectationHorizonEditor.setExam(exam);
		examNotesEditor.setExam(exam);
		examResultsEditor.setExam(exam);
		gradingScaleViewer.setCourse(selectedCourse);
	}

	private void removeExamContextTabs() {
		if (expectationHorizonTab == null) {
			expectationHorizonEditor.setExam(null);
			examNotesEditor.setExam(null);
			examResultsEditor.setExam(null);
			gradingScaleViewer.setCourse(null);
			return;
		}

		if (getContextTabs().getSelectedTab() == expectationHorizonTab
				|| getContextTabs().getSelectedTab() == notesTab || getContextTabs().getSelectedTab() == resultsTab
				|| getContextTabs().getSelectedTab() == gradingScaleTab) {
			getContextTabs().setSelectedIndex(0);
		}
		getContextTabs().remove(expectationHorizonTab);
		getContextTabs().remove(notesTab);
		getContextTabs().remove(resultsTab);
		getContextTabs().remove(gradingScaleTab);
		expectationHorizonTab = null;
		notesTab = null;
		resultsTab = null;
		gradingScaleTab = null;
		expectationHorizonEditor.setExam(null);
		examNotesEditor.setExam(null);
		examResultsEditor.setExam(null);
		gradingScaleViewer.setCourse(null);
	}

	private void updateEditorEnabled() {
		final boolean enabled = selectedCourse != null;
		title.setEnabled(enabled);
		date.setEnabled(enabled);
		saveButton.setEnabled(enabled);
		saveButton.setText(selectedExam == null ? "Anlegen" : "Speichern");
		duplicateButton.setEnabled(enabled && selectedExam != null);
		duplicateButton.setVisible(selectedExam != null);
	}

	private boolean validateSingleEditor(final String trimmedTitle) {
		clearSingleEditorValidation();
		boolean valid = true;
		if (selectedCourse == null) {
			courseFilter.setErrorMessage("Kurs ist erforderlich.");
			courseFilter.setInvalid(true);
			valid = false;
		}
		if (trimmedTitle.isBlank()) {
			title.setErrorMessage("Titel ist erforderlich.");
			title.setInvalid(true);
			valid = false;
		}
		if (date.getValue() == null) {
			date.setErrorMessage("Datum ist erforderlich.");
			date.setInvalid(true);
			valid = false;
		}
		return valid;
	}

	private void clearSingleEditorValidation() {
		courseFilter.setInvalid(false);
		title.setInvalid(false);
		date.setInvalid(false);
	}

	private boolean validateDuplicateDialog(final String trimmedTitle, final Course targetCourse) {
		clearDuplicateDialogValidation();
		boolean valid = true;
		if (trimmedTitle.isBlank()) {
			duplicateTitle.setErrorMessage("Titel ist erforderlich.");
			duplicateTitle.setInvalid(true);
			valid = false;
		}
		if (duplicateDate.getValue() == null) {
			duplicateDate.setErrorMessage("Datum ist erforderlich.");
			duplicateDate.setInvalid(true);
			valid = false;
		}
		if (targetCourse == null) {
			duplicateCourse.setErrorMessage("Kurs ist erforderlich.");
			duplicateCourse.setInvalid(true);
			valid = false;
		}
		return valid;
	}

	private void clearDuplicateDialogValidation() {
		duplicateTitle.setInvalid(false);
		duplicateDate.setInvalid(false);
		duplicateCourse.setInvalid(false);
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
