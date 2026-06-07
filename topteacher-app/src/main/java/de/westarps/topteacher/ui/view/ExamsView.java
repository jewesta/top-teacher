package de.westarps.topteacher.ui.view;

import java.time.LocalDate;
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
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.loe.ExamNotesEditor;
import de.westarps.topteacher.ui.component.loe.ExamResultsEditor;
import de.westarps.topteacher.ui.component.loe.LevelOfExpectationsEditor;
import de.westarps.topteacher.ui.component.FormBinders;
import de.westarps.topteacher.ui.component.GradingScaleViewer;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;

@Route(value = "exams", layout = MainLayout.class)
public class ExamsView extends AbstractMasterDataView<Exam> {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final LevelOfExpectationsEditor levelOfExpectationsEditor;
	private final ExamNotesEditor examNotesEditor;
	private final ExamResultsEditor examResultsEditor;
	private final GradingScaleViewer gradingScaleViewer;
	private final ComboBox<Course> courseFilter = new ComboBox<>("Kurs");
	private final TextField title = new TextField("Titel");
	private final DatePicker date = new DatePicker("Datum");
	private final Binder<ExamFormData> examBinder = new Binder<>();
	private final Button saveButton = new Button();
	private final Button duplicateButton = new Button("Duplizieren...");
	private final Dialog duplicateDialog = new Dialog();
	private final TextField duplicateTitle = new TextField("Titel");
	private final DatePicker duplicateDate = new DatePicker("Datum");
	private final ComboBox<Course> duplicateCourse = new ComboBox<>("Kurs");
	private final Binder<DuplicateExamFormData> duplicateExamBinder = new Binder<>();
	private final Span multiSelectionSummary = new Span();

	private Course selectedCourse;
	private Exam selectedExam;
	private Tab levelOfExpectationsTab;
	private Tab notesTab;
	private Tab resultsTab;
	private Tab gradingScaleTab;

	public ExamsView(final CourseRepository courseRepository, final ExamRepository examRepository,
			final LevelOfExpectationsRepository levelOfExpectationsRepository,
			final GradingScaleRepository gradingScaleRepository) {
		super("Klausuren", "tt-exams-view", new MultiSelectionGrid<>(Exam.class, false));
		this.courseRepository = courseRepository;
		this.examRepository = examRepository;
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
		this.levelOfExpectationsEditor = new LevelOfExpectationsEditor(levelOfExpectationsRepository);
		this.examNotesEditor = new ExamNotesEditor(levelOfExpectationsRepository);
		this.examResultsEditor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
				gradingScaleRepository);
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
	protected String getEditorTabLabel() {
		return "Klausur";
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

		bindSingleEditor();

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

		readSingleEditor(new ExamFormData(exam.title(), exam.date()));
		updateEditorEnabled();
	}

	private void showMultiSelectEditor(final List<Exam> exams) {
		selectedExam = null;
		multiSelectionSummary.setText(exams.size() + " Klausuren ausgewählt");
		updateEditorEnabled();
	}

	private void saveExam() {
		final ExamFormData formData = new ExamFormData();
		if (!examBinder.writeBeanIfValid(formData)) {
			return;
		}

		final Integer id = selectedExam == null ? null : selectedExam.id();
		final Integer courseId = selectedExam == null ? selectedCourse.id() : selectedExam.courseId();
		examRepository.save(new Exam(id, courseId, formData.getTitle(), formData.getDate()));

		refreshGrid();
		clearSelection();
		clearSingleEditor();
		removeExamContextTabs();
	}

	private void configureDuplicateDialog() {
		duplicateDialog.setHeaderTitle("Klausur duplizieren");

		duplicateTitle.setRequiredIndicatorVisible(true);
		duplicateTitle.setWidthFull();

		duplicateDate.setLocale(Locale.GERMANY);
		duplicateDate.setI18n(germanDatePickerI18n());
		duplicateDate.setRequiredIndicatorVisible(true);
		duplicateDate.setWidthFull();

		duplicateCourse.setItemLabelGenerator(Course::getDisplayName);
		duplicateCourse.setRequiredIndicatorVisible(true);
		duplicateCourse.setWidthFull();
		bindDuplicateDialog();

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
		readDuplicateDialog(new DuplicateExamFormData(nextAvailableTitle(selectedCourse.id(), selectedExam.title()),
				selectedExam.date(), selectedCourse));
		duplicateDialog.open();
	}

	private void duplicateExam() {
		if (selectedExam == null) {
			return;
		}

		final DuplicateExamFormData formData = new DuplicateExamFormData();
		if (!duplicateExamBinder.writeBeanIfValid(formData)) {
			return;
		}

		final String duplicateExamTitle = nextAvailableTitle(formData.getCourse().id(), formData.getTitle());
		final Exam duplicatedExam = examRepository.save(new Exam(null, formData.getCourse().id(), duplicateExamTitle,
				formData.getDate()));
		levelOfExpectationsRepository.copyDesignAndNotes(selectedExam.id(), duplicatedExam.id());

		duplicateDialog.close();
		if (!formData.getCourse().id().equals(selectedCourse.id())) {
			courseFilter.setValue(formData.getCourse());
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
		readSingleEditor(new ExamFormData("", null));
		updateEditorEnabled();
	}

	private void updateExamContextTabs(final Exam exam) {
		if (exam == null) {
			removeExamContextTabs();
			return;
		}

		if (levelOfExpectationsTab == null) {
			levelOfExpectationsTab = getContextTabs().add("EH", levelOfExpectationsEditor);
			notesTab = getContextTabs().add("Notizen", examNotesEditor);
			resultsTab = getContextTabs().add("Ergebnisse", examResultsEditor);
			gradingScaleTab = getContextTabs().add("Notenschlüssel", gradingScaleViewer);
		}

		levelOfExpectationsEditor.setExam(exam);
		examNotesEditor.setExam(exam);
		examResultsEditor.setExam(exam);
		gradingScaleViewer.setCourse(selectedCourse);
	}

	private void removeExamContextTabs() {
		if (levelOfExpectationsTab == null) {
			levelOfExpectationsEditor.setExam(null);
			examNotesEditor.setExam(null);
			examResultsEditor.setExam(null);
			gradingScaleViewer.setCourse(null);
			return;
		}

		if (getContextTabs().getSelectedTab() == levelOfExpectationsTab
				|| getContextTabs().getSelectedTab() == notesTab || getContextTabs().getSelectedTab() == resultsTab
				|| getContextTabs().getSelectedTab() == gradingScaleTab) {
			getContextTabs().setSelectedIndex(0);
		}
		getContextTabs().remove(levelOfExpectationsTab);
		getContextTabs().remove(notesTab);
		getContextTabs().remove(resultsTab);
		getContextTabs().remove(gradingScaleTab);
		levelOfExpectationsTab = null;
		notesTab = null;
		resultsTab = null;
		gradingScaleTab = null;
		levelOfExpectationsEditor.setExam(null);
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

	private void bindSingleEditor() {
		examBinder.forField(title).withConverter(ExamsView::trim, value -> value)
				.withValidator(value -> !value.isBlank(), "Titel ist erforderlich.")
				.bind(ExamFormData::getTitle, ExamFormData::setTitle);
		examBinder.forField(date).asRequired("Datum ist erforderlich.").bind(ExamFormData::getDate,
				ExamFormData::setDate);
	}

	private void readSingleEditor(final ExamFormData formData) {
		examBinder.readBean(formData);
		FormBinders.clearValidation(examBinder);
	}

	private void bindDuplicateDialog() {
		duplicateExamBinder.forField(duplicateTitle).withConverter(ExamsView::trim, value -> value)
				.withValidator(value -> !value.isBlank(), "Titel ist erforderlich.")
				.bind(DuplicateExamFormData::getTitle, DuplicateExamFormData::setTitle);
		duplicateExamBinder.forField(duplicateDate).asRequired("Datum ist erforderlich.")
				.bind(DuplicateExamFormData::getDate, DuplicateExamFormData::setDate);
		duplicateExamBinder.forField(duplicateCourse).asRequired("Kurs ist erforderlich.")
				.bind(DuplicateExamFormData::getCourse, DuplicateExamFormData::setCourse);
	}

	private void readDuplicateDialog(final DuplicateExamFormData formData) {
		duplicateExamBinder.readBean(formData);
		FormBinders.clearValidation(duplicateExamBinder);
	}

	private static String trim(final String value) {
		return value == null ? "" : value.trim();
	}

	private static final class ExamFormData {

		private String title = "";
		private LocalDate date;

		private ExamFormData() {
		}

		private ExamFormData(final String title, final LocalDate date) {
			this.title = title;
			this.date = date;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(final String title) {
			this.title = title;
		}

		public LocalDate getDate() {
			return date;
		}

		public void setDate(final LocalDate date) {
			this.date = date;
		}
	}

	private static final class DuplicateExamFormData {

		private String title = "";
		private LocalDate date;
		private Course course;

		private DuplicateExamFormData() {
		}

		private DuplicateExamFormData(final String title, final LocalDate date, final Course course) {
			this.title = title;
			this.date = date;
			this.course = course;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(final String title) {
			this.title = title;
		}

		public LocalDate getDate() {
			return date;
		}

		public void setDate(final LocalDate date) {
			this.date = date;
		}

		public Course getCourse() {
			return course;
		}

		public void setCourse(final Course course) {
			this.course = course;
		}
	}
}
