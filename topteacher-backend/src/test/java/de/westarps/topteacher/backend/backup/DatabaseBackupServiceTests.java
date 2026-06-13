package de.westarps.topteacher.backend.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import de.westarps.topteacher.backend.settings.AppSettings;

class DatabaseBackupServiceTests {

	@TempDir
	private Path tempDir;

	@Test
	void executesH2BackupToConfiguredTargetFolder() {
		final AppSettings appSettings = mock(AppSettings.class);
		final JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(appSettings.ttDatabaseBackupTargetFolder()).thenReturn(tempDir.toString());

		final DatabaseBackupService service = new DatabaseBackupService(appSettings, jdbc, fixedClock());

		final Path backupFile = service.backUpNow();

		assertThat(backupFile).isEqualTo(tempDir.resolve("topteacher-db-20260607-201530.zip"));
		verify(jdbc).execute(contains("BACKUP TO"));
		verify(jdbc).execute(contains("topteacher-db-20260607-201530.zip"));
		verify(appSettings).clearTtEventDatabaseBackupError();
	}

	@Test
	void keepsExistingBackupsByChoosingTheNextFreeFileName() throws Exception {
		final AppSettings appSettings = mock(AppSettings.class);
		final JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(appSettings.ttDatabaseBackupTargetFolder()).thenReturn(tempDir.toString());
		Files.createFile(tempDir.resolve("topteacher-db-20260607-201530.zip"));

		final DatabaseBackupService service = new DatabaseBackupService(appSettings, jdbc, fixedClock());

		final Path backupFile = service.backUpNow();

		assertThat(backupFile).isEqualTo(tempDir.resolve("topteacher-db-20260607-201530-1.zip"));
		verify(jdbc).execute(contains("topteacher-db-20260607-201530-1.zip"));
	}

	@Test
	void storesBackupErrorsAsAppSettingEvents() {
		final AppSettings appSettings = mock(AppSettings.class);
		final JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(appSettings.ttDatabaseBackupTargetFolder()).thenReturn(tempDir.toString());
		doThrow(new IllegalStateException("boom")).when(jdbc).execute(anyString());

		final DatabaseBackupService service = new DatabaseBackupService(appSettings, jdbc, fixedClock());

		assertThatThrownBy(service::backUpNow).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Datenbank-Backup fehlgeschlagen").hasMessageContaining("boom");
		verify(appSettings).saveTtEventDatabaseBackupError(contains("boom"));
	}

	private static Clock fixedClock() {
		return Clock.fixed(Instant.parse("2026-06-07T20:15:30Z"), ZoneOffset.UTC);
	}
}
