package de.westarps.topteacher.ui.view;

import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;

@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "pupils", layout = MainLayout.class)
public class PupilsView extends AbstractMasterDataView<Pupil> {

	private final PupilRepository pupilRepository;
	private final TextField name = new TextField("Vorname");
	private final TextField surname = new TextField("Nachname");
	private final TextField currentSchoolClass = new TextField("Klasse");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Status");
	private final Button saveButton = new Button();
	private final Button archiveButton = new Button("Archivieren");
	private final Span multiSelectionSummary = new Span();
	private final ComboBox<Lifecycle> bulkLifecycle = new ComboBox<>("Status");
	private final Button applyLifecycleButton = new Button("Anwenden");

	private Pupil selectedPupil;
	private List<Pupil> selectedPupils = List.of();
	private Map<Integer, SchoolClass> latestSchoolClassByPupilId = Map.of();

	public PupilsView(final PupilRepository pupilRepository) {
		super("Schüler", "tt-pupils-view", new MultiSelectionGrid<>(Pupil.class, false));
		this.pupilRepository = pupilRepository;

		configureEditors();
		initializeView();
		refreshGrid();
		clearSingleEditor();
	}

	@Override
	protected void configureGrid(final MultiSelectionGrid<Pupil> grid) {
		grid.addColumn(Pupil::id).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
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
		multiSelectionSummary.addClassName("tt-selection-summary");
		return AbstractFormEditor.singleColumn("tt-pupil-bulk-editor", List.of(multiSelectionSummary),
				List.of(bulkLifecycle), List.of(applyLifecycleButton));
	}

	@Override
	protected String getSearchText(final Pupil pupil) {
		final SchoolClass schoolClass = latestSchoolClassByPupilId.get(pupil.id());
		final String schoolClassText = schoolClass == null ? ""
				: schoolClass.getDisplayName() + " " + schoolClass.name();
		return String.join(" ", String.valueOf(pupil.id()), pupil.name(), pupil.surname(), schoolClassText,
				pupil.lifecycle().getDisplayName(), pupil.lifecycle().name());
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
		name.addValueChangeListener(event -> name.setInvalid(false));
		surname.addValueChangeListener(event -> surname.setInvalid(false));

		currentSchoolClass.setReadOnly(true);

		lifecycle.setItems(Lifecycle.values());
		lifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		lifecycle.addValueChangeListener(event -> lifecycle.setInvalid(false));

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> savePupil());

		archiveButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		archiveButton.addClickListener(event -> archiveSelectedPupil());

		bulkLifecycle.setItems(Lifecycle.values());
		bulkLifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		bulkLifecycle.setClearButtonVisible(true);
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

		name.setValue(pupil.name());
		surname.setValue(pupil.surname());
		currentSchoolClass.setValue(latestSchoolClassLabel(pupil));
		lifecycle.setValue(pupil.lifecycle());
		clearSingleEditorValidation();
		updateEditorModeControls();
	}

	private void showMultiSelectEditor(final List<Pupil> pupils) {
		selectedPupil = null;
		selectedPupils = List.copyOf(pupils);
		multiSelectionSummary.setText(selectedPupils.size() + " Schüler ausgewählt");
		setBulkLifecycleValue(commonLifecycle(selectedPupils));
		updateBulkApplyButton();
	}

	private void savePupil() {
		final String trimmedName = name.getValue().trim();
		final String trimmedSurname = surname.getValue().trim();
		if (!validateSingleEditor(trimmedName, trimmedSurname)) {
			return;
		}

		final Lifecycle selectedLifecycle = lifecycle.getValue() == null ? Lifecycle.ACTIVE : lifecycle.getValue();
		final Integer id = selectedPupil == null ? null : selectedPupil.id();
		pupilRepository.save(new Pupil(id, trimmedName, trimmedSurname, selectedLifecycle));

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
		Notification.show("Status für " + selectedPupils.size() + " Schüler aktualisiert.");
		refreshGrid();
	}

	private void refreshGrid() {
		latestSchoolClassByPupilId = pupilRepository.findLatestSchoolClassByPupilId();
		setGridItems(pupilRepository.findAll());
	}

	private void clearSingleEditor() {
		selectedPupil = null;
		name.clear();
		surname.clear();
		currentSchoolClass.clear();
		lifecycle.setValue(Lifecycle.ACTIVE);
		clearSingleEditorValidation();
		updateEditorModeControls();
	}

	private void updateEditorModeControls() {
		final boolean editMode = selectedPupil != null;
		saveButton.setText(editMode ? "Speichern" : "Anlegen");
		currentSchoolClass.setVisible(editMode);
		lifecycle.setVisible(editMode);
		archiveButton.setVisible(editMode && selectedPupil.lifecycle() == Lifecycle.ACTIVE);
	}

	private boolean validateSingleEditor(final String trimmedName, final String trimmedSurname) {
		clearSingleEditorValidation();
		boolean valid = true;
		if (trimmedName.isBlank()) {
			name.setErrorMessage("Vorname ist erforderlich.");
			name.setInvalid(true);
			valid = false;
		}
		if (trimmedSurname.isBlank()) {
			surname.setErrorMessage("Nachname ist erforderlich.");
			surname.setInvalid(true);
			valid = false;
		}
		if (selectedPupil != null && lifecycle.getValue() == null) {
			lifecycle.setErrorMessage("Status ist erforderlich.");
			lifecycle.setInvalid(true);
			valid = false;
		}
		return valid;
	}

	private void clearSingleEditorValidation() {
		name.setInvalid(false);
		surname.setInvalid(false);
		lifecycle.setInvalid(false);
	}

	private String latestSchoolClassLabel(final Pupil pupil) {
		final SchoolClass schoolClass = latestSchoolClassByPupilId.get(pupil.id());
		return schoolClass == null ? "" : schoolClass.getDisplayName();
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
}
