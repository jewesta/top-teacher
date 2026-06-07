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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.backup.DatabaseBackupScheduler;
import de.westarps.topteacher.backend.backup.DatabaseBackupService;
import de.westarps.topteacher.backend.settings.AppSettings;

class SettingsViewTests {

	@TempDir
	private Path tempDir;

	@Test
	void wrapsBackupSettingsInATabAndUsesOneColumnForm() {
		final SettingsView view = view("", false, "0 0 2 * * *");

		final TabSheet tabSheet = components(view, TabSheet.class).getFirst();
		final Component backupContent = tabSheet.getComponent(tabSheet.getSelectedTab());
		final FormLayout form = components(backupContent, FormLayout.class).getFirst();

		assertThat(view.getPageTitle()).isEqualTo("Einstellungen");
		assertThat(tabSheet.getTabCount()).isEqualTo(1);
		assertThat(tabSheet.getTabAt(0).getLabel()).isEqualTo("Backup");
		assertThat(form.getResponsiveSteps()).hasSize(1);
		assertThat(form.getResponsiveSteps().getFirst().toJson().get("columns").asInt()).isEqualTo(1);
		assertThat(button(backupContent, "Speichern").isEnabled()).isFalse();
		assertThat(button(backupContent, "Backup jetzt").isEnabled()).isFalse();
	}

	@Test
	void enablesSaveOnlyWhileSettingsAreDirtyAndValid() {
		final SettingsView view = view("", false, "0 0 2 * * *");
		final Component backupContent = backupContent(view);

		textField(backupContent, "Zielordner").setValue(tempDir.toString());

		assertThat(button(backupContent, "Speichern").isEnabled()).isTrue();
		assertThat(button(backupContent, "Backup jetzt").isEnabled()).isFalse();
	}

	@Test
	void savingSettingsResetsDirtyStateAndEnablesManualBackup() {
		final AppSettings appSettings = settings("", false, "0 0 2 * * *");
		final DatabaseBackupScheduler backupScheduler = mock(DatabaseBackupScheduler.class);
		final DatabaseBackupService backupService = mock(DatabaseBackupService.class);
		when(backupService.backUpNow()).thenReturn(tempDir.resolve("topteacher-db.zip"));
		final SettingsView view = new SettingsView(appSettings, backupService, backupScheduler);
		final Component backupContent = backupContent(view);
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			textField(backupContent, "Zielordner").setValue(tempDir.toString());
			button(backupContent, "Speichern").click();

			assertThat(button(backupContent, "Speichern").isEnabled()).isFalse();
			assertThat(button(backupContent, "Backup jetzt").isEnabled()).isTrue();

			button(backupContent, "Backup jetzt").click();
		} finally {
			UI.setCurrent(null);
		}

		verify(appSettings).saveTtDatabaseBackupTargetFolder(tempDir.toString());
		verify(backupScheduler).reload();
		verify(backupService).backUpNow();
	}

	@Test
	void liveValidatesThatTargetFolderExists() {
		final SettingsView view = view("", false, "0 0 2 * * *");
		final Component backupContent = backupContent(view);
		final TextField targetFolder = textField(backupContent, "Zielordner");

		targetFolder.setValue(tempDir.resolve("missing").toString());

		assertThat(targetFolder.isInvalid()).isTrue();
		assertThat(targetFolder.getErrorMessage()).isEqualTo("Zielordner existiert nicht.");
		assertThat(button(backupContent, "Speichern").isEnabled()).isFalse();
	}

	@Test
	void requiresTargetFolderWhenScheduledBackupIsEnabled() {
		final SettingsView view = view("", false, "0 0 2 * * *");
		final Component backupContent = backupContent(view);

		components(backupContent, Checkbox.class).getFirst().setValue(true);

		assertThat(textField(backupContent, "Zielordner").isInvalid()).isTrue();
		assertThat(button(backupContent, "Speichern").isEnabled()).isFalse();
	}

	private static SettingsView view(final String targetFolder, final boolean scheduleEnabled, final String cron) {
		return new SettingsView(settings(targetFolder, scheduleEnabled, cron), mock(DatabaseBackupService.class),
				mock(DatabaseBackupScheduler.class));
	}

	private static AppSettings settings(final String targetFolder, final boolean scheduleEnabled, final String cron) {
		final AppSettings appSettings = mock(AppSettings.class);
		when(appSettings.ttDatabaseBackupTargetFolder()).thenReturn(targetFolder);
		when(appSettings.ttDatabaseBackupScheduleEnabled()).thenReturn(scheduleEnabled);
		when(appSettings.ttDatabaseBackupScheduleCron()).thenReturn(cron);
		return appSettings;
	}

	private static TextField textField(final Component root, final String label) {
		return components(root, TextField.class).stream()
				.filter(field -> label.equals(field.getLabel()))
				.findFirst()
				.orElseThrow();
	}

	private static Component backupContent(final SettingsView view) {
		final TabSheet tabSheet = components(view, TabSheet.class).getFirst();
		return tabSheet.getComponent(tabSheet.getSelectedTab());
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream()
				.filter(button -> text.equals(button.getText()))
				.findFirst()
				.orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
