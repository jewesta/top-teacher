package de.westarps.topteacher.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.SettingsRepository;

@SpringBootTest
class AppSettingsTests {

	@Autowired
	private AppSettings appSettings;

	@Autowired
	private SettingsRepository settingsRepository;

	@AfterEach
	void resetSettings() {
		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "true");
		settingsRepository.save(AppSettings.TT_DATABASE_BACKUP_TARGET_FOLDER_KEY, "");
		settingsRepository.save(AppSettings.TT_DATABASE_BACKUP_SCHEDULE_ENABLED_KEY, "false");
		settingsRepository.save(AppSettings.TT_DATABASE_BACKUP_SCHEDULE_CRON_KEY, "0 0 2 * * *");
		settingsRepository.delete(AppSettings.TT_EVENT_DATABASE_BACKUP_ERROR_KEY);
	}

	@Test
	void defaultsToShowingTheTeacherWatermark() {
		settingsRepository.delete(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY);

		assertThat(appSettings.ttLoeExportShowWatermark()).isTrue();
	}

	@Test
	void readsTheTeacherWatermarkSettingFromTheDatabase() {
		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "false");
		assertThat(appSettings.ttLoeExportShowWatermark()).isFalse();

		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "true");
		assertThat(appSettings.ttLoeExportShowWatermark()).isTrue();
	}

	@Test
	void rejectsInvalidBooleanValues() {
		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "sometimes");

		assertThatThrownBy(appSettings::ttLoeExportShowWatermark).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY);
	}

	@Test
	void defaultsDatabaseBackupSettings() {
		settingsRepository.delete(AppSettings.TT_DATABASE_BACKUP_TARGET_FOLDER_KEY);
		settingsRepository.delete(AppSettings.TT_DATABASE_BACKUP_SCHEDULE_ENABLED_KEY);
		settingsRepository.delete(AppSettings.TT_DATABASE_BACKUP_SCHEDULE_CRON_KEY);

		assertThat(appSettings.ttDatabaseBackupTargetFolder()).isEmpty();
		assertThat(appSettings.ttDatabaseBackupScheduleEnabled()).isFalse();
		assertThat(appSettings.ttDatabaseBackupScheduleCron()).isEqualTo("0 0 2 * * *");
	}

	@Test
	void savesDatabaseBackupSettings() {
		appSettings.saveTtDatabaseBackupTargetFolder(" /Volumes/Backups/TopTeacher ");
		appSettings.saveTtDatabaseBackupScheduleEnabled(true);
		appSettings.saveTtDatabaseBackupScheduleCron(" 0 30 1 * * * ");

		assertThat(appSettings.ttDatabaseBackupTargetFolder()).isEqualTo("/Volumes/Backups/TopTeacher");
		assertThat(appSettings.ttDatabaseBackupScheduleEnabled()).isTrue();
		assertThat(appSettings.ttDatabaseBackupScheduleCron()).isEqualTo("0 30 1 * * *");
	}

	@Test
	void savesAndClearsDatabaseBackupErrorEvent() {
		appSettings.saveTtEventDatabaseBackupError("Backup failed");
		assertThat(appSettings.ttEventDatabaseBackupError()).contains("Backup failed");

		appSettings.clearTtEventDatabaseBackupError();
		assertThat(appSettings.ttEventDatabaseBackupError()).isEmpty();
	}
}
