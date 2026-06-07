package de.westarps.topteacher.backend.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.settings.AppSettings;

@Service
public class DatabaseBackupService {

	private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final AppSettings appSettings;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	@Autowired
	public DatabaseBackupService(final AppSettings appSettings, final JdbcTemplate jdbc) {
		this(appSettings, jdbc, Clock.systemDefaultZone());
	}

	DatabaseBackupService(final AppSettings appSettings, final JdbcTemplate jdbc, final Clock clock) {
		this.appSettings = appSettings;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public Path backUpNow() {
		try {
			final Path targetFile = nextTargetFile();
			jdbc.execute("BACKUP TO '" + sqlString(targetFile.toString()) + "'");
			appSettings.clearTtEventDatabaseBackupError();
			return targetFile;
		} catch (final RuntimeException | IOException exception) {
			final String message = "Datenbank-Backup fehlgeschlagen: " + exception.getMessage();
			appSettings.saveTtEventDatabaseBackupError(message);
			throw new IllegalStateException(message, exception);
		}
	}

	private Path nextTargetFile() throws IOException {
		final String targetFolder = appSettings.ttDatabaseBackupTargetFolder();
		if (targetFolder.isBlank()) {
			throw new IllegalStateException("Es wurde kein Backup-Zielordner konfiguriert.");
		}

		final Path folder = Path.of(targetFolder).toAbsolutePath().normalize();
		Files.createDirectories(folder);
		if (!Files.isDirectory(folder) || !Files.isWritable(folder)) {
			throw new IllegalStateException("Der Backup-Zielordner ist nicht beschreibbar: " + folder);
		}

		final String timestamp = BACKUP_TIMESTAMP.format(clock.instant().atZone(clock.getZone()));
		return uniqueTargetFile(folder, "topteacher-db-" + timestamp + ".zip");
	}

	private static Path uniqueTargetFile(final Path folder, final String fileName) {
		Path candidate = folder.resolve(fileName);
		int suffix = 1;
		while (Files.exists(candidate)) {
			final String stem = fileName.substring(0, fileName.length() - ".zip".length());
			candidate = folder.resolve(stem + "-" + suffix + ".zip");
			suffix++;
		}
		return candidate;
	}

	private static String sqlString(final String value) {
		return value.replace("'", "''");
	}
}
