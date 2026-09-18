package de.westarps.topteacher.mcp;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.backup.DatabaseBackupService;

/**
 * MCP operation for creating a database backup through TopTeacher's configured
 * backup service.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class DatabaseBackupMcpTools {

	private final DatabaseBackupService backupService;

	public DatabaseBackupMcpTools(final DatabaseBackupService backupService) {
		this.backupService = Objects.requireNonNull(backupService, "backupService");
	}

	@McpTool(name = "create_database_backup", title = "Create a TopTeacher database backup",
			description = "Create an H2 database backup in the target folder already configured in TopTeacher. This tool accepts no path and never changes backup settings. It reports FAILED with the existing TopTeacher error message when backup is not configured or can not be written.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = false, openWorldHint = false))
	public DatabaseBackupResult createDatabaseBackup() {
		try {
			final Path backupFile = backupService.backUpNow();
			return new DatabaseBackupResult(DatabaseBackupStatus.CREATED, "Database backup created.",
					List.of(new DatabaseBackupFileView(backupFile.getFileName().toString())));
		} catch (final IllegalStateException failure) {
			return new DatabaseBackupResult(DatabaseBackupStatus.FAILED, failure.getMessage(), List.of());
		}
	}

	public enum DatabaseBackupStatus {
		CREATED,
		FAILED
	}

	public record DatabaseBackupFileView(String fileName) {
	}

	public record DatabaseBackupResult(DatabaseBackupStatus status, String message,
			List<DatabaseBackupFileView> backups) {

		public DatabaseBackupResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			backups = List.copyOf(Objects.requireNonNull(backups, "backups"));
		}
	}
}
