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
import org.springframework.mock.env.MockEnvironment;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.backup.DatabaseBackupScheduler;
import de.westarps.topteacher.backend.backup.DatabaseBackupService;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.ui.component.ClipboardCopyButton;

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
		assertThat(tabSheet.getTabAt(0).getLabel()).isEqualTo("Sicherung");
		assertThat(form.getResponsiveSteps()).hasSize(1);
		assertThat(form.getResponsiveSteps().getFirst().toJson().get("columns").asInt()).isEqualTo(1);
		assertThat(button(backupContent, "Speichern").isEnabled()).isFalse();
		assertThat(button(backupContent, "Jetzt sichern").isEnabled()).isFalse();
	}

	@Test
	void stretchesSettingsTabsToAvailableContentArea() {
		final SettingsView view = new SettingsView(List.of(new StaticSettingsTab("Test", new Div())));

		final TabSheet tabSheet = components(view, TabSheet.class).getFirst();

		assertThat(view.getAlignItems()).isEqualTo(FlexComponent.Alignment.STRETCH);
		assertThat(view.isPadding()).isFalse();
		assertThat(view.isSpacing()).isFalse();
		assertThat(view.getFlexGrow(tabSheet)).isEqualTo(1);
		assertThat(tabSheet.getClassNames()).contains("tt-settings-tabs");
		assertThat(tabSheet.getThemeNames()).contains(TabSheetVariant.LUMO_NO_PADDING.getVariantName());
	}

	@Test
	void reattachesSelectedUiScopedTabContentAfterRouteRecreation() {
		final Div tabContent = new Div();
		final SettingsTab settingsTab = new StaticSettingsTab("Fächer", tabContent);
		final SettingsView firstView = new SettingsView(List.of(settingsTab));
		final TabSheet firstTabSheet = components(firstView, TabSheet.class).getFirst();
		firstTabSheet.getElement().appendChild(tabContent.getElement());

		assertThat(tabContent.getElement().getParent()).isEqualTo(firstTabSheet.getElement());

		final SettingsView secondView = new SettingsView(List.of(settingsTab));
		final TabSheet secondTabSheet = components(secondView, TabSheet.class).getFirst();

		assertThat(secondTabSheet.getComponent(secondTabSheet.getSelectedTab())).isSameAs(tabContent);
		assertThat(tabContent.getElement().getParent()).isNull();
	}

	@Test
	void showsCurrentH2FileAsReadOnlyCopyableField() {
		final Path databaseFile = tempDir.resolve("topteacher");
		final SettingsView view = view("", false, "0 0 2 * * *", databaseFile);
		final Component backupContent = backupContent(view);

		final TextField h2File = textField(backupContent, "Aktuelle H2-Datenbank-Datei");

		assertThat(h2File.isReadOnly()).isTrue();
		assertThat(h2File.getValue()).isEqualTo(databaseFile + ".mv.db");
		final Component suffixComponent = h2File.getSuffixComponent();
		assertThat(suffixComponent).isInstanceOf(ClipboardCopyButton.class);
		assertThat(suffixComponent.getElement().getAttribute("aria-label")).isEqualTo("Pfad kopieren");
		assertThat(backupContent.getChildren().toList().getFirst()).isSameAs(h2File);
		assertThat(backupContent.getChildren().toList().get(1).getElement().getTag()).isEqualTo("hr");
	}

	@Test
	void enablesSaveOnlyWhileSettingsAreDirtyAndValid() {
		final SettingsView view = view("", false, "0 0 2 * * *");
		final Component backupContent = backupContent(view);

		textField(backupContent, "Zielordner").setValue(tempDir.toString());

		assertThat(button(backupContent, "Speichern").isEnabled()).isTrue();
		assertThat(button(backupContent, "Jetzt sichern").isEnabled()).isFalse();
	}

	@Test
	void savingSettingsResetsDirtyStateAndEnablesManualBackup() {
		final AppSettings appSettings = settings("", false, "0 0 2 * * *");
		final DatabaseBackupScheduler backupScheduler = mock(DatabaseBackupScheduler.class);
		final DatabaseBackupService backupService = mock(DatabaseBackupService.class);
		when(backupService.backUpNow()).thenReturn(tempDir.resolve("topteacher-db.zip"));
		final SettingsView view = new SettingsView(List.of(new DatabaseBackupSettingsTab(appSettings, backupService,
				backupScheduler, databaseEnvironment(tempDir.resolve("topteacher")))));
		final Component backupContent = backupContent(view);
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			textField(backupContent, "Zielordner").setValue(tempDir.toString());
			button(backupContent, "Speichern").click();

			assertThat(button(backupContent, "Speichern").isEnabled()).isFalse();
			assertThat(button(backupContent, "Jetzt sichern").isEnabled()).isTrue();

			button(backupContent, "Jetzt sichern").click();
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

	private SettingsView view(final String targetFolder, final boolean scheduleEnabled, final String cron) {
		return view(targetFolder, scheduleEnabled, cron, tempDir.resolve("topteacher"));
	}

	private static SettingsView view(final String targetFolder, final boolean scheduleEnabled, final String cron,
			final Path databaseFile) {
		return new SettingsView(List.of(new DatabaseBackupSettingsTab(settings(targetFolder, scheduleEnabled, cron),
				mock(DatabaseBackupService.class), mock(DatabaseBackupScheduler.class),
				databaseEnvironment(databaseFile))));
	}

	private static AppSettings settings(final String targetFolder, final boolean scheduleEnabled, final String cron) {
		final AppSettings appSettings = mock(AppSettings.class);
		when(appSettings.ttDatabaseBackupTargetFolder()).thenReturn(targetFolder);
		when(appSettings.ttDatabaseBackupScheduleEnabled()).thenReturn(scheduleEnabled);
		when(appSettings.ttDatabaseBackupScheduleCron()).thenReturn(cron);
		return appSettings;
	}

	private static MockEnvironment databaseEnvironment(final Path databaseFile) {
		return new MockEnvironment().withProperty("tt.database.file", databaseFile.toString());
	}

	private static TextField textField(final Component root, final String label) {
		return components(root, TextField.class).stream().filter(field -> label.equals(field.getLabel())).findFirst()
				.orElseThrow();
	}

	private static Component backupContent(final SettingsView view) {
		final TabSheet tabSheet = components(view, TabSheet.class).getFirst();
		return tabSheet.getComponent(tabSheet.getSelectedTab());
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private record StaticSettingsTab(String label, Component content) implements SettingsTab {
	}
}
