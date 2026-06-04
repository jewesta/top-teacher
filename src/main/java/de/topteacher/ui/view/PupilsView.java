package de.topteacher.ui.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import de.topteacher.backend.pupil.Pupil;
import de.topteacher.backend.pupil.PupilLifecycle;
import de.topteacher.backend.pupil.PupilRepository;
import de.topteacher.ui.MainLayout;

@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "pupils", layout = MainLayout.class)
public class PupilsView extends VerticalLayout implements HasDynamicTitle {

	private final PupilRepository pupilRepository;
	private final Grid<Pupil> grid = new Grid<>(Pupil.class, false);
	private final TextField name = new TextField("Name");
	private final TextField surname = new TextField("Surname");
	private final ComboBox<PupilLifecycle> lifecycle = new ComboBox<>("Lifecycle");
	private final Button saveButton = new Button("Save");
	private final Button newButton = new Button("New");
	private final Button archiveButton = new Button("Archive");

	private Pupil selectedPupil;

	public PupilsView(final PupilRepository pupilRepository) {
		this.pupilRepository = pupilRepository;

		setSizeFull();
		setPadding(false);
		setSpacing(false);
		addClassName("tt-pupils-view");

		configureGrid();
		configureForm();
		refreshGrid();
		clearForm();

		add(createPageContent());
	}

	@Override
	public String getPageTitle() {
		return "Pupils";
	}

	private void configureGrid() {
		grid.addColumn(Pupil::id).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
		grid.addColumn(Pupil::name).setHeader("Name").setAutoWidth(true);
		grid.addColumn(Pupil::surname).setHeader("Surname").setAutoWidth(true);
		grid.addColumn(Pupil::lifecycle).setHeader("Lifecycle").setAutoWidth(true);
		grid.setSizeFull();
		grid.asSingleSelect().addValueChangeListener(event -> editPupil(event.getValue()));
	}

	private void configureForm() {
		name.setRequiredIndicatorVisible(true);
		surname.setRequiredIndicatorVisible(true);
		lifecycle.setItems(PupilLifecycle.values());

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> savePupil());

		newButton.addClickListener(event -> {
			grid.deselectAll();
			clearForm();
		});

		archiveButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		archiveButton.addClickListener(event -> archiveSelectedPupil());
	}

	private Component createPageContent() {
		final VerticalLayout listArea = new VerticalLayout(grid);
		listArea.addClassName("tt-pupils-list");
		listArea.setPadding(false);
		listArea.setSpacing(false);
		listArea.setSizeFull();

		final SplitLayout splitLayout = new SplitLayout(listArea, createContextArea());
		splitLayout.addClassName("tt-pupils-split");
		splitLayout.setSizeFull();
		splitLayout.setSplitterPosition(70);
		splitLayout.setPrimaryStyle("min-width", "24rem");
		splitLayout.setSecondaryStyle("min-width", "20rem");
		return splitLayout;
	}

	private Component createContextArea() {
		final TabSheet tabs = new TabSheet();
		tabs.addClassName("tt-context-tabs");
		tabs.setSizeFull();
		tabs.add("Editor", createEditor());

		final VerticalLayout contextArea = new VerticalLayout(tabs);
		contextArea.addClassName("tt-context-area");
		contextArea.setPadding(false);
		contextArea.setSpacing(false);
		contextArea.setSizeFull();
		return contextArea;
	}

	private Component createEditor() {
		final FormLayout form = new FormLayout(name, surname, lifecycle);
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("32rem", 2));

		final var buttons = new HorizontalLayout(saveButton, newButton, archiveButton);
		buttons.setSpacing(true);

		final VerticalLayout editor = new VerticalLayout(form, buttons);
		editor.addClassName("tt-pupil-editor");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
	}

	private void editPupil(final Pupil pupil) {
		selectedPupil = pupil;
		if (pupil == null) {
			clearForm();
			return;
		}

		name.setValue(pupil.name());
		surname.setValue(pupil.surname());
		lifecycle.setValue(pupil.lifecycle());
		updateArchiveButton();
	}

	private void savePupil() {
		final String trimmedName = name.getValue().trim();
		final String trimmedSurname = surname.getValue().trim();
		if (trimmedName.isBlank() || trimmedSurname.isBlank()) {
			Notification.show("Name and surname are required.");
			return;
		}

		final PupilLifecycle selectedLifecycle = lifecycle.getValue() == null ? PupilLifecycle.ACTIVE
				: lifecycle.getValue();
		final Integer id = selectedPupil == null ? null : selectedPupil.id();
		pupilRepository.save(new Pupil(id, trimmedName, trimmedSurname, selectedLifecycle));

		refreshGrid();
		clearForm();
	}

	private void archiveSelectedPupil() {
		if (selectedPupil == null) {
			return;
		}

		pupilRepository.archive(selectedPupil.id());
		refreshGrid();
		clearForm();
	}

	private void refreshGrid() {
		grid.setItems(pupilRepository.findAll());
	}

	private void clearForm() {
		selectedPupil = null;
		name.clear();
		surname.clear();
		lifecycle.setValue(PupilLifecycle.ACTIVE);
		updateArchiveButton();
	}

	private void updateArchiveButton() {
		archiveButton.setEnabled(selectedPupil != null && selectedPupil.lifecycle() == PupilLifecycle.ACTIVE);
	}
}
