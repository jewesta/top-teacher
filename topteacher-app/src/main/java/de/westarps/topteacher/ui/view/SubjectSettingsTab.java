package de.westarps.topteacher.ui.view;

import java.util.List;

import org.springframework.core.annotation.Order;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.FormBinders;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

@Order(5)
@UIScope
@SpringComponent
public class SubjectSettingsTab extends SplitListDetailView<Subject> implements SettingsTab {

	private final SubjectRepository subjectRepository;
	private final TextField name = new TextField("Fach");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Status");
	private final Binder<SubjectFormData> binder = new Binder<>();
	private final Button newButton = createNewButton();
	private final Button saveButton = Buttons.createOrSave();
	private final Button archiveButton = Buttons.archive();
	private final ComboBox<Lifecycle> bulkLifecycle = new ComboBox<>("Status");
	private final Button applyLifecycleButton = new Button("Anwenden");

	private Subject selectedSubject;
	private List<Subject> selectedSubjects = List.of();

	public SubjectSettingsTab(final SubjectRepository subjectRepository) {
		super("Fächer", "tt-subject-settings-tab", new MultiSelectionGrid<>(Subject.class, false));
		this.subjectRepository = subjectRepository;

		getSearchField().setPlaceholder("Fach suchen");
		configureEditors();
		initializeView();
		refreshGrid();
		clearSingleEditor();
	}

	@Override
	public String label() {
		return "Fächer";
	}

	@Override
	public Component content() {
		return this;
	}

	@Override
	protected void configureGrid(final MultiSelectionGrid<Subject> grid) {
		grid.addColumn(Subject::name).setHeader("Fach").setAutoWidth(true);
		grid.addColumn(subject -> subject.lifecycle().getDisplayName()).setHeader("Status").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		return AbstractFormEditor.singleColumn("tt-subject-settings-editor", List.of(), List.of(name, lifecycle),
				List.of(saveButton, archiveButton));
	}

	@Override
	protected Component createMultiSelectEditor() {
		return AbstractFormEditor.singleColumn("tt-subject-settings-bulk-editor", List.of(), List.of(bulkLifecycle),
				List.of(applyLifecycleButton));
	}

	@Override
	protected List<Component> createListToolbarComponents() {
		return List.of(newButton);
	}

	@Override
	protected String getEditorTabLabel() {
		return "Fach";
	}

	@Override
	protected String getCreateEditorStatus() {
		return "Neues Fach";
	}

	@Override
	protected String getSingleEditorStatus(final Subject selectedItem) {
		return "Fach bearbeiten";
	}

	@Override
	protected String getMultiEditorStatus(final List<Subject> selectedItems) {
		return selectedItems.size() + " Fächer ausgewählt";
	}

	@Override
	protected String getSearchText(final Subject subject) {
		return String.join(" ", subject.name(), subject.lifecycle().getDisplayName(), subject.lifecycle().name());
	}

	@Override
	protected void onEditorModeChanged(final EditorMode editorMode, final List<Subject> selectedItems) {
		if (editorMode == EditorMode.MULTI_SELECT) {
			showMultiSelectEditor(selectedItems);
			return;
		}

		final Subject subject = selectedItems.isEmpty() ? null : selectedItems.get(0);
		showSingleSelectEditor(subject);
	}

	private void configureEditors() {
		name.setClearButtonVisible(true);
		name.setValueChangeMode(ValueChangeMode.EAGER);
		name.setRequiredIndicatorVisible(true);

		lifecycle.setItems(Lifecycle.values());
		lifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		lifecycle.setRequiredIndicatorVisible(true);

		bindSingleEditor();

		newButton.addClickListener(event -> {
			clearSelection();
			clearSingleEditor();
		});

		saveButton.addClickListener(event -> saveSubject());

		archiveButton.addClickListener(event -> TopTeacherDialogs.openArchiveConfirmation("Fach archivieren?",
				"Das Fach wird archiviert. Das bedeutet, dass das Fach standardmäßig nicht mehr angezeigt wird und nicht neu zugeordnet werden kann.",
				"Bestehende Kurse und Klausuren bleiben erhalten. Sie können die Archivierung wieder rückgängig machen.",
				this::archiveSelectedSubject));

		bulkLifecycle.setItems(Lifecycle.values());
		bulkLifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		bulkLifecycle.setClearButtonVisible(true);
		bulkLifecycle.addValueChangeListener(event -> updateBulkApplyButton());

		applyLifecycleButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		applyLifecycleButton.addClickListener(event -> applyLifecycleToSelectedSubjects());
		updateBulkApplyButton();
	}

	private void showSingleSelectEditor(final Subject subject) {
		selectedSubjects = List.of();
		selectedSubject = subject;
		if (subject == null) {
			clearSingleEditor();
			return;
		}

		readSingleEditor(new SubjectFormData(subject.name(), subject.lifecycle()));
		updateEditorModeControls();
	}

	private void showMultiSelectEditor(final List<Subject> subjects) {
		selectedSubject = null;
		selectedSubjects = List.copyOf(subjects);
		setBulkLifecycleValue(commonLifecycle(selectedSubjects));
		updateBulkApplyButton();
	}

	private void saveSubject() {
		final SubjectFormData formData = new SubjectFormData();
		if (!binder.writeBeanIfValid(formData)) {
			return;
		}

		final Integer id = selectedSubject == null ? null : selectedSubject.id();
		subjectRepository.save(new Subject(id, formData.getName(), formData.getLifecycle()));
		refreshGrid();
		clearSelection();
		clearSingleEditor();
		Notification.show("Fach gespeichert.");
	}

	private void archiveSelectedSubject() {
		if (selectedSubject == null) {
			return;
		}

		subjectRepository.archive(selectedSubject.id());
		refreshGrid();
		clearSelection();
		clearSingleEditor();
		Notification.show("Fach archiviert.");
	}

	private void applyLifecycleToSelectedSubjects() {
		final Lifecycle selectedLifecycle = bulkLifecycle.getValue();
		if (selectedSubjects.isEmpty() || selectedLifecycle == null) {
			return;
		}

		selectedSubjects.forEach(
				subject -> subjectRepository.save(new Subject(subject.id(), subject.name(), selectedLifecycle)));
		Notification.show("Status für " + selectedSubjects.size() + " Fächer aktualisiert.");
		refreshGrid();
	}

	private void refreshGrid() {
		setGridItems(subjectRepository.findAll());
	}

	private void clearSingleEditor() {
		selectedSubject = null;
		readSingleEditor(new SubjectFormData("", Lifecycle.ACTIVE));
		updateEditorModeControls();
	}

	private void updateEditorModeControls() {
		final boolean editMode = selectedSubject != null;
		Buttons.setCreateOrSaveMode(saveButton, editMode);
		lifecycle.setVisible(editMode);
		archiveButton.setVisible(editMode && selectedSubject.lifecycle() == Lifecycle.ACTIVE);
	}

	private void bindSingleEditor() {
		binder.forField(name).asRequired("Fach ist erforderlich.")
				.withConverter(SubjectSettingsTab::trim, value -> value)
				.bind(SubjectFormData::getName, SubjectFormData::setName);
		binder.forField(lifecycle).asRequired("Status ist erforderlich.").bind(SubjectFormData::getLifecycle,
				SubjectFormData::setLifecycle);
	}

	private void readSingleEditor(final SubjectFormData formData) {
		binder.readBean(formData);
		FormBinders.clearValidation(binder);
	}

	private Lifecycle commonLifecycle(final List<Subject> subjects) {
		if (subjects.isEmpty()) {
			return null;
		}

		final Lifecycle firstLifecycle = subjects.get(0).lifecycle();
		return subjects.stream().allMatch(subject -> subject.lifecycle() == firstLifecycle) ? firstLifecycle : null;
	}

	private void setBulkLifecycleValue(final Lifecycle value) {
		if (value == null) {
			bulkLifecycle.clear();
			return;
		}
		bulkLifecycle.setValue(value);
	}

	private void updateBulkApplyButton() {
		applyLifecycleButton.setEnabled(!selectedSubjects.isEmpty() && bulkLifecycle.getValue() != null);
	}

	private static String trim(final String value) {
		return value == null ? "" : value.trim();
	}

	private static final class SubjectFormData {

		private String name = "";
		private Lifecycle lifecycle = Lifecycle.ACTIVE;

		private SubjectFormData() {
		}

		private SubjectFormData(final String name, final Lifecycle lifecycle) {
			this.name = trim(name);
			this.lifecycle = lifecycle;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = trim(name);
		}

		public Lifecycle getLifecycle() {
			return lifecycle;
		}

		public void setLifecycle(final Lifecycle lifecycle) {
			this.lifecycle = lifecycle;
		}
	}
}
