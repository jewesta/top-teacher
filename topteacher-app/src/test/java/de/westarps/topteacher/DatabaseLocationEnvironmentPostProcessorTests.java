package de.westarps.topteacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DatabaseLocationEnvironmentPostProcessorTests {

	@TempDir
	private Path tempDir;

	private final DatabaseLocationEnvironmentPostProcessor processor =
			new DatabaseLocationEnvironmentPostProcessor();

	@Test
	void createsDefaultDatabaseFilePropertyWhenDatasourceUrlUsesIt() {
		final Path databaseFile = tempDir.resolve("Application Support/TopTeacher/topteacher");
		final DatabaseLocationEnvironmentPostProcessor processor =
				new DatabaseLocationEnvironmentPostProcessor(() -> databaseFile);
		final MockEnvironment environment = new MockEnvironment()
				.withProperty("spring.datasource.url", "jdbc:h2:file:${tt.database.file};AUTO_SERVER=TRUE");

		assertThatCode(() -> process(processor, environment)).doesNotThrowAnyException();
		assertThat(environment.getProperty("tt.database.file")).isEqualTo(databaseFile.toString());
		assertThat(databaseFile.getParent()).isDirectory();
	}

	@Test
	void acceptsDatabaseFilePropertyWhenDirectoryExists() {
		final MockEnvironment environment = new MockEnvironment()
				.withProperty("tt.database.file", tempDir.resolve("topteacher").toString())
				.withProperty("spring.datasource.url", "jdbc:h2:file:${tt.database.file};AUTO_SERVER=TRUE");

		assertThatCode(() -> process(environment)).doesNotThrowAnyException();
	}

	@Test
	void rejectsDatabaseFilePropertyWhenDirectoryIsMissing() {
		final MockEnvironment environment = new MockEnvironment()
				.withProperty("tt.database.file", tempDir.resolve("missing/topteacher").toString())
				.withProperty("spring.datasource.url", "jdbc:h2:file:${tt.database.file};AUTO_SERVER=TRUE");

		assertThatThrownBy(() -> process(environment))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("does not exist");
	}

	@Test
	void ignoresMemoryDatasourceUrls() {
		final MockEnvironment environment = new MockEnvironment()
				.withProperty("spring.datasource.url", "jdbc:h2:mem:topteacher-test;DB_CLOSE_DELAY=-1");

		assertThatCode(() -> process(environment)).doesNotThrowAnyException();
	}

	@Test
	void validatesExplicitFileDatasourceUrls() {
		final MockEnvironment environment = new MockEnvironment()
				.withProperty("spring.datasource.url",
						"jdbc:h2:file:" + tempDir.resolve("topteacher") + ";AUTO_SERVER=TRUE");

		assertThatCode(() -> process(environment)).doesNotThrowAnyException();
	}

	@Test
	void choosesMacOsDefaultDatabaseFile() {
		assertThat(DatabaseLocationEnvironmentPostProcessor.defaultDatabaseFile("Mac OS X", "/Users/jens", null, null))
				.isEqualTo(Path.of("/Users/jens/Library/Application Support/TopTeacher/topteacher"));
	}

	@Test
	void choosesWindowsDefaultDatabaseFile() {
		assertThat(DatabaseLocationEnvironmentPostProcessor.defaultDatabaseFile("Windows 11", "C:\\Users\\Jens",
				"C:\\Users\\Jens\\AppData\\Roaming", null))
				.isEqualTo(Path.of("C:\\Users\\Jens\\AppData\\Roaming").resolve("TopTeacher").resolve("topteacher"));
	}

	@Test
	void choosesLinuxDefaultDatabaseFile() {
		assertThat(DatabaseLocationEnvironmentPostProcessor.defaultDatabaseFile("Linux", "/home/jens", null, null))
				.isEqualTo(Path.of("/home/jens/.local/share/TopTeacher/topteacher"));
	}

	private void process(final MockEnvironment environment) {
		process(processor, environment);
	}

	private void process(final DatabaseLocationEnvironmentPostProcessor processor,
			final MockEnvironment environment) {
		processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
	}
}
