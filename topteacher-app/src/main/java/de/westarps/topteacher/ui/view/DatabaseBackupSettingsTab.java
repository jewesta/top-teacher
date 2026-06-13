package de.westarps.topteacher.ui.view;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.backend.backup.DatabaseBackupScheduler;
import de.westarps.topteacher.backend.backup.DatabaseBackupService;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.ui.component.ClipboardCopyButton;
import de.westarps.topteacher.ui.component.FormBinders;
import it.burning.cron.CronExpressionDescriptor;
import it.burning.cron.CronExpressionParser.Options;

@Order(10)
@UIScope
@SpringComponent
public class DatabaseBackupSettingsTab extends VerticalLayout implements SettingsTab {

	private static final String DATABASE_FILE_PROPERTY = "tt.database.file";
	private static final String DATASOURCE_URL_PROPERTY = "spring.datasource.url";
	private static final String H2_FILE_URL_PREFIX = "jdbc:h2:file:";
	private static final String H2_FILE_SUFFIX = ".mv.db";

	private final AppSettings appSettings;
	private final DatabaseBackupService backupService;
	private final DatabaseBackupScheduler backupScheduler;

	private final Binder<BackupSettingsFormData> backupBinder = new Binder<>();
	private final TextField currentDatabaseFile = new TextField("Aktuelle H2-Datei");
	private final Checkbox backupEnabled = new Checkbox("Automatische Sicherung aktiv");
	private final TextField backupTargetFolder = new TextField("Zielordner");
	private final TextField backupCron = new TextField("Zeitplan");
	private final Span backupDescription = new Span(
			"TopTeacher kann die H2-Datenbank regelmäßig als ZIP-Datei sichern. Der Zielordner muss auf diesem Rechner erreichbar und beschreibbar sein.");
	private final Span backupFileNameDescription = new Span(
			"Dateiname: topteacher-db-JJJJMMTT-HHMMSS.zip; bei Namensgleichheit wird -1, -2, ... ergänzt.");
	private final Span cronDescription = new Span();
	private final Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
	private final Button backupNowButton = new Button("Backup jetzt", new Icon("vaadin", "lifebuoy"));
	private final String currentDatabaseFilePath;
	private BackupSettingsFormData loadedBackupSettings;

	public DatabaseBackupSettingsTab(final AppSettings appSettings, final DatabaseBackupService backupService,
			final DatabaseBackupScheduler backupScheduler, final Environment environment) {
		this.appSettings = appSettings;
		this.backupService = backupService;
		this.backupScheduler = backupScheduler;
		currentDatabaseFilePath = currentDatabaseFile(environment);

		configureContent();
		bindBackupSettings();
		readSettings();
		updateButtonStates();
	}

	@Override
	public String label() {
		return "Backup";
	}

	@Override
	public Component content() {
		return this;
	}

	private void configureContent() {
		currentDatabaseFile.setReadOnly(true);
		currentDatabaseFile.setWidthFull();
		currentDatabaseFile.setValue(currentDatabaseFilePath);
		currentDatabaseFile.setSuffixComponent(createCopyCurrentDatabaseFileButton());

		backupTargetFolder.setClearButtonVisible(true);
		backupTargetFolder.setPlaceholder("/Volumes/Backups/TopTeacher");
		backupTargetFolder.setValueChangeMode(ValueChangeMode.EAGER);
		backupTargetFolder.setWidthFull();
		backupTargetFolder.addValueChangeListener(event -> updateButtonStates());

		backupCron.setClearButtonVisible(true);
		backupCron.addClassName("tt-settings-cron-field");
		backupCron.setHelperText("Spring-Cron mit 6 Feldern: Sekunde Minute Stunde Tag Monat Wochentag, z. B. 0 0 2 * * *.");
		backupCron.setValueChangeMode(ValueChangeMode.EAGER);
		backupCron.addValueChangeListener(event -> updateButtonStates());

		backupEnabled.addValueChangeListener(event -> updateButtonStates());

		backupDescription.addClassName("tt-settings-description");
		backupFileNameDescription.addClassName("tt-settings-description");
		cronDescription.addClassName("tt-settings-cron-description");

		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveSettings(true));

		backupNowButton.addClickListener(event -> backUpNow());

		final FormLayout form = new FormLayout();
		form.addClassName("tt-settings-form");
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
		form.add(backupEnabled, backupTargetFolder, backupCron, cronDescription);

		final HorizontalLayout actions = new HorizontalLayout(saveButton, backupNowButton);
		actions.addClassName("tt-settings-actions");
		actions.setSpacing(true);

		addClassName("tt-settings-content");
		setPadding(false);
		setWidthFull();
		add(currentDatabaseFile, new Hr(), backupDescription, backupFileNameDescription, form, actions);
	}

	private Component createCopyCurrentDatabaseFileButton() {
		return new ClipboardCopyButton(currentDatabaseFilePath, "Pfad kopieren", "Pfad kopiert.",
				"Pfad konnte nicht kopiert werden.");
	}

	private void readSettings() {
		loadedBackupSettings = new BackupSettingsFormData(appSettings.ttDatabaseBackupScheduleEnabled(),
				appSettings.ttDatabaseBackupTargetFolder(), appSettings.ttDatabaseBackupScheduleCron());
		backupBinder.readBean(loadedBackupSettings);
		FormBinders.clearValidation(backupBinder);
	}

	private void saveSettings(final boolean showNotification) {
		updateButtonStates();

		final BackupSettingsFormData formData = new BackupSettingsFormData();
		if (!backupBinder.writeBeanIfValid(formData)) {
			return;
		}

		appSettings.saveTtDatabaseBackupScheduleEnabled(formData.isScheduleEnabled());
		appSettings.saveTtDatabaseBackupTargetFolder(formData.getTargetFolder());
		appSettings.saveTtDatabaseBackupScheduleCron(formData.getCron());
		backupScheduler.reload();
		loadedBackupSettings = formData;
		backupBinder.readBean(formData);
		FormBinders.clearValidation(backupBinder);
		updateButtonStates();

		if (showNotification) {
			Notification.show("Einstellungen gespeichert.");
		}
	}

	private void backUpNow() {
		updateButtonStates();
		if (!canBackUpNow() || hasDirtyChanges()) {
			return;
		}

		try {
			final Path backupFile = backupService.backUpNow();
			Notification.show("Backup erstellt: " + backupFile.getFileName());
		} catch (final IllegalStateException exception) {
			Notification.show(exception.getMessage());
		}
	}

	private void updateButtonStates() {
		updateTargetFolderValidation();
		updateCronDescription();

		final boolean dirty = hasDirtyChanges();
		saveButton.setEnabled(dirty && canSave());
		backupNowButton.setEnabled(!dirty && canBackUpNow());
	}

	private void updateTargetFolderValidation() {
		final boolean targetFolderRequired = Boolean.TRUE.equals(backupEnabled.getValue());
		final String errorMessage = targetFolderValidationMessage(backupTargetFolder.getValue(), targetFolderRequired);
		backupTargetFolder.setInvalid(errorMessage != null);
		backupTargetFolder.setErrorMessage(errorMessage);
	}

	private void updateCronDescription() {
		final String cron = backupCron.getValue();
		if (cron == null || cron.isBlank()) {
			cronDescription.setText("Bitte einen Cron-Ausdruck angeben.");
			backupCron.setInvalid(true);
			backupCron.setErrorMessage("Zeitplan ist erforderlich.");
			return;
		}

		try {
			CronExpression.parse(cron);
			cronDescription.setText("Aktueller Zeitplan: " + describeCron(cron));
			backupCron.setInvalid(false);
		} catch (final IllegalArgumentException exception) {
			cronDescription.setText("Ungültiger Cron-Ausdruck.");
			backupCron.setInvalid(true);
			backupCron.setErrorMessage("Ungültiger Cron-Ausdruck.");
		}
	}

	private boolean canSave() {
		return isValidCronExpression(backupCron.getValue())
				&& targetFolderValidationMessage(backupTargetFolder.getValue(),
						Boolean.TRUE.equals(backupEnabled.getValue())) == null;
	}

	private boolean canBackUpNow() {
		return isValidCronExpression(backupCron.getValue())
				&& targetFolderValidationMessage(backupTargetFolder.getValue(), true) == null;
	}

	private boolean hasDirtyChanges() {
		return loadedBackupSettings != null
				&& (loadedBackupSettings.isScheduleEnabled() != Boolean.TRUE.equals(backupEnabled.getValue())
						|| !loadedBackupSettings.getTargetFolder().equals(trim(backupTargetFolder.getValue()))
						|| !loadedBackupSettings.getCron().equals(trim(backupCron.getValue())));
	}

	private void bindBackupSettings() {
		backupBinder.forField(backupEnabled)
				.bind(BackupSettingsFormData::isScheduleEnabled, BackupSettingsFormData::setScheduleEnabled);
		backupBinder.forField(backupTargetFolder).withConverter(DatabaseBackupSettingsTab::trim, value -> value)
				.withValidator((value, context) -> {
					final String errorMessage =
							targetFolderValidationMessage(value, Boolean.TRUE.equals(backupEnabled.getValue()));
					return errorMessage == null ? ValidationResult.ok() : ValidationResult.error(errorMessage);
				})
				.bind(BackupSettingsFormData::getTargetFolder, BackupSettingsFormData::setTargetFolder);
		backupBinder.forField(backupCron).withConverter(DatabaseBackupSettingsTab::trim, value -> value)
				.withValidator(value -> !value.isBlank(), "Zeitplan ist erforderlich.")
				.withValidator(DatabaseBackupSettingsTab::isValidCronExpression, "Ungültiger Cron-Ausdruck.")
				.bind(BackupSettingsFormData::getCron, BackupSettingsFormData::setCron);
	}

	private static boolean isValidCronExpression(final String cron) {
		try {
			CronExpression.parse(trim(cron));
			return true;
		} catch (final IllegalArgumentException exception) {
			return false;
		}
	}

	private static String targetFolderValidationMessage(final String value, final boolean required) {
		final String targetFolder = trim(value);
		if (targetFolder.isBlank()) {
			return required ? "Bitte einen Zielordner angeben." : null;
		}

		try {
			final Path folder = Path.of(targetFolder).toAbsolutePath().normalize();
			if (!Files.exists(folder)) {
				return "Zielordner existiert nicht.";
			}
			if (!Files.isDirectory(folder)) {
				return "Pfad ist kein Ordner.";
			}
			if (!Files.isWritable(folder)) {
				return "Zielordner ist nicht beschreibbar.";
			}
			return null;
		} catch (final InvalidPathException | SecurityException exception) {
			return "Pfad ist ungültig.";
		}
	}

	private static String describeCron(final String cron) {
		try {
			final Options options = new Options();
			options.setLocale(Locale.GERMANY);
			options.setVerbose(true);
			return CronExpressionDescriptor.getDescription(cron, options);
		} catch (final RuntimeException exception) {
			return "Gültiger Cron-Ausdruck.";
		}
	}

	private static String trim(final String value) {
		return value == null ? "" : value.trim();
	}

	private static String currentDatabaseFile(final Environment environment) {
		return configuredDatabaseFile(environment)
				.map(databaseFile -> Path.of(databaseFile + H2_FILE_SUFFIX).toAbsolutePath().normalize().toString())
				.orElse("");
	}

	private static Optional<String> configuredDatabaseFile(final Environment environment) {
		final String databaseFile = environment.getProperty(DATABASE_FILE_PROPERTY);
		if (StringUtils.hasText(databaseFile)) {
			return Optional.of(databaseFile);
		}
		return filePathFromDatasourceUrl(environment.getProperty(DATASOURCE_URL_PROPERTY));
	}

	private static Optional<String> filePathFromDatasourceUrl(final String datasourceUrl) {
		if (!StringUtils.hasText(datasourceUrl) || !datasourceUrl.startsWith(H2_FILE_URL_PREFIX)) {
			return Optional.empty();
		}

		final String pathAndOptions = datasourceUrl.substring(H2_FILE_URL_PREFIX.length());
		final int optionStart = pathAndOptions.indexOf(';');
		final String path = optionStart >= 0 ? pathAndOptions.substring(0, optionStart) : pathAndOptions;
		return StringUtils.hasText(path) ? Optional.of(path) : Optional.empty();
	}

	private static final class BackupSettingsFormData {

		private boolean scheduleEnabled;
		private String targetFolder = "";
		private String cron = "";

		private BackupSettingsFormData() {
		}

		private BackupSettingsFormData(final boolean scheduleEnabled, final String targetFolder, final String cron) {
			this.scheduleEnabled = scheduleEnabled;
			this.targetFolder = trim(targetFolder);
			this.cron = trim(cron);
		}

		private boolean isScheduleEnabled() {
			return scheduleEnabled;
		}

		private void setScheduleEnabled(final boolean scheduleEnabled) {
			this.scheduleEnabled = scheduleEnabled;
		}

		private String getTargetFolder() {
			return targetFolder;
		}

		private void setTargetFolder(final String targetFolder) {
			this.targetFolder = trim(targetFolder);
		}

		private String getCron() {
			return cron;
		}

		private void setCron(final String cron) {
			this.cron = trim(cron);
		}
	}
}
