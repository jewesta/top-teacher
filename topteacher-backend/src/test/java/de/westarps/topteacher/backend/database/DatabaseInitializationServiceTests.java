package de.westarps.topteacher.backend.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;

import de.westarps.topteacher.backend.repo.SettingsRepository;
import de.westarps.topteacher.backend.settings.AppSettings;

class DatabaseInitializationServiceTests {

	private SingleConnectionDataSource dataSource;
	private JdbcTemplate jdbc;
	private SettingsRepository settingsRepository;
	private AppSettings appSettings;
	private MockEnvironment environment;
	private DatabaseInitializationService initializationService;

	@BeforeEach
	void setUp() {
		final String databaseName = "topteacher-initialization-test-" + UUID.randomUUID();
		dataSource = new SingleConnectionDataSource(
				"jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "", true);
		jdbc = new JdbcTemplate(dataSource);
		settingsRepository = new SettingsRepository(new NamedParameterJdbcTemplate(dataSource));
		appSettings = new AppSettings(settingsRepository);
		environment = new MockEnvironment().withProperty("tt.database.initialization.prompt-on-first-start", "true");
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"))
				.execute(dataSource);
		initializationService = new DatabaseInitializationService(appSettings, dataSource, environment, jdbc,
				new DefaultResourceLoader());
	}

	@AfterEach
	void tearDown() {
		jdbc.execute("shutdown");
		dataSource.destroy();
	}

	@Test
	void promptsForFirstStartWhenDatabaseContainsOnlyBaseData() {
		assertThat(initializationService.shouldPromptForFirstStart()).isTrue();
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isFalse();
	}

	@Test
	void skipsFirstStartPromptWhenDatabaseAlreadyContainsBusinessData() {
		jdbc.update("""
				insert into pupil (name, surname, lifecycle)
				values ('Real', 'Pupil', 'ACTIVE')
				""");

		assertThat(initializationService.shouldPromptForFirstStart()).isFalse();
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isTrue();
	}

	@Test
	void skipsFirstStartPromptWhenHiddenPropertyDisablesIt() {
		environment.setProperty("tt.database.initialization.prompt-on-first-start", "false");

		assertThat(initializationService.shouldPromptForFirstStart()).isFalse();
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isFalse();
	}

	@Test
	void initializesEmptyDatabaseAndMarksItCompleted() {
		jdbc.update("""
				insert into pupil (name, surname, lifecycle)
				values ('Real', 'Pupil', 'ACTIVE')
				""");
		jdbc.update("""
				insert into subject (name, lifecycle)
				values ('Darstellendes Spiel', 'ACTIVE')
				""");

		initializationService.initialize(DatabaseInitializationMode.EMPTY);

		assertThat(countRows("pupil")).isZero();
		assertThat(countRows("course")).isZero();
		assertThat(countRows("subject")).isEqualTo(20);
		assertThat(countRows("grading_scale")).isEqualTo(1);
		assertThat(countRows("grading_scale_range")).isEqualTo(16);
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isTrue();
	}

	@Test
	void initializesDemoDatabaseAndMarksItCompleted() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);

		assertThat(countRows("subject")).isEqualTo(20);
		assertThat(countRows("pupil")).isEqualTo(20);
		assertThat(countRows("course")).isEqualTo(2);
		assertThat(countRows("exam")).isEqualTo(2);
		assertThat(count("""
				select count(*)
				from exam
				where title in (
				    'Klausur Windenergie und Klimawandel',
				    'Klausur Reaktionsgeschwindigkeit und Gleichgewicht'
				)
				""")).isEqualTo(2);
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isTrue();
	}

	private int countRows(final String table) {
		return count("select count(*) from " + table);
	}

	private int count(final String sql) {
		final Integer result = jdbc.queryForObject(sql, Integer.class);
		return result == null ? 0 : result;
	}
}
