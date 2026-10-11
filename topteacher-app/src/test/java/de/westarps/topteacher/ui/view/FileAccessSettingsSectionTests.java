package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.file.FileAccessSettingsService;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.SharedFolderMapping;

class FileAccessSettingsSectionTests {

	@TempDir
	private Path tempDir;

	@Test
	void loadsMappingsAndKeepsSaveDisabledUntilTheyChange() {
		final FileAccessSettingsService settings = remoteSettings();
		when(settings.mappings()).thenReturn(List.of(new SharedFolderMapping("/Volumes/Foo", tempDir.toString(), 0)));

		final FileAccessSettingsSection section = new FileAccessSettingsSection(settings);

		assertThat(section.label()).isEqualTo("Dateizugriff");
		assertThat(textField(section, "Pfad auf dem Client").getValue()).isEqualTo("/Volumes/Foo");
		assertThat(textField(section, "Pfad auf dem TopTeacher-Server").getValue()).isEqualTo(tempDir.toString());
		assertThat(button(section, "Speichern").isEnabled()).isFalse();
	}

	@Test
	void addsValidMappingAndPersistsTheCompleteOrderedList() {
		final FileAccessSettingsService settings = remoteSettings();
		final FileAccessSettingsSection section = new FileAccessSettingsSection(settings);

		button(section, "Zuordnung hinzufügen").click();
		textField(section, "Pfad auf dem Client").setValue("/Volumes/Foo");
		textField(section, "Pfad auf dem TopTeacher-Server").setValue(tempDir.toString());

		assertThat(button(section, "Speichern").isEnabled()).isTrue();
		save(section);

		verify(settings).save(InstallationLocation.REMOTE,
				List.of(new SharedFolderMapping("/Volumes/Foo", tempDir.toString(), 0)));
		assertThat(button(section, "Speichern").isEnabled()).isFalse();
	}

	@Test
	void rejectsDuplicateClientRootsAndUnavailableServerFolders() {
		final FileAccessSettingsSection section = new FileAccessSettingsSection(remoteSettings());

		button(section, "Zuordnung hinzufügen").click();
		button(section, "Zuordnung hinzufügen").click();
		final List<TextField> clients = textFields(section, "Pfad auf dem Client");
		final List<TextField> servers = textFields(section, "Pfad auf dem TopTeacher-Server");
		clients.get(0).setValue("/Volumes/Foo");
		clients.get(1).setValue("/Volumes/Foo/");
		servers.get(0).setValue(tempDir.toString());
		servers.get(1).setValue(tempDir.resolve("missing").toString());

		assertThat(clients).allMatch(TextField::isInvalid);
		assertThat(servers.get(1).getErrorMessage()).isEqualTo("Der Ordner ist auf diesem Server nicht erreichbar.");
		assertThat(button(section, "Speichern").isEnabled()).isFalse();
	}

	@Test
	void localInstallationHidesMappingControlsAndShowsTheLocalExplanation() {
		final FileAccessSettingsService settings = mock(FileAccessSettingsService.class);
		when(settings.installationLocation()).thenReturn(InstallationLocation.LOCAL);
		when(settings.mappings()).thenReturn(List.of());
		final FileAccessSettingsSection section = new FileAccessSettingsSection(settings);

		assertThat(installationLocation(section).getValue()).isEqualTo(InstallationLocation.LOCAL);
		assertThat(button(section, "Zuordnung hinzufügen").isVisible()).isFalse();
		assertThat(button(section, "Zuordnung hinzufügen").isEnabled()).isFalse();
		assertThat(mappingList(section).isVisible()).isFalse();
		assertThat(mappingList(section).isEnabled()).isFalse();
		assertThat(components(section, Span.class)).anyMatch(span -> span.getText().equals(
				"TopTeacher! läuft auf demselben System wie die KI und geht davon aus, dass alle Dateipfade, die es bekommt, lokal erreichbar sind."));
		assertThat(button(section, "Speichern").isEnabled()).isFalse();

		installationLocation(section).setValue(InstallationLocation.REMOTE);

		assertThat(button(section, "Zuordnung hinzufügen").isVisible()).isTrue();
		assertThat(mappingList(section).isVisible()).isTrue();
		assertThat(button(section, "Speichern").isEnabled()).isTrue();
	}

	@Test
	void savesLocalModeDespiteAnInvalidRemoteDraftAndRetainsItWhenSwitchingBack() {
		final FileAccessSettingsService settings = remoteSettings();
		final FileAccessSettingsSection section = new FileAccessSettingsSection(settings);
		button(section, "Zuordnung hinzufügen").click();
		textField(section, "Pfad auf dem Client").setValue("/Volumes/Draft");
		assertThat(button(section, "Speichern").isEnabled()).isFalse();

		installationLocation(section).setValue(InstallationLocation.LOCAL);
		assertThat(button(section, "Speichern").isEnabled()).isTrue();
		save(section);

		verify(settings).save(InstallationLocation.LOCAL, List.of());
		assertThat(button(section, "Speichern").isEnabled()).isFalse();
		installationLocation(section).setValue(InstallationLocation.REMOTE);
		assertThat(textField(section, "Pfad auf dem Client").getValue()).isEqualTo("/Volumes/Draft");
		assertThat(button(section, "Speichern").isEnabled()).isFalse();
		textField(section, "Pfad auf dem TopTeacher-Server").setValue(tempDir.toString());
		assertThat(button(section, "Speichern").isEnabled()).isTrue();
	}

	private static FileAccessSettingsService remoteSettings() {
		final FileAccessSettingsService settings = mock(FileAccessSettingsService.class);
		when(settings.installationLocation()).thenReturn(InstallationLocation.REMOTE);
		when(settings.mappings()).thenReturn(List.of());
		return settings;
	}

	private static void save(final Component section) {
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			button(section, "Speichern").click();
		} finally {
			UI.setCurrent(null);
		}
	}

	@SuppressWarnings("unchecked")
	private static Select<InstallationLocation> installationLocation(final Component root) {
		return (Select<InstallationLocation>) components(root, Select.class).getFirst();
	}

	private static VerticalLayout mappingList(final Component root) {
		return components(root, VerticalLayout.class).stream()
				.filter(layout -> layout.hasClassName("tt-file-access-mapping-list")).findFirst().orElseThrow();
	}

	private static TextField textField(final Component root, final String label) {
		return textFields(root, label).getFirst();
	}

	private static List<TextField> textFields(final Component root, final String label) {
		return components(root, TextField.class).stream().filter(field -> label.equals(field.getLabel())).toList();
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
