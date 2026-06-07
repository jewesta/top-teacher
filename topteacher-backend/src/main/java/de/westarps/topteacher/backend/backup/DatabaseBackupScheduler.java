package de.westarps.topteacher.backend.backup;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.settings.AppSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class DatabaseBackupScheduler {

	private final Object monitor = new Object();
	private final AppSettings appSettings;
	private final DatabaseBackupService backupService;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "topteacher-database-backup");
		thread.setDaemon(true);
		return thread;
	});

	private ScheduledFuture<?> scheduledBackup;

	public DatabaseBackupScheduler(final AppSettings appSettings, final DatabaseBackupService backupService) {
		this.appSettings = appSettings;
		this.backupService = backupService;
	}

	@PostConstruct
	public void start() {
		reload();
	}

	@PreDestroy
	public void stop() {
		synchronized (monitor) {
			cancelScheduledBackup();
		}
		executor.shutdownNow();
	}

	public void reload() {
		synchronized (monitor) {
			cancelScheduledBackup();
			scheduleNextBackup();
		}
	}

	private void runBackupAndScheduleNext() {
		try {
			backupService.backUpNow();
		} finally {
			reload();
		}
	}

	private void scheduleNextBackup() {
		if (!appSettings.ttDatabaseBackupScheduleEnabled()) {
			return;
		}

		try {
			final String cron = appSettings.ttDatabaseBackupScheduleCron();
			final CronExpression expression = CronExpression.parse(cron);
			final Instant now = Instant.now();
			final ZonedDateTime nextRun = expression.next(ZonedDateTime.ofInstant(now, ZoneId.systemDefault()));
			if (nextRun == null) {
				appSettings.saveTtEventDatabaseBackupError(
						"Datenbank-Backup-Zeitplan hat keinen nächsten Termin: " + cron);
				return;
			}

			final long delayMillis = Math.max(0, Duration.between(now, nextRun.toInstant()).toMillis());
			scheduledBackup = executor.schedule(this::runBackupAndScheduleNext, delayMillis, TimeUnit.MILLISECONDS);
		} catch (final IllegalArgumentException exception) {
			appSettings.saveTtEventDatabaseBackupError(
					"Datenbank-Backup-Zeitplan ist ungültig: " + exception.getMessage());
		}
	}

	private void cancelScheduledBackup() {
		if (scheduledBackup != null) {
			scheduledBackup.cancel(false);
			scheduledBackup = null;
		}
	}
}
