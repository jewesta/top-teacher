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
		assertBaseGradingScales();
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isTrue();
	}

	@Test
	void initializesDemoDatabaseAndMarksItCompleted() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);

		assertThat(countRows("subject")).isEqualTo(20);
		assertBaseGradingScales();
		assertThat(countRows("pupil")).isEqualTo(20);
		assertThat(countRows("course")).isEqualTo(2);
		assertThat(countRows("exam")).isEqualTo(2);
		assertThat(count("""
				select count(*)
				from course c
				join grading_scale gs
				    on gs.id = c.grading_scale_id
				where (c.school_class = 'CLS_10A' and gs.name = 'Einführungsphase')
				   or (c.school_class = 'CLS_Q1' and gs.name = 'Qualifikationsphase')
				""")).isEqualTo(2);
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

	private void assertBaseGradingScales() {
		assertThat(countRows("grading_scale")).isEqualTo(2);
		assertThat(countRows("grading_scale_range")).isEqualTo(32);
		assertThat(count("""
				select count(*)
				from grading_scale
				where name in ('Einführungsphase', 'Qualifikationsphase')
				""")).isEqualTo(2);
		assertThat(count("""
				select count(*)
				from grading_scale gs
				join grading_scale_range gsr
				    on gsr.grading_scale_id = gs.id
				where gs.name = 'Qualifikationsphase'
				  and (
				      (gsr.grade_points = 15 and gsr.min_points = 144 and gsr.max_points = 150)
				   or (gsr.grade_points = 14 and gsr.min_points = 137 and gsr.max_points = 143)
				   or (gsr.grade_points = 13 and gsr.min_points = 130 and gsr.max_points = 136)
				   or (gsr.grade_points = 12 and gsr.min_points = 123 and gsr.max_points = 129)
				   or (gsr.grade_points = 11 and gsr.min_points = 116 and gsr.max_points = 122)
				   or (gsr.grade_points = 10 and gsr.min_points = 109 and gsr.max_points = 115)
				   or (gsr.grade_points = 9 and gsr.min_points = 102 and gsr.max_points = 108)
				   or (gsr.grade_points = 8 and gsr.min_points = 95 and gsr.max_points = 101)
				   or (gsr.grade_points = 7 and gsr.min_points = 88 and gsr.max_points = 94)
				   or (gsr.grade_points = 6 and gsr.min_points = 81 and gsr.max_points = 87)
				   or (gsr.grade_points = 5 and gsr.min_points = 74 and gsr.max_points = 80)
				   or (gsr.grade_points = 4 and gsr.min_points = 67 and gsr.max_points = 73)
				   or (gsr.grade_points = 3 and gsr.min_points = 56 and gsr.max_points = 66)
				   or (gsr.grade_points = 2 and gsr.min_points = 45 and gsr.max_points = 55)
				   or (gsr.grade_points = 1 and gsr.min_points = 34 and gsr.max_points = 44)
				   or (gsr.grade_points = 0 and gsr.min_points = 0 and gsr.max_points = 33)
				  )
				""")).isEqualTo(16);
	}

	private int countRows(final String table) {
		return count("select count(*) from " + table);
	}

	private int count(final String sql) {
		final Integer result = jdbc.queryForObject(sql, Integer.class);
		return result == null ? 0 : result;
	}
}
