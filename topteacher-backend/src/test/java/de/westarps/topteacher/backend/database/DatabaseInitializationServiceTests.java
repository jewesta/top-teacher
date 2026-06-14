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
				join subject
				    on subject.id = c.subject_id
				join grading_scale gs
				    on gs.id = c.grading_scale_id
				where (c.school_class = 'CLS_Q2' and subject.name = 'Englisch'
				        and gs.name = 'Qualifikationsphase ab ''25'
				        and gs.max_points = 150)
				   or (c.school_class = 'CLS_Q1' and subject.name = 'Spanisch'
				        and gs.name = 'Qualifikationsphase ab ''25'
				        and gs.max_points = 150)
				""")).isEqualTo(2);
		assertThat(count("""
				select count(*)
				from exam
				where title in (
				    '1. Klausur Shakespeare',
				    'Examen No 3: Migración'
				)
				""")).isEqualTo(2);
		assertThat(count("""
				select count(*)
				from (
				    select subject.name, count(*) pupil_count
				    from course_pupil cp
				    join course c
				        on c.id = cp.course_id
				    join subject
				        on subject.id = c.subject_id
				    group by subject.name
				    having (subject.name = 'Englisch' and count(*) = 5)
				       or (subject.name = 'Spanisch' and count(*) = 7)
				) course_roster
				""")).isEqualTo(2);
		assertThat(countRows("exam_pupil")).isEqualTo(12);
		assertThat(count("""
				select count(*)
				from (
				    select e.id
				    from exam e
				    join eh_part part
				        on part.exam_id = e.id
				    join eh_category category
				        on category.part_id = part.id
				    join eh_task task
				        on task.category_id = category.id
				    join eh_requirement requirement
				        on requirement.task_id = task.id
				    group by e.id
				    having sum(case when requirement.bonus then 0 else requirement.max_points end) = 150
				       and sum(case when requirement.bonus then requirement.max_points else 0 end) > 0
				) demo_exam
				""")).isEqualTo(2);
		assertThat(countRows("eh_requirement_result")).isZero();
		assertThat(countRows("eh_criterion_result")).isZero();
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isTrue();
	}

	private void assertBaseGradingScales() {
		assertThat(countRows("grading_scale")).isEqualTo(4);
		assertThat(countRows("grading_scale_range")).isEqualTo(64);
		assertThat(count("""
				select count(*)
				from grading_scale
				where (name = 'Einführungsphase' and max_points = 100)
				   or (name = 'Qualifikationsphase ab ''25' and max_points in (150, 160, 200))
				""")).isEqualTo(4);
		assertThat(count("""
				select count(*)
				from grading_scale gs
				join grading_scale_range gsr
				    on gsr.grading_scale_id = gs.id
				where gs.name = 'Qualifikationsphase ab ''25'
				  and gs.max_points = 150
				  and (
				      (gsr.grade_points = 15 and gsr.min_points = 143 and gsr.max_points = 150)
				   or (gsr.grade_points = 14 and gsr.min_points = 135 and gsr.max_points = 142)
				   or (gsr.grade_points = 13 and gsr.min_points = 128 and gsr.max_points = 134)
				   or (gsr.grade_points = 12 and gsr.min_points = 120 and gsr.max_points = 127)
				   or (gsr.grade_points = 11 and gsr.min_points = 113 and gsr.max_points = 119)
				   or (gsr.grade_points = 10 and gsr.min_points = 105 and gsr.max_points = 112)
				   or (gsr.grade_points = 9 and gsr.min_points = 98 and gsr.max_points = 104)
				   or (gsr.grade_points = 8 and gsr.min_points = 90 and gsr.max_points = 97)
				   or (gsr.grade_points = 7 and gsr.min_points = 83 and gsr.max_points = 89)
				   or (gsr.grade_points = 6 and gsr.min_points = 75 and gsr.max_points = 82)
				   or (gsr.grade_points = 5 and gsr.min_points = 68 and gsr.max_points = 74)
				   or (gsr.grade_points = 4 and gsr.min_points = 60 and gsr.max_points = 67)
				   or (gsr.grade_points = 3 and gsr.min_points = 50 and gsr.max_points = 59)
				   or (gsr.grade_points = 2 and gsr.min_points = 41 and gsr.max_points = 49)
				   or (gsr.grade_points = 1 and gsr.min_points = 30 and gsr.max_points = 40)
				   or (gsr.grade_points = 0 and gsr.min_points = 0 and gsr.max_points = 29)
				  )
				""")).isEqualTo(16);
		assertThat(count("""
				select count(*)
				from grading_scale gs
				join grading_scale_range gsr
				    on gsr.grading_scale_id = gs.id
				where gs.name = 'Qualifikationsphase ab ''25'
				  and gs.max_points = 160
				  and (
				      (gsr.grade_points = 15 and gsr.min_points = 152 and gsr.max_points = 160)
				   or (gsr.grade_points = 14 and gsr.min_points = 144 and gsr.max_points = 151)
				   or (gsr.grade_points = 13 and gsr.min_points = 136 and gsr.max_points = 143)
				   or (gsr.grade_points = 12 and gsr.min_points = 128 and gsr.max_points = 135)
				   or (gsr.grade_points = 11 and gsr.min_points = 120 and gsr.max_points = 127)
				   or (gsr.grade_points = 10 and gsr.min_points = 112 and gsr.max_points = 119)
				   or (gsr.grade_points = 9 and gsr.min_points = 104 and gsr.max_points = 111)
				   or (gsr.grade_points = 8 and gsr.min_points = 96 and gsr.max_points = 103)
				   or (gsr.grade_points = 7 and gsr.min_points = 88 and gsr.max_points = 95)
				   or (gsr.grade_points = 6 and gsr.min_points = 80 and gsr.max_points = 87)
				   or (gsr.grade_points = 5 and gsr.min_points = 72 and gsr.max_points = 79)
				   or (gsr.grade_points = 4 and gsr.min_points = 64 and gsr.max_points = 71)
				   or (gsr.grade_points = 3 and gsr.min_points = 53 and gsr.max_points = 63)
				   or (gsr.grade_points = 2 and gsr.min_points = 43 and gsr.max_points = 52)
				   or (gsr.grade_points = 1 and gsr.min_points = 32 and gsr.max_points = 42)
				   or (gsr.grade_points = 0 and gsr.min_points = 0 and gsr.max_points = 31)
				  )
				""")).isEqualTo(16);
		assertThat(count("""
				select count(*)
				from grading_scale gs
				join grading_scale_range gsr
				    on gsr.grading_scale_id = gs.id
				where gs.name = 'Qualifikationsphase ab ''25'
				  and gs.max_points = 200
				  and (
				      (gsr.grade_points = 15 and gsr.min_points = 190 and gsr.max_points = 200)
				   or (gsr.grade_points = 14 and gsr.min_points = 180 and gsr.max_points = 189)
				   or (gsr.grade_points = 13 and gsr.min_points = 170 and gsr.max_points = 179)
				   or (gsr.grade_points = 12 and gsr.min_points = 160 and gsr.max_points = 169)
				   or (gsr.grade_points = 11 and gsr.min_points = 150 and gsr.max_points = 159)
				   or (gsr.grade_points = 10 and gsr.min_points = 140 and gsr.max_points = 149)
				   or (gsr.grade_points = 9 and gsr.min_points = 130 and gsr.max_points = 139)
				   or (gsr.grade_points = 8 and gsr.min_points = 120 and gsr.max_points = 129)
				   or (gsr.grade_points = 7 and gsr.min_points = 110 and gsr.max_points = 119)
				   or (gsr.grade_points = 6 and gsr.min_points = 100 and gsr.max_points = 109)
				   or (gsr.grade_points = 5 and gsr.min_points = 90 and gsr.max_points = 99)
				   or (gsr.grade_points = 4 and gsr.min_points = 80 and gsr.max_points = 89)
				   or (gsr.grade_points = 3 and gsr.min_points = 66 and gsr.max_points = 79)
				   or (gsr.grade_points = 2 and gsr.min_points = 54 and gsr.max_points = 65)
				   or (gsr.grade_points = 1 and gsr.min_points = 40 and gsr.max_points = 53)
				   or (gsr.grade_points = 0 and gsr.min_points = 0 and gsr.max_points = 39)
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
