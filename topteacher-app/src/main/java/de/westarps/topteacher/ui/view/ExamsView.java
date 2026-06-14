package de.westarps.topteacher.ui.view;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
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
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.ExamNumber;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.FormBinders;
import de.westarps.topteacher.ui.component.GradingScaleViewer;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;
import de.westarps.topteacher.ui.component.PupilAssignmentGrid;
import de.westarps.topteacher.ui.component.loe.ExamEvaluationViewer;
import de.westarps.topteacher.ui.component.loe.ExamNotesEditor;
import de.westarps.topteacher.ui.component.loe.ExamResultsEditor;
import de.westarps.topteacher.ui.component.loe.LevelOfExpectationsEditor;

@Route(value = "exams", layout = MainLayout.class)
public class ExamsView extends AbstractMasterDataView<Exam> {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final LevelOfExpectationsEditor levelOfExpectationsEditor;
	private final ExamNotesEditor examNotesEditor;
	private final ExamResultsEditor examResultsEditor;
	private final ExamEvaluationViewer examEvaluationViewer;
	private final GradingScaleViewer gradingScaleViewer;
	private final ComboBox<Course> courseFilter = new ComboBox<>("Kurs");
	private final TextField title = new TextField("Titel");
	private final DatePicker date = new DatePicker("Datum");
	private final ComboBox<Exam> originalExam = new ComboBox<>("Nachschreibeklausur zu");
	private final MultiSelectComboBox<Pupil> creationPupils = new MultiSelectComboBox<>("Teilnehmende Schüler:innen");
	private final Binder<ExamFormData> examBinder = new Binder<>();
	private final Button saveButton = new Button();
	private final Button duplicateButton = new Button("Duplizieren...");
	private final Dialog duplicateDialog = new Dialog();
	private final TextField duplicateTitle = new TextField("Titel");
	private final DatePicker duplicateDate = new DatePicker("Datum");
	private final ComboBox<Course> duplicateCourse = new ComboBox<>("Kurs");
	private final Binder<DuplicateExamFormData> duplicateExamBinder = new Binder<>();
	private final Span multiSelectionSummary = new Span();
	private final PupilAssignmentGrid pupilAssignmentGrid = new PupilAssignmentGrid("Schüler:innen suchen");

	private Course selectedCourse;
	private Exam selectedExam;
	private Map<Integer, ExamNumber> examNumbersByExamId = Map.of();
	private List<Exam> originalExamCandidates = List.of();
	private Tab pupilsTab;
	private Tab levelOfExpectationsTab;
	private Tab notesTab;
	private Tab resultsTab;
	private Tab evaluationTab;
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
		this.examResultsEditor = new ExamResultsEditor(courseRepository, examRepository, levelOfExpectationsRepository,
				gradingScaleRepository);
		this.examEvaluationViewer = new ExamEvaluationViewer(courseRepository, examRepository,
				levelOfExpectationsRepository, gradingScaleRepository);
		this.gradingScaleViewer = new GradingScaleViewer(gradingScaleRepository);

		configureCourseFilter();
		configureEditors();
		configurePupilAssignments();
		initializeView();
		refreshCourseFilter();
		clearSingleEditor();
	}

	@Override
	protected void configureGrid(final MultiSelectionGrid<Exam> grid) {
		grid.addColumn(this::examNumberLabel).setHeader("Nr.").setWidth("6rem").setFlexGrow(0);
		grid.addColumn(Exam::title).setHeader("Titel").setAutoWidth(true);
		grid.addColumn(exam -> DATE_FORMATTER.format(exam.date())).setHeader("Datum").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		return AbstractFormEditor.responsive("tt-exam-editor", List.of(title, date, originalExam, creationPupils),
				List.of(saveButton, duplicateButton));
	}

	@Override
	protected Component createMultiSelectEditor() {
		multiSelectionSummary.addClassName("tt-selection-summary");
		return AbstractFormEditor.contentOnly("tt-exam-bulk-editor", List.of(multiSelectionSummary));
	}

	@Override
	protected String getSearchText(final Exam exam) {
		return String.join(" ", String.valueOf(exam.id()), examNumberLabel(exam), exam.title(),
				DATE_FORMATTER.format(exam.date()), exam.date().toString());
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
		courseFilter.setWidth("16rem");
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
		date.addValueChangeListener(event -> examBinder.validate());

		originalExam.setClearButtonVisible(true);
		originalExam.setItemLabelGenerator(this::originalExamLabel);
		originalExam.addValueChangeListener(event -> {
			examBinder.validate();
			if (selectedExam == null) {
				refreshCreationPupilOptions();
			}
		});

		creationPupils.setItemLabelGenerator(this::pupilLabel);
		creationPupils.setClearButtonVisible(true);
		creationPupils.setWidthFull();

		bindSingleEditor();

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveExam());

		duplicateButton.addClickListener(event -> openDuplicateDialog());

		configureDuplicateDialog();
	}

	private void configurePupilAssignments() {
		pupilAssignmentGrid.setAssignAction(this::assignPupilToExam);
		pupilAssignmentGrid.setRemoveAction(this::removePupilFromExam);
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

		refreshOriginalExamItems();
		readSingleEditor(new ExamFormData(exam.title(), exam.date(), originalExam(exam)));
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
		final Integer originalExamId = formData.getOriginalExam() == null ? null : formData.getOriginalExam().id();
		try {
			final Exam exam = new Exam(id, courseId, formData.getTitle(), formData.getDate(), originalExamId);
			if (selectedExam == null) {
				examRepository.save(exam, selectedCreationPupilIds());
			} else {
				examRepository.save(exam);
			}
		} catch (final IllegalArgumentException exception) {
			Notification.show(exception.getMessage());
			return;
		}

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
		final Exam duplicatedExam = examRepository
				.save(new Exam(null, formData.getCourse().id(), duplicateExamTitle, formData.getDate()));
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
			examNumbersByExamId = Map.of();
			originalExamCandidates = List.of();
			originalExam.setItems(originalExamCandidates);
			setGridItems(List.of());
			return;
		}

		final List<Exam> exams = examRepository.findByCourseId(selectedCourse.id());
		examNumbersByExamId = examRepository.findNumbersByCourseId(selectedCourse.id());
		setGridItems(exams);
		refreshOriginalExamItems();
	}

	private void clearSingleEditor() {
		selectedExam = null;
		refreshOriginalExamItems();
		readSingleEditor(new ExamFormData("", null, null));
		refreshCreationPupilOptions();
		updateEditorEnabled();
	}

	private void updateExamContextTabs(final Exam exam) {
		if (exam == null) {
			removeExamContextTabs();
			return;
		}

		if (levelOfExpectationsTab == null) {
			pupilsTab = getContextTabs().add("Schüler:innen", pupilAssignmentGrid);
			levelOfExpectationsTab = getContextTabs().add("EH", levelOfExpectationsEditor);
			notesTab = getContextTabs().add("Notizen", examNotesEditor);
			resultsTab = getContextTabs().add("Ergebnisse", examResultsEditor);
			evaluationTab = getContextTabs().add("Auswertung", examEvaluationViewer);
			gradingScaleTab = getContextTabs().add("Notenschlüssel", gradingScaleViewer);
		}

		refreshPupilAssignments();
		levelOfExpectationsEditor.setExam(exam);
		examNotesEditor.setExam(exam);
		examResultsEditor.setExam(exam);
		examEvaluationViewer.setExam(exam);
		gradingScaleViewer.setCourse(selectedCourse);
	}

	private void removeExamContextTabs() {
		if (pupilsTab == null) {
			pupilAssignmentGrid.setRows(List.of(), List.of());
			levelOfExpectationsEditor.setExam(null);
			examNotesEditor.setExam(null);
			examResultsEditor.setExam(null);
			examEvaluationViewer.setExam(null);
			gradingScaleViewer.setCourse(null);
			return;
		}

		if (getContextTabs().getSelectedTab() == pupilsTab
				|| getContextTabs().getSelectedTab() == levelOfExpectationsTab
				|| getContextTabs().getSelectedTab() == notesTab || getContextTabs().getSelectedTab() == resultsTab
				|| getContextTabs().getSelectedTab() == evaluationTab
				|| getContextTabs().getSelectedTab() == gradingScaleTab) {
			getContextTabs().setSelectedIndex(0);
		}
		getContextTabs().remove(pupilsTab);
		getContextTabs().remove(levelOfExpectationsTab);
		getContextTabs().remove(notesTab);
		getContextTabs().remove(resultsTab);
		getContextTabs().remove(evaluationTab);
		getContextTabs().remove(gradingScaleTab);
		pupilsTab = null;
		levelOfExpectationsTab = null;
		notesTab = null;
		resultsTab = null;
		evaluationTab = null;
		gradingScaleTab = null;
		pupilAssignmentGrid.setRows(List.of(), List.of());
		levelOfExpectationsEditor.setExam(null);
		examNotesEditor.setExam(null);
		examResultsEditor.setExam(null);
		examEvaluationViewer.setExam(null);
		gradingScaleViewer.setCourse(null);
	}

	private void updateEditorEnabled() {
		final boolean enabled = selectedCourse != null;
		title.setEnabled(enabled);
		date.setEnabled(enabled);
		originalExam.setEnabled(enabled);
		creationPupils.setEnabled(enabled && selectedExam == null);
		creationPupils.setVisible(selectedExam == null);
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
		examBinder.forField(originalExam)
				.withValidator(this::hasValidMakeupExamDate,
						"Eine Nachschreibeklausur darf nicht vor der ursprünglichen Klausur liegen.")
				.bind(ExamFormData::getOriginalExam, ExamFormData::setOriginalExam);
	}

	private void readSingleEditor(final ExamFormData formData) {
		examBinder.readBean(formData);
		FormBinders.clearValidation(examBinder);
	}

	private void refreshCreationPupilOptions() {
		if (selectedCourse == null || selectedExam != null) {
			creationPupils.setItems(List.of());
			creationPupils.clear();
			return;
		}

		final List<Pupil> coursePupils = courseRepository.findPupils(selectedCourse.id());
		creationPupils.setItems(coursePupils);
		creationPupils.setValue(defaultCreationPupilSelection(coursePupils));
	}

	private Set<Pupil> defaultCreationPupilSelection(final List<Pupil> coursePupils) {
		final Exam selectedOriginalExam = originalExam.getValue();
		if (selectedOriginalExam == null) {
			return new LinkedHashSet<>(coursePupils);
		}

		final Set<Integer> originalPupilIds = examRepository.findPupils(selectedOriginalExam.id()).stream()
				.map(Pupil::id).collect(Collectors.toSet());
		return coursePupils.stream().filter(pupil -> !originalPupilIds.contains(pupil.id()))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Set<Integer> selectedCreationPupilIds() {
		return creationPupils.getValue().stream().map(Pupil::id).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private void refreshPupilAssignments() {
		if (selectedExam == null) {
			pupilAssignmentGrid.setRows(List.of(), List.of());
			return;
		}

		pupilAssignmentGrid.setRows(examRepository.findPupils(selectedExam.id()),
				examRepository.findAssignablePupils(selectedExam.id()),
				examRepository.findPupilRemovalLocks(selectedExam.id()));
	}

	private void assignPupilToExam(final Pupil pupil) {
		if (selectedExam == null) {
			return;
		}

		try {
			examRepository.assignPupil(selectedExam.id(), pupil.id());
		} catch (final IllegalArgumentException exception) {
			Notification.show(exception.getMessage());
		}
		refreshPupilAssignments();
		refreshParticipantConsumers();
	}

	private void removePupilFromExam(final Pupil pupil) {
		if (selectedExam == null) {
			return;
		}

		try {
			examRepository.removePupil(selectedExam.id(), pupil.id());
		} catch (final IllegalArgumentException exception) {
			Notification.show(exception.getMessage());
		}
		refreshPupilAssignments();
		refreshParticipantConsumers();
	}

	private void refreshParticipantConsumers() {
		if (selectedExam == null) {
			return;
		}

		examResultsEditor.setExam(selectedExam);
		examEvaluationViewer.setExam(selectedExam);
	}

	private void refreshOriginalExamItems() {
		if (selectedCourse == null) {
			originalExamCandidates = List.of();
		} else {
			final Integer selectedExamId = selectedExam == null ? null : selectedExam.id();
			originalExamCandidates = examRepository.findMainExamsByCourseId(selectedCourse.id()).stream()
					.filter(candidate -> selectedExamId == null || !selectedExamId.equals(candidate.id())).toList();
		}
		originalExam.setItems(originalExamCandidates);
	}

	private Exam originalExam(final Exam exam) {
		if (exam.originalExamId() == null) {
			return null;
		}
		return originalExamCandidates.stream().filter(candidate -> exam.originalExamId().equals(candidate.id()))
				.findFirst().orElse(null);
	}

	private String examNumberLabel(final Exam exam) {
		final ExamNumber examNumber = examNumbersByExamId.get(exam.id());
		return examNumber == null ? "" : examNumber.getDisplayName();
	}

	private String originalExamLabel(final Exam exam) {
		return String.join(" ", examNumberLabel(exam), DATE_FORMATTER.format(exam.date()), "-", exam.title()).trim();
	}

	private String pupilLabel(final Pupil pupil) {
		return pupil.surname() + ", " + pupil.name();
	}

	private boolean hasValidMakeupExamDate(final Exam originalExam) {
		final LocalDate selectedDate = date.getValue();
		return originalExam == null || selectedDate == null || !selectedDate.isBefore(originalExam.date());
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
		private Exam originalExam;

		private ExamFormData() {
		}

		private ExamFormData(final String title, final LocalDate date, final Exam originalExam) {
			this.title = title;
			this.date = date;
			this.originalExam = originalExam;
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

		public Exam getOriginalExam() {
			return originalExam;
		}

		public void setOriginalExam(final Exam originalExam) {
			this.originalExam = originalExam;
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
