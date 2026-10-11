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
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.McpAccessLevel;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupMcpToolsTests extends FullMcpAccessTest {

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
		assertThat(result.message()).isEqualTo("Database backup could not be created. Check the backup settings in TopTeacher.");
		assertThat(result.backups()).isEmpty();
	}

	@Test
	void doesNotDiscloseServerPathsOrDatabaseFailureDetailsInEitherAccessMode() {
		when(backupService.backUpNow()).thenThrow(new IllegalStateException(
				"Datenbank-Backup fehlgeschlagen: BACKUP TO '/private/server/backups/topteacher-db.zip'; SQL state 90028"));
		final DatabaseBackupMcpTools tools = new DatabaseBackupMcpTools(backupService);

		for (final McpAccessLevel mode : McpAccessLevel.values()) {
			try (McpAccessContext.Scope ignored = McpAccessContext.open(mode)) {
				final var result = tools.createDatabaseBackup();
				assertThat(result.status()).isEqualTo(DatabaseBackupStatus.FAILED);
				assertThat(result.message()).contains("backup settings").doesNotContain("/private", "SQL", "90028");
				assertThat(result.backups()).isEmpty();
			}
		}
	}
}
