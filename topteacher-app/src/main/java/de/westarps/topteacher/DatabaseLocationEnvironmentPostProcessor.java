package de.westarps.topteacher;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

public class DatabaseLocationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String DATABASE_FILE_PROPERTY = "tt.database.file";
	private static final String DATASOURCE_URL_PROPERTY = "spring.datasource.url";
	private static final String H2_FILE_URL_PREFIX = "jdbc:h2:file:";

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void postProcessEnvironment(final ConfigurableEnvironment environment,
			final SpringApplication application) {
		final String databaseFile = environment.getProperty(DATABASE_FILE_PROPERTY);
		if (StringUtils.hasText(databaseFile)) {
			validateDatabaseFile(databaseFile);
			return;
		}

		final String rawDatasourceUrl = rawProperty(environment, DATASOURCE_URL_PROPERTY).orElse("");
		if (rawDatasourceUrl.contains("${" + DATABASE_FILE_PROPERTY + "}")) {
			throw new IllegalStateException("Missing required property '" + DATABASE_FILE_PROPERTY
					+ "'. Set it to the H2 database file path without the '.mv.db' suffix.");
		}

		filePathFromDatasourceUrl(environment.getProperty(DATASOURCE_URL_PROPERTY))
				.ifPresent(DatabaseLocationEnvironmentPostProcessor::validateDatabaseFile);
	}

	private static Optional<String> rawProperty(final ConfigurableEnvironment environment, final String propertyName) {
		for (final PropertySource<?> propertySource : environment.getPropertySources()) {
			final Object value = propertySource.getProperty(propertyName);
			if (value != null) {
				return Optional.of(value.toString());
			}
		}
		return Optional.empty();
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

	private static void validateDatabaseFile(final String databaseFile) {
		final Path path = toPath(databaseFile);
		final Path parent = path.getParent();
		if (parent == null) {
			throw new IllegalStateException("Property '" + DATABASE_FILE_PROPERTY
					+ "' must point to a database file in an existing directory.");
		}
		if (!Files.exists(parent)) {
			throw new IllegalStateException("Database directory does not exist: " + parent);
		}
		if (!Files.isDirectory(parent)) {
			throw new IllegalStateException("Database parent path is not a directory: " + parent);
		}
		if (!Files.isWritable(parent)) {
			throw new IllegalStateException("Database directory is not writable: " + parent);
		}
		if (Files.exists(path) && Files.isDirectory(path)) {
			throw new IllegalStateException("Database file path points to a directory: " + path);
		}

		final Path h2File = toPath(databaseFile + ".mv.db");
		if (Files.exists(h2File)) {
			if (!Files.isRegularFile(h2File)) {
				throw new IllegalStateException("H2 database path is not a regular file: " + h2File);
			}
			if (!Files.isReadable(h2File)) {
				throw new IllegalStateException("H2 database file is not readable: " + h2File);
			}
			if (!Files.isWritable(h2File)) {
				throw new IllegalStateException("H2 database file is not writable: " + h2File);
			}
		}
	}

	private static Path toPath(final String databaseFile) {
		try {
			return Path.of(databaseFile).toAbsolutePath().normalize();
		} catch (final InvalidPathException exception) {
			throw new IllegalStateException("Invalid database file path: " + databaseFile, exception);
		}
	}
}
