package de.westarps.topteacher.ui.view;

import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.FormBinders;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "pupils", layout = MainLayout.class)
public class PupilsView extends SplitListDetailView<Pupil> {

	private final PupilRepository pupilRepository;
	private final TextField name = new TextField("Vorname");
	private final TextField surname = new TextField("Nachname");
	private final TextField currentSchoolClass = new TextField("Klasse");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Status");
	private final Binder<PupilFormData> pupilBinder = new Binder<>();
	private final Button newButton = createNewButton();
	private final Button saveButton = Buttons.createOrSave();
	private final Button archiveButton = Buttons.archive();
	private final ComboBox<Lifecycle> bulkLifecycle = new ComboBox<>("Status");
	private final Button applyLifecycleButton = new Button("Anwenden");
	private FormBinders.DirtySaveButton dirtySaveButton;

	private Pupil selectedPupil;
	private List<Pupil> selectedPupils = List.of();
	private Map<Integer, SchoolClass> latestSchoolClassByPupilId = Map.of();

	public PupilsView(final PupilRepository pupilRepository) {
		super("Schüler:innen", "tt-pupils-view", new MultiSelectionGrid<>(Pupil.class, false));
		this.pupilRepository = pupilRepository;

		configureEditors();
		initializeView();
		refreshGrid();
		clearSingleEditor();
	}

	@Override
	protected void configureGrid(final MultiSelectionGrid<Pupil> grid) {
		grid.addColumn(Pupil::name).setHeader("Vorname").setAutoWidth(true);
		grid.addColumn(Pupil::surname).setHeader("Nachname").setAutoWidth(true);
		grid.addColumn(this::latestSchoolClassLabel).setHeader("Klasse").setAutoWidth(true);
		grid.addColumn(pupil -> pupil.lifecycle().getDisplayName()).setHeader("Status").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		return AbstractFormEditor.responsive("tt-pupil-editor", List.of(name, surname, currentSchoolClass, lifecycle),
				List.of(saveButton, archiveButton));
	}

	@Override
	protected Component createMultiSelectEditor() {
		return AbstractFormEditor.singleColumn("tt-pupil-bulk-editor", List.of(), List.of(bulkLifecycle),
				List.of(applyLifecycleButton));
	}

	@Override
	protected List<Component> createListToolbarComponents() {
		return List.of(newButton);
	}

	@Override
	protected String getEditorTabLabel() {
		return "Schüler:in";
	}

	@Override
	protected String getCreateEditorStatus() {
		return "Neue:r Schüler:in";
	}

	@Override
	protected String getSingleEditorStatus(final Pupil selectedItem) {
		return "Schüler:in bearbeiten";
	}

	@Override
	protected String getMultiEditorStatus(final List<Pupil> selectedItems) {
		return selectedItems.size() + " Schüler:innen ausgewählt";
	}

	@Override
	protected String getSearchText(final Pupil pupil) {
		final SchoolClass schoolClass = latestSchoolClassByPupilId.get(pupil.id());
		final String schoolClassText = schoolClass == null ? ""
				: schoolClass.getDisplayName() + " " + schoolClass.name();
		return String.join(" ", pupil.name(), pupil.surname(), schoolClassText, pupil.lifecycle().getDisplayName(),
				pupil.lifecycle().name());
	}

	@Override
	protected void onEditorModeChanged(final EditorMode editorMode, final List<Pupil> selectedItems) {
		if (editorMode == EditorMode.MULTI_SELECT) {
			showMultiSelectEditor(selectedItems);
			return;
		}

		final Pupil pupil = selectedItems.isEmpty() ? null : selectedItems.get(0);
		showSingleSelectEditor(pupil);
	}

	private void configureEditors() {
		name.setRequiredIndicatorVisible(true);
		surname.setRequiredIndicatorVisible(true);

		currentSchoolClass.setReadOnly(true);

		lifecycle.setItems(Lifecycle.values());
		lifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);

		bindSingleEditor();
		dirtySaveButton = FormBinders.bindDirtySaveButton(pupilBinder, saveButton);

		newButton.addClickListener(event -> {
			clearSelection();
			clearSingleEditor();
		});

		saveButton.addClickListener(event -> savePupil());

		archiveButton.addClickListener(event -> TopTeacherDialogs.openArchiveConfirmation("Schüler:in archivieren?",
				"Diese Schüler:in wird archiviert. Das bedeutet, dass diese Schüler:in standardmäßig nicht mehr angezeigt wird und nicht neu zugeordnet werden kann.",
				"Bestehende Kurse, Klausuren und Ergebnisse bleiben erhalten. Du kannst die Archivierung wieder rückgängig machen.",
				this::archiveSelectedPupil));

		bulkLifecycle.setItems(Lifecycle.values());
		bulkLifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		bulkLifecycle.setRequiredIndicatorVisible(true);
		bulkLifecycle.addValueChangeListener(event -> updateBulkApplyButton());

		applyLifecycleButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		applyLifecycleButton.addClickListener(event -> applyLifecycleToSelectedPupils());
		updateBulkApplyButton();
	}

	private void showSingleSelectEditor(final Pupil pupil) {
		selectedPupils = List.of();
		selectedPupil = pupil;
		if (pupil == null) {
			clearSingleEditor();
			return;
		}

		currentSchoolClass.setValue(latestSchoolClassLabel(pupil));
		readSingleEditor(new PupilFormData(pupil.name(), pupil.surname(), pupil.lifecycle()));
		updateEditorModeControls();
	}

	private void showMultiSelectEditor(final List<Pupil> pupils) {
		selectedPupil = null;
		selectedPupils = List.copyOf(pupils);
		setBulkLifecycleValue(commonLifecycle(selectedPupils));
		updateBulkApplyButton();
	}

	private void savePupil() {
		final PupilFormData formData = new PupilFormData();
		if (!pupilBinder.writeBeanIfValid(formData)) {
			return;
		}

		final Integer id = selectedPupil == null ? null : selectedPupil.id();
		pupilRepository.save(new Pupil(id, formData.getName(), formData.getSurname(), formData.getLifecycle()));

		refreshGrid();
		clearSelection();
		clearSingleEditor();
	}

	private void archiveSelectedPupil() {
		if (selectedPupil == null) {
			return;
		}

		pupilRepository.archive(selectedPupil.id());
		refreshGrid();
		clearSelection();
		clearSingleEditor();
	}

	private void applyLifecycleToSelectedPupils() {
		final Lifecycle selectedLifecycle = bulkLifecycle.getValue();
		if (selectedPupils.isEmpty() || selectedLifecycle == null) {
			return;
		}

		selectedPupils.forEach(
				pupil -> pupilRepository.save(new Pupil(pupil.id(), pupil.name(), pupil.surname(), selectedLifecycle)));
		Notification.show("Status für " + selectedPupils.size() + " Schüler:innen aktualisiert.");
		refreshGrid();
	}

	private void refreshGrid() {
		latestSchoolClassByPupilId = pupilRepository.findLatestSchoolClassByPupilId();
		setGridItems(pupilRepository.findAll());
	}

	private void clearSingleEditor() {
		selectedPupil = null;
		currentSchoolClass.clear();
		readSingleEditor(new PupilFormData("", "", Lifecycle.ACTIVE));
		updateEditorModeControls();
	}

	private void updateEditorModeControls() {
		final boolean editMode = selectedPupil != null;
		Buttons.setCreateOrSaveMode(saveButton, editMode);
		currentSchoolClass.setVisible(editMode);
		lifecycle.setVisible(editMode);
		archiveButton.setVisible(editMode && selectedPupil.lifecycle() == Lifecycle.ACTIVE);
	}

	private String latestSchoolClassLabel(final Pupil pupil) {
		final SchoolClass schoolClass = latestSchoolClassByPupilId.get(pupil.id());
		return schoolClass == null ? "" : schoolClass.getDisplayName();
	}

	private void bindSingleEditor() {
		pupilBinder.forField(name).withConverter(PupilsView::trim, value -> value)
				.withValidator(value -> !value.isBlank(), "Vorname ist erforderlich.")
				.bind(PupilFormData::getName, PupilFormData::setName);
		pupilBinder.forField(surname).withConverter(PupilsView::trim, value -> value)
				.withValidator(value -> !value.isBlank(), "Nachname ist erforderlich.")
				.bind(PupilFormData::getSurname, PupilFormData::setSurname);
		pupilBinder.forField(lifecycle).asRequired("Status ist erforderlich.").bind(PupilFormData::getLifecycle,
				PupilFormData::setLifecycle);
	}

	private void readSingleEditor(final PupilFormData formData) {
		pupilBinder.readBean(formData);
		FormBinders.clearValidation(pupilBinder);
		dirtySaveButton.reset();
	}

	private static String trim(final String value) {
		return value == null ? "" : value.trim();
	}

	private Lifecycle commonLifecycle(final List<Pupil> pupils) {
		if (pupils.isEmpty()) {
			return null;
		}

		final Lifecycle firstLifecycle = pupils.get(0).lifecycle();
		return pupils.stream().allMatch(pupil -> pupil.lifecycle() == firstLifecycle) ? firstLifecycle : null;
	}

	private void setBulkLifecycleValue(final Lifecycle value) {
		if (value == null) {
			bulkLifecycle.clear();
			return;
		}
		bulkLifecycle.setValue(value);
	}

	private void updateBulkApplyButton() {
		applyLifecycleButton.setEnabled(!selectedPupils.isEmpty() && bulkLifecycle.getValue() != null);
	}

	private static final class PupilFormData {

		private String name = "";
		private String surname = "";
		private Lifecycle lifecycle = Lifecycle.ACTIVE;

		public PupilFormData() {
		}

		private PupilFormData(final String name, final String surname, final Lifecycle lifecycle) {
			this.name = name;
			this.surname = surname;
			this.lifecycle = lifecycle;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		public String getSurname() {
			return surname;
		}

		public void setSurname(final String surname) {
			this.surname = surname;
		}

		public Lifecycle getLifecycle() {
			return lifecycle;
		}

		public void setLifecycle(final Lifecycle lifecycle) {
			this.lifecycle = lifecycle;
		}
	}
}
