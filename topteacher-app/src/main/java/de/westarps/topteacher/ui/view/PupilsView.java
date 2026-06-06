package de.westarps.topteacher.ui.view;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.ui.MainLayout;
import de.westarps.topteacher.ui.component.MultiSelectionGrid;

@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "pupils", layout = MainLayout.class)
public class PupilsView extends AbstractMasterDataView<Pupil> {

	private final PupilRepository pupilRepository;
	private final TextField name = new TextField("Vorname");
	private final TextField surname = new TextField("Nachname");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Status");
	private final Button saveButton = new Button("Speichern");
	private final Button newButton = new Button("Neu");
	private final Button archiveButton = new Button("Archivieren");
	private final Span multiSelectionSummary = new Span();
	private final ComboBox<Lifecycle> bulkLifecycle = new ComboBox<>("Status");
	private final Button applyLifecycleButton = new Button("Anwenden");

	private Pupil selectedPupil;
	private List<Pupil> selectedPupils = List.of();

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
		grid.addColumn(pupil -> pupil.lifecycle().getDisplayName()).setHeader("Status").setAutoWidth(true);
	}

	@Override
	protected Component createSingleSelectEditor() {
		final FormLayout form = new FormLayout(name, surname, lifecycle);
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("32rem", 2));

		final HorizontalLayout buttons = new HorizontalLayout(saveButton, newButton, archiveButton);
		buttons.setSpacing(true);

		final VerticalLayout editor = new VerticalLayout(form, buttons);
		editor.addClassNames("tt-editor", "tt-pupil-editor");
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
		editor.addClassNames("tt-editor", "tt-pupil-bulk-editor");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
	}

	@Override
	protected String getSearchText(final Pupil pupil) {
		return String.join(" ", String.valueOf(pupil.id()), pupil.name(), pupil.surname(),
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
		lifecycle.setItems(Lifecycle.values());
		lifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> savePupil());

		newButton.addClickListener(event -> {
			clearSelection();
			clearSingleEditor();
		});

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
		lifecycle.setValue(pupil.lifecycle());
		updateArchiveButton();
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
		if (trimmedName.isBlank() || trimmedSurname.isBlank()) {
			Notification.show("Vorname und Nachname sind erforderlich.");
			return;
		}

		final Lifecycle selectedLifecycle = lifecycle.getValue() == null ? Lifecycle.ACTIVE : lifecycle.getValue();
		final Integer id = selectedPupil == null ? null : selectedPupil.id();
		pupilRepository.save(new Pupil(id, trimmedName, trimmedSurname, selectedLifecycle));

		refreshGrid();
		clearSingleEditor();
	}

	private void archiveSelectedPupil() {
		if (selectedPupil == null) {
			return;
		}

		pupilRepository.archive(selectedPupil.id());
		refreshGrid();
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
		setGridItems(pupilRepository.findAll());
	}

	private void clearSingleEditor() {
		selectedPupil = null;
		name.clear();
		surname.clear();
		lifecycle.setValue(Lifecycle.ACTIVE);
		updateArchiveButton();
	}

	private void updateArchiveButton() {
		archiveButton.setEnabled(selectedPupil != null && selectedPupil.lifecycle() == Lifecycle.ACTIVE);
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
