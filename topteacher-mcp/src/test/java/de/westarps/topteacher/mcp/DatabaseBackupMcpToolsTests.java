package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.backup.DatabaseBackupService;
import de.westarps.topteacher.mcp.DatabaseBackupMcpTools.DatabaseBackupStatus;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupMcpToolsTests {

	@Mock
	private DatabaseBackupService backupService;

	@Test
	void reportsTheCreatedBackupFileName() {
		when(backupService.backUpNow()).thenReturn(Path.of("/configured/backups/topteacher-db-20260918-230000.zip"));

		final var result = new DatabaseBackupMcpTools(backupService).createDatabaseBackup();

		assertThat(result.status()).isEqualTo(DatabaseBackupStatus.CREATED);
		assertThat(result.backups()).singleElement()
				.satisfies(backup -> assertThat(backup.fileName()).isEqualTo("topteacher-db-20260918-230000.zip"));
	}

	@Test
	void reportsAnUnconfiguredBackupWithoutAProtocolError() {
		when(backupService.backUpNow()).thenThrow(new IllegalStateException(
				"Datenbank-Backup fehlgeschlagen: Es wurde kein Backup-Zielordner konfiguriert."));

		final var result = new DatabaseBackupMcpTools(backupService).createDatabaseBackup();

		assertThat(result.status()).isEqualTo(DatabaseBackupStatus.FAILED);
		assertThat(result.message()).contains("kein Backup-Zielordner konfiguriert");
		assertThat(result.backups()).isEmpty();
	}
}
