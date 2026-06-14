package de.westarps.topteacher.ui.view;

import java.util.List;
import java.util.Locale;

import org.springframework.core.annotation.Order;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.ui.component.AbstractFormEditor;
import de.westarps.topteacher.ui.component.FormBinders;
import de.westarps.topteacher.ui.component.QuickFilterField;

@Order(5)
@UIScope
@SpringComponent
public class SubjectSettingsTab extends VerticalLayout implements SettingsTab {

	private final SubjectRepository subjectRepository;
	private final QuickFilterField search = new QuickFilterField();
	private final Grid<Subject> grid = new Grid<>(Subject.class, false);
	private final TextField name = new TextField("Fach");
	private final ComboBox<Lifecycle> lifecycle = new ComboBox<>("Status");
	private final Binder<SubjectFormData> binder = new Binder<>();
	private final Button newButton = new Button("Neu");
	private final Button saveButton = new Button();
	private final Button archiveButton = new Button("Archivieren");
	private final Span selectionSummary = new Span();

	private ListDataProvider<Subject> dataProvider;
	private Subject selectedSubject;

	public SubjectSettingsTab(final SubjectRepository subjectRepository) {
		this.subjectRepository = subjectRepository;

		configureContent();
		configureGrid();
		configureEditor();
		refreshGrid();
		clearEditor();
	}

	@Override
	public String label() {
		return "Fächer";
	}

	@Override
	public Component content() {
		return this;
	}

	private void configureContent() {
		addClassName("tt-settings-content");
		setPadding(false);
		setSpacing(true);
		setSizeFull();

		search.setPlaceholder("Fach suchen");
		search.addValueChangeListener(event -> applyFilter());

		final HorizontalLayout toolbar = new HorizontalLayout(search, newButton);
		toolbar.addClassName("tt-subject-settings-toolbar");
		toolbar.setAlignItems(Alignment.END);
		toolbar.setPadding(false);
		toolbar.setSpacing(true);
		toolbar.setWidthFull();

		newButton.addClickListener(event -> {
			grid.deselectAll();
			clearEditor();
		});

		add(toolbar, grid, createEditor());
		expand(grid);
	}

	private void configureGrid() {
		grid.addColumn(Subject::id).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
		grid.addColumn(Subject::name).setHeader("Fach").setAutoWidth(true);
		grid.addColumn(subject -> subject.lifecycle().getDisplayName()).setHeader("Status").setAutoWidth(true);
		grid.setSizeFull();
		grid.asSingleSelect().addValueChangeListener(event -> showSubject(event.getValue()));
	}

	private Component createEditor() {
		selectionSummary.addClassName("tt-selection-summary");
		return AbstractFormEditor.singleColumn("tt-subject-settings-editor", List.of(selectionSummary),
				List.of(name, lifecycle), List.of(saveButton, archiveButton));
	}

	private void configureEditor() {
		name.setClearButtonVisible(true);
		name.setValueChangeMode(ValueChangeMode.EAGER);
		name.setRequiredIndicatorVisible(true);

		lifecycle.setItems(Lifecycle.values());
		lifecycle.setItemLabelGenerator(Lifecycle::getDisplayName);
		lifecycle.setRequiredIndicatorVisible(true);

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveSubject());

		archiveButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		archiveButton.addClickListener(event -> archiveSelectedSubject());

		binder.forField(name).asRequired("Fach ist erforderlich.")
				.withConverter(SubjectSettingsTab::trim, value -> value)
				.bind(SubjectFormData::getName, SubjectFormData::setName);
		binder.forField(lifecycle).asRequired("Status ist erforderlich.").bind(SubjectFormData::getLifecycle,
				SubjectFormData::setLifecycle);
	}

	private void showSubject(final Subject subject) {
		selectedSubject = subject;
		if (subject == null) {
			clearEditor();
			return;
		}

		readEditor(new SubjectFormData(subject.name(), subject.lifecycle()));
		updateEditorState();
	}

	private void saveSubject() {
		final SubjectFormData formData = new SubjectFormData();
		if (!binder.writeBeanIfValid(formData)) {
			return;
		}

		final Integer id = selectedSubject == null ? null : selectedSubject.id();
		subjectRepository.save(new Subject(id, formData.getName(), formData.getLifecycle()));
		refreshGrid();
		clearEditor();
		Notification.show("Fach gespeichert.");
	}

	private void archiveSelectedSubject() {
		if (selectedSubject == null) {
			return;
		}

		subjectRepository.archive(selectedSubject.id());
		refreshGrid();
		clearEditor();
		Notification.show("Fach archiviert.");
	}

	private void refreshGrid() {
		dataProvider = DataProvider.ofCollection(subjectRepository.findAll());
		grid.setItems(dataProvider);
		applyFilter();
	}

	private void applyFilter() {
		if (dataProvider == null) {
			return;
		}

		final String filter = normalize(search.getValue());
		dataProvider.setFilter(subject -> normalize(
				String.join(" ", String.valueOf(subject.id()), subject.name(), subject.lifecycle().getDisplayName()))
				.contains(filter));
	}

	private void clearEditor() {
		selectedSubject = null;
		readEditor(new SubjectFormData("", Lifecycle.ACTIVE));
		updateEditorState();
	}

	private void readEditor(final SubjectFormData formData) {
		binder.readBean(formData);
		FormBinders.clearValidation(binder);
	}

	private void updateEditorState() {
		final boolean editMode = selectedSubject != null;
		selectionSummary.setText(editMode ? "Fach bearbeiten" : "Neues Fach");
		saveButton.setText(editMode ? "Speichern" : "Anlegen");
		archiveButton.setVisible(editMode && selectedSubject.lifecycle() == Lifecycle.ACTIVE);
	}

	private static String normalize(final String value) {
		return trim(value).toLowerCase(Locale.ROOT);
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
