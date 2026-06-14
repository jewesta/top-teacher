package de.westarps.topteacher.backend.settings;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.SettingsRepository;

@Component
public class AppSettings {

	public static final String TT_LOE_EXPORT_SHOW_WATERMARK_KEY = "tt.loe.export.show_watermark";
	public static final boolean TT_LOE_EXPORT_SHOW_WATERMARK_DEFAULT = true;
	public static final String TT_DATABASE_BACKUP_TARGET_FOLDER_KEY = "tt.database.backup.target_folder";
	public static final String TT_DATABASE_BACKUP_TARGET_FOLDER_DEFAULT = "";
	public static final String TT_DATABASE_BACKUP_SCHEDULE_ENABLED_KEY = "tt.database.backup.schedule.enabled";
	public static final boolean TT_DATABASE_BACKUP_SCHEDULE_ENABLED_DEFAULT = false;
	public static final String TT_DATABASE_BACKUP_SCHEDULE_CRON_KEY = "tt.database.backup.schedule.cron";
	public static final String TT_DATABASE_BACKUP_SCHEDULE_CRON_DEFAULT = "0 0 2 * * *";
	public static final String TT_EVENT_DATABASE_BACKUP_ERROR_KEY = "tt.event.database.backup.error";
	public static final String TT_DATABASE_INITIALIZATION_COMPLETED_KEY = "tt.database.initialization.completed";
	public static final boolean TT_DATABASE_INITIALIZATION_COMPLETED_DEFAULT = false;

	private final SettingsRepository settingsRepository;

	public AppSettings(final SettingsRepository settingsRepository) {
		this.settingsRepository = settingsRepository;
	}

	public boolean ttLoeExportShowWatermark() {
		return settingsRepository.findValue(TT_LOE_EXPORT_SHOW_WATERMARK_KEY)
				.map(value -> parseBoolean(TT_LOE_EXPORT_SHOW_WATERMARK_KEY, value))
				.orElse(TT_LOE_EXPORT_SHOW_WATERMARK_DEFAULT);
	}

	public String ttDatabaseBackupTargetFolder() {
		return settingsRepository.findValue(TT_DATABASE_BACKUP_TARGET_FOLDER_KEY).map(String::trim)
				.orElse(TT_DATABASE_BACKUP_TARGET_FOLDER_DEFAULT);
	}

	public void saveTtDatabaseBackupTargetFolder(final String targetFolder) {
		settingsRepository.save(TT_DATABASE_BACKUP_TARGET_FOLDER_KEY, targetFolder == null ? "" : targetFolder.trim());
	}

	public boolean ttDatabaseBackupScheduleEnabled() {
		return settingsRepository.findValue(TT_DATABASE_BACKUP_SCHEDULE_ENABLED_KEY)
				.map(value -> parseBoolean(TT_DATABASE_BACKUP_SCHEDULE_ENABLED_KEY, value))
				.orElse(TT_DATABASE_BACKUP_SCHEDULE_ENABLED_DEFAULT);
	}

	public void saveTtDatabaseBackupScheduleEnabled(final boolean enabled) {
		settingsRepository.save(TT_DATABASE_BACKUP_SCHEDULE_ENABLED_KEY, Boolean.toString(enabled));
	}

	public String ttDatabaseBackupScheduleCron() {
		return settingsRepository.findValue(TT_DATABASE_BACKUP_SCHEDULE_CRON_KEY).map(String::trim)
				.filter(value -> !value.isBlank()).orElse(TT_DATABASE_BACKUP_SCHEDULE_CRON_DEFAULT);
	}

	public void saveTtDatabaseBackupScheduleCron(final String cron) {
		final String value = cron == null || cron.isBlank() ? TT_DATABASE_BACKUP_SCHEDULE_CRON_DEFAULT : cron.trim();
		settingsRepository.save(TT_DATABASE_BACKUP_SCHEDULE_CRON_KEY, value);
	}

	public Optional<String> ttEventDatabaseBackupError() {
		return settingsRepository.findValue(TT_EVENT_DATABASE_BACKUP_ERROR_KEY).filter(value -> !value.isBlank());
	}

	public void saveTtEventDatabaseBackupError(final String error) {
		settingsRepository.save(TT_EVENT_DATABASE_BACKUP_ERROR_KEY, error == null ? "" : error);
	}

	public void clearTtEventDatabaseBackupError() {
		settingsRepository.delete(TT_EVENT_DATABASE_BACKUP_ERROR_KEY);
	}

	public boolean ttDatabaseInitializationCompleted() {
		return settingsRepository.findValue(TT_DATABASE_INITIALIZATION_COMPLETED_KEY)
				.map(value -> parseBoolean(TT_DATABASE_INITIALIZATION_COMPLETED_KEY, value))
				.orElse(TT_DATABASE_INITIALIZATION_COMPLETED_DEFAULT);
	}

	public void saveTtDatabaseInitializationCompleted(final boolean completed) {
		settingsRepository.save(TT_DATABASE_INITIALIZATION_COMPLETED_KEY, Boolean.toString(completed));
	}

	private static boolean parseBoolean(final String key, final String value) {
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
		case "true" -> true;
		case "false" -> false;
		default ->
			throw new IllegalArgumentException("Setting " + key + " must be true or false, but was '" + value + "'.");
		};
	}
}
