package de.westarps.topteacher.ui.view;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.backend.file.FileAccessSettingsService;
import de.westarps.topteacher.backend.repo.SharedFolderMappingRepository;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.SharedFolderMapping;
import de.westarps.topteacher.ui.component.ActionToolbar;
import de.westarps.topteacher.ui.component.Buttons;

@Order(10)
@UIScope
@SpringComponent
public class FileAccessSettingsSection extends VerticalLayout implements SystemSettingsSection {

	private static final String DUPLICATE_CLIENT_PATH = "Dieser Client-Pfad ist mehrfach eingetragen.";
	private static final String LOCAL_DESCRIPTION = "TopTeacher! läuft auf demselben System wie die KI und geht davon aus, dass alle Dateipfade, die es bekommt, lokal erreichbar sind.";
	private static final String REMOTE_DESCRIPTION = "Ordne Pfade, die auf einem Client sichtbar sind, den entsprechenden freigegebenen Ordnern auf diesem TopTeacher-Server zu. So kann TopTeacher zum Beispiel ein im Chat übergebenes Bild vom gemeinsamen Speicher laden.";

	private final FileAccessSettingsService settings;
	private final Select<InstallationLocation> installationLocation = new Select<>("Installationsort");
	private final Span description = new Span();
	private final List<MappingRow> rows = new ArrayList<>();
	private final VerticalLayout mappingList = new VerticalLayout();
	private final Span emptyState = new Span("Noch keine Ordnerzuordnungen eingerichtet.");
	private final Button saveButton = Buttons.save(event -> save());
	private final Button addButton = Buttons.add("Zuordnung hinzufügen", event -> addEmptyRow());
	private List<MappingSnapshot> loadedMappings = List.of();
	private InstallationLocation loadedInstallationLocation;

	public FileAccessSettingsSection(final FileAccessSettingsService settings) {
		this.settings = settings;
		configureContent();
		loadSettings();
	}

	@Override
	public String label() {
		return "Dateizugriff";
	}

	@Override
	public Component content() {
		return this;
	}

	private void configureContent() {
		addClassNames("tt-settings-content", "tt-file-access-settings-content");
		setPadding(false);
		setSpacing(true);
		setWidthFull();

		installationLocation.setItems(InstallationLocation.LOCAL, InstallationLocation.REMOTE);
		installationLocation.setItemLabelGenerator(location -> switch (location) {
		case LOCAL -> "Lokal";
		case REMOTE -> "Entfernt";
		});
		installationLocation.setEmptySelectionAllowed(false);
		installationLocation.setWidth("10rem");
		installationLocation.addValueChangeListener(event -> {
			updateModePresentation();
			updateButtonStates();
		});

		final ActionToolbar actions = new ActionToolbar(saveButton, addButton, installationLocation);
		actions.addComponentAtIndex(0, installationLocation);
		actions.setAlignItems(Alignment.END);
		actions.addClassName("tt-settings-actions");

		description.addClassName("tt-settings-description");

		emptyState.addClassNames("tt-settings-description", "tt-file-access-empty-state");
		mappingList.addClassName("tt-file-access-mapping-list");
		mappingList.setPadding(false);
		mappingList.setSpacing(true);
		mappingList.setWidthFull();

		add(actions, description, mappingList);
	}

	private void loadSettings() {
		rows.clear();
		mappingList.removeAll();
		final List<SharedFolderMapping> mappings = settings.mappings();
		for (final SharedFolderMapping mapping : mappings) {
			addRow(new MappingFormData(mapping.clientPath(), mapping.serverPath()));
		}
		loadedMappings = snapshots();
		loadedInstallationLocation = settings.installationLocation();
		installationLocation.setValue(loadedInstallationLocation);
		updateEmptyState();
		updateButtonStates();
	}

	private void addEmptyRow() {
		if (!isRemote() || rows.size() >= SharedFolderMappingRepository.MAXIMUM_MAPPINGS) {
			return;
		}
		addRow(new MappingFormData("", ""));
		updateEmptyState();
		updateButtonStates();
	}

	private void addRow(final MappingFormData formData) {
		final MappingRow row = new MappingRow(formData);
		rows.add(row);
		mappingList.add(row);
	}

	private void removeRow(final MappingRow row) {
		if (!isRemote()) {
			return;
		}
		rows.remove(row);
		mappingList.remove(row);
		updateEmptyState();
		updateButtonStates();
	}

	private void updateEmptyState() {
		if (rows.isEmpty()) {
			if (emptyState.getParent().isEmpty()) {
				mappingList.add(emptyState);
			}
		} else {
			emptyState.getElement().removeFromParent();
		}
	}

	private boolean isRemote() {
		return installationLocation.getValue() == InstallationLocation.REMOTE;
	}

	private void updateModePresentation() {
		final boolean remote = isRemote();
		description.setText(remote ? REMOTE_DESCRIPTION : LOCAL_DESCRIPTION);
		addButton.setVisible(remote);
		mappingList.setVisible(remote);
		mappingList.setEnabled(remote);
	}

	private void updateButtonStates() {
		final boolean remote = isRemote();
		final boolean valid = !remote || validateRows();
		final boolean changed = installationLocation.getValue() != loadedInstallationLocation
				|| (remote && !snapshots().equals(loadedMappings));
		saveButton.setEnabled(installationLocation.getValue() != null && valid && changed);
		addButton.setEnabled(remote && rows.size() < SharedFolderMappingRepository.MAXIMUM_MAPPINGS);
	}

	private boolean validateRows() {
		boolean valid = true;
		for (final MappingRow row : rows) {
			valid &= row.binder.validate().isOk();
		}

		final Map<String, Integer> clientPathCounts = new HashMap<>();
		for (final MappingRow row : rows) {
			final String clientPath = normalizedPath(row.clientPath.getValue());
			if (!clientPath.isBlank()) {
				clientPathCounts.merge(clientPath, 1, Integer::sum);
			}
		}
		for (final MappingRow row : rows) {
			final String clientPath = normalizedPath(row.clientPath.getValue());
			if (clientPathCounts.getOrDefault(clientPath, 0) > 1) {
				row.clientPath.setInvalid(true);
				row.clientPath.setErrorMessage(DUPLICATE_CLIENT_PATH);
				valid = false;
			}
		}
		return valid;
	}

	private void save() {
		if (!saveButton.isEnabled() || (isRemote() && !validateRows())) {
			updateButtonStates();
			return;
		}

		final List<SharedFolderMapping> mappings = new ArrayList<>();
		if (isRemote()) {
			for (int index = 0; index < rows.size(); index++) {
				final MappingRow row = rows.get(index);
				mappings.add(new SharedFolderMapping(trim(row.clientPath.getValue()), trim(row.serverPath.getValue()), index));
			}
		}
		settings.save(installationLocation.getValue(), mappings);
		loadedInstallationLocation = installationLocation.getValue();
		if (isRemote()) {
			loadedMappings = snapshots();
		}
		updateButtonStates();
		Notification.show("Dateizugriff gespeichert.");
	}

	private List<MappingSnapshot> snapshots() {
		return rows.stream().map(row -> new MappingSnapshot(normalizedPath(row.clientPath.getValue()),
				normalizedPath(row.serverPath.getValue()))).toList();
	}

	private static boolean isAbsolutePath(final String value) {
		try {
			return !trim(value).isBlank() && Path.of(trim(value)).isAbsolute();
		} catch (final InvalidPathException exception) {
			return false;
		}
	}

	private static String serverPathValidationMessage(final String value) {
		if (!isAbsolutePath(value)) {
			return "Bitte gib einen absoluten Pfad an.";
		}
		try {
			final Path path = Path.of(trim(value)).normalize();
			if (!Files.exists(path)) {
				return "Der Ordner ist auf diesem Server nicht erreichbar.";
			}
			if (!Files.isDirectory(path)) {
				return "Der Pfad bezeichnet keinen Ordner.";
			}
			if (!Files.isReadable(path)) {
				return "Der Ordner ist auf diesem Server nicht lesbar.";
			}
			return null;
		} catch (final InvalidPathException | SecurityException exception) {
			return "Der Pfad ist ungültig.";
		}
	}

	private static String trim(final String value) {
		return value == null ? "" : value.trim();
	}

	private static String normalizedPath(final String value) {
		try {
			return Path.of(trim(value)).normalize().toString();
		} catch (final InvalidPathException exception) {
			return trim(value);
		}
	}

	private final class MappingRow extends HorizontalLayout {

		private final Binder<MappingFormData> binder = new Binder<>();
		private final TextField clientPath = pathField("Pfad auf dem Client", "/Volumes/Freigabe");
		private final TextField serverPath = pathField("Pfad auf dem TopTeacher-Server", "/mnt/freigabe");

		private MappingRow(final MappingFormData formData) {
			addClassName("tt-file-access-mapping-row");
			setPadding(false);
			setSpacing(true);
			setAlignItems(Alignment.END);
			setWidthFull();

			binder.forField(clientPath).asRequired("Bitte gib einen Client-Pfad an.")
					.withValidator(FileAccessSettingsSection::isAbsolutePath, "Bitte gib einen absoluten Pfad an.")
					.withValidator(value -> trim(value).length() <= SharedFolderMapping.MAXIMUM_PATH_LENGTH,
							"Der Pfad ist zu lang.")
					.bind(MappingFormData::getClientPath, MappingFormData::setClientPath);
			binder.forField(serverPath).asRequired("Bitte gib einen Server-Pfad an.")
					.withValidator(value -> trim(value).length() <= SharedFolderMapping.MAXIMUM_PATH_LENGTH,
							"Der Pfad ist zu lang.")
					.withValidator((value, context) -> {
						final String message = serverPathValidationMessage(value);
						return message == null ? com.vaadin.flow.data.binder.ValidationResult.ok()
								: com.vaadin.flow.data.binder.ValidationResult.error(message);
					}).bind(MappingFormData::getServerPath, MappingFormData::setServerPath);
			binder.setBean(formData);
			binder.addValueChangeListener(event -> updateButtonStates());

			final Button removeButton = Buttons.icon("Zuordnung entfernen", VaadinIcon.TRASH,
					event -> removeRow(this));
			removeButton.addClassName("tt-file-access-remove");
			add(clientPath, serverPath, removeButton);
			setFlexGrow(1, clientPath, serverPath);
		}
	}

	private static TextField pathField(final String label, final String placeholder) {
		final TextField field = new TextField(label);
		field.setClearButtonVisible(true);
		field.setPlaceholder(placeholder);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		field.setWidthFull();
		return field;
	}

	private static final class MappingFormData {

		private String clientPath;
		private String serverPath;

		private MappingFormData(final String clientPath, final String serverPath) {
			this.clientPath = clientPath;
			this.serverPath = serverPath;
		}

		private String getClientPath() {
			return clientPath;
		}

		private void setClientPath(final String clientPath) {
			this.clientPath = clientPath;
		}

		private String getServerPath() {
			return serverPath;
		}

		private void setServerPath(final String serverPath) {
			this.serverPath = serverPath;
		}
	}

	private record MappingSnapshot(String clientPath, String serverPath) {
	}
}
