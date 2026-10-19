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
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeValidator;

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
	void existingMaterialsDefaultToDedicatedPagesAndKeepExplicitSettingsOnReinitialization() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);
		jdbc.execute("alter table loe_material drop column dedicated_page");
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
		assertThat(jdbc.queryForList("select dedicated_page from loe_material", Boolean.class)).isNotEmpty().containsOnly(true);
		final int materialId = jdbc.queryForObject("select min(id) from loe_material", Integer.class);
		jdbc.update("update loe_material set dedicated_page = false where id = ?", materialId);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"),
				new ClassPathResource("db/demo-data.sql")).execute(dataSource);
		assertThat(jdbc.queryForObject("select dedicated_page from loe_material where id = ?", Boolean.class, materialId)).isFalse();
	}

	@Test
	void promptsForFirstStartWhenDatabaseContainsOnlyBaseData() {
		assertThat(initializationService.shouldPromptForFirstStart()).isTrue();
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isFalse();
		assertThat(appSettings.ttFileAccessInstallationLocation()).isEqualTo(InstallationLocation.LOCAL);
	}

	@Test
	void migratesAnExistingMappingInstallationToRemoteOnlyWhenTheSettingIsMissing() {
		jdbc.update("insert into shared_folder_mapping (client_path, server_path, sort_order) values (?, ?, 0)",
				"/Volumes/School", "/mnt/school");
		settingsRepository.delete(AppSettings.TT_FILE_ACCESS_INSTALLATION_LOCATION_KEY);

		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"))
				.execute(dataSource);

		assertThat(appSettings.ttFileAccessInstallationLocation()).isEqualTo(InstallationLocation.REMOTE);

		appSettings.saveTtFileAccessInstallationLocation(InstallationLocation.LOCAL);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"),
				new ClassPathResource("db/demo-data.sql")).execute(dataSource);

		assertThat(appSettings.ttFileAccessInstallationLocation()).isEqualTo(InstallationLocation.LOCAL);
		assertThat(countRows("shared_folder_mapping")).isOne();
	}

	@Test
	void migratesAnInstallationWithoutMappingsToLocalAndPreservesAnExplicitRemoteChoice() {
		settingsRepository.delete(AppSettings.TT_FILE_ACCESS_INSTALLATION_LOCATION_KEY);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);

		assertThat(appSettings.ttFileAccessInstallationLocation()).isEqualTo(InstallationLocation.LOCAL);

		appSettings.saveTtFileAccessInstallationLocation(InstallationLocation.REMOTE);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"))
				.execute(dataSource);

		assertThat(appSettings.ttFileAccessInstallationLocation()).isEqualTo(InstallationLocation.REMOTE);
	}

	@Test
	void dropsTheLegacyExamNoteSectionTableWhenTheSchemaIsReapplied() {
		jdbc.execute("create table exam_note_section (id integer primary key)");
		assertThat(legacyNoteTableCount()).isOne();

		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);

		assertThat(legacyNoteTableCount()).isZero();
	}

	@Test
	void existingCategoriesDefaultToPrintingAndReapplyingSchemaPreservesExplicitSettings() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);
		assertThat(jdbc.queryForList("select print_tasks_on_exam_sheet from eh_category where title like 'Sprachliche%'",
				Boolean.class)).isNotEmpty().containsOnly(false);
		jdbc.execute("alter table eh_category drop column print_tasks_on_exam_sheet");
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
		assertThat(jdbc.queryForList("select print_tasks_on_exam_sheet from eh_category", Boolean.class))
				.isNotEmpty().containsOnly(true);
		final int categoryId = jdbc.queryForObject("select min(id) from eh_category", Integer.class);
		jdbc.update("update eh_category set print_tasks_on_exam_sheet = false where id = ?", categoryId);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"),
				new ClassPathResource("db/demo-data.sql")).execute(dataSource);
		assertThat(jdbc.queryForObject("select print_tasks_on_exam_sheet from eh_category where id = ?",
				Boolean.class, categoryId)).isFalse();
	}

	@Test
	void addsAnEmptyFooterToExistingExamsAndPreservesItWhenTheSchemaIsReapplied() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);
		jdbc.execute("alter table exam drop column footer_markdown");

		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);

		assertThat(jdbc.queryForList("select footer_markdown from exam", String.class)).containsOnly("");
		final int examId = jdbc.queryForObject("select min(id) from exam", Integer.class);
		jdbc.update("update exam set footer_markdown = ? where id = ?", "**Existing footer**", examId);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"),
				new ClassPathResource("db/demo-data.sql")).execute(dataSource);

		assertThat(jdbc.queryForObject("select footer_markdown from exam where id = ?", String.class, examId))
				.isEqualTo("**Existing footer**");
	}

	@Test
	void backfillsTaskPointBudgetsFromRegularRequirementsOnce() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);
		jdbc.update("update eh_task set max_points = 0");
		jdbc.update("delete from app_setting where setting_key = 'tt.database.migration.issue35.task-points'");

		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);

		assertThat(count("""
			select count(*)
			from eh_task task
			where task.max_points <> (
			    select coalesce(sum(requirement.max_points), 0)
			    from eh_requirement requirement
			    where requirement.task_id = task.id
			      and requirement.bonus = false
			)
			""")).isZero();
		assertThat(count("""
			select count(*)
			from app_setting
			where setting_key = 'tt.database.migration.issue35.task-points'
			""")).isOne();
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
	void migratesCriterionDeclarationsOnceWithoutChangingCriteriaOrScores() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);
		final int requirementId = jdbc.queryForObject("select min(id) from eh_requirement", Integer.class);
		final int criterionId = jdbc.queryForObject("select min(id) from eh_criterion where requirement_id = ?",
				Integer.class, requirementId);
		final int pupilId = jdbc.queryForObject("select min(id) from pupil", Integer.class);
		jdbc.update("insert into eh_criterion_result (criterion_id, pupil_id, achieved, point_units) values (?, ?, true, 1)",
				criterionId, pupilId);
		jdbc.update("insert into eh_requirement_result (requirement_id, pupil_id, points, point_units, comment_text) values (?, ?, 1, 1, 'Keep this comment')",
				requirementId, pupilId);
		final var criteriaBefore = jdbc.queryForList("select * from eh_criterion order by id");
		final var resultsBefore = jdbc.queryForList("select * from eh_criterion_result order by criterion_id, pupil_id");
		final var requirementResultsBefore = jdbc
				.queryForList("select * from eh_requirement_result order by requirement_id, pupil_id");
		jdbc.update("update eh_requirement set description_markdown = ? where id = ?",
				"[eins](eh:1) [halb](eh:2/0,5) [anderthalb](eh:3/1.5) [offen](eh:4/?) [neu](crit:5?points=2)",
				requirementId);
		jdbc.update("delete from app_setting where setting_key = 'tt.database.migration.issue35.criterion-urls'");

		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);

		assertThat(jdbc.queryForObject("select description_markdown from eh_requirement where id = ?", String.class,
				requirementId)).isEqualTo(
						"[eins](crit:1) [halb](crit:2?points=0,5) [anderthalb](crit:3?points=1.5) [offen](crit:4?points=?) [neu](crit:5?points=2)");
		assertThat(jdbc.queryForList("select * from eh_criterion order by id")).isEqualTo(criteriaBefore);
		assertThat(jdbc.queryForList("select * from eh_criterion_result order by criterion_id, pupil_id"))
				.isEqualTo(resultsBefore);
		assertThat(jdbc.queryForList("select * from eh_requirement_result order by requirement_id, pupil_id"))
				.isEqualTo(requirementResultsBefore);

		jdbc.update("update eh_requirement set description_markdown = '[alt](eh:1)' where id = ?", requirementId);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
		assertThat(jdbc.queryForObject("select description_markdown from eh_requirement where id = ?", String.class,
				requirementId)).isEqualTo("[alt](eh:1)");
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
			        and gs.max_points = 160)
			""")).isEqualTo(2);
		assertThat(count("""
			select count(*)
			from exam
			where title in (
			    '1. Klausur Shakespeare',
			    'Abiturprüfung 2026 Spanisch (Demo)'
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
		assertThat(countRows("loe_material")).isEqualTo(5);
		assertThat(count("""
			select count(*)
			from (
			    select e.id
			    from exam e
			    join grading_scale gs on gs.id = e.grading_scale_id
			    join eh_part part
			        on part.exam_id = e.id
			    join eh_category category
			        on category.part_id = part.id
			    join eh_task task
			        on task.category_id = category.id
			    join eh_requirement requirement
			        on requirement.task_id = task.id
			    group by e.id, gs.max_points
			    having sum(case when requirement.bonus then 0 else requirement.max_points end) = gs.max_points
			       and sum(case when requirement.bonus then requirement.max_points else 0 end) > 0
			) demo_exam
			""")).isEqualTo(2);
		assertThat(count("""
			select count(*)
			from (
			    select exam.id
			    from exam
			    join grading_scale on grading_scale.id = exam.grading_scale_id
			    join eh_part part on part.exam_id = exam.id
			    join eh_category category on category.part_id = part.id
			    join eh_task task on task.category_id = category.id
			    group by exam.id, grading_scale.max_points
			    having sum(task.max_points) = grading_scale.max_points
			) demo_exam
			""")).isEqualTo(2);
		assertThat(countRows("eh_requirement_result")).isZero();
		assertThat(countRows("eh_criterion_result")).isZero();
		assertThat(appSettings.ttDatabaseInitializationCompleted()).isTrue();
	}

	@Test
	void spanishDemoContainsTheCompleteExamAndMatchesTheExisting160PointScale() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);
		final int examId = jdbc.queryForObject("select id from exam where title = 'Abiturprüfung 2026 Spanisch (Demo)'", Integer.class);
		assertThat(jdbc.queryForObject("select gs.max_points from exam e join grading_scale gs on gs.id = e.grading_scale_id where e.id = ?",
				Integer.class, examId)).isEqualTo(160);
		assertThat(jdbc.queryForList("""
			select sum(t.max_points) from eh_part p
			join eh_category c on c.part_id = p.id join eh_task t on t.category_id = c.id
			where p.exam_id = ? group by p.id, p.sort_order order by p.sort_order
			""", Integer.class, examId)).containsExactly(110, 50);
		assertThat(jdbc.queryForList("""
			select sum(t.max_points) from eh_part p
			join eh_category c on c.part_id = p.id join eh_task t on t.category_id = c.id
			where p.exam_id = ? and not c.print_tasks_on_exam_sheet
			group by p.id, p.sort_order order by p.sort_order
			""", Integer.class, examId)).containsExactly(66, 30);
		assertThat(jdbc.queryForList("""
			select c.print_tasks_on_exam_sheet from eh_category c join eh_part p on p.id = c.part_id
			where p.exam_id = ? order by p.sort_order, c.sort_order
			""", Boolean.class, examId)).containsExactly(true, false, true, false);
		final var tasks = jdbc.query("""
			select t.* from eh_task t join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id where p.exam_id = ?
			""", (row, index) -> new LoeTask(row.getInt("id"), row.getInt("category_id"), row.getString("title"),
				row.getString("description_markdown"), row.getInt("max_points"), row.getInt("sort_order")), examId);
		final var requirements = jdbc.query("""
			select r.* from eh_requirement r join eh_task t on t.id = r.task_id
			join eh_category c on c.id = t.category_id join eh_part p on p.id = c.part_id
			where p.exam_id = ?
			""", (row, index) -> new LoeRequirement(row.getInt("id"), row.getInt("task_id"),
				row.getString("description_markdown"), row.getInt("max_points"), row.getBoolean("bonus"), row.getInt("sort_order")), examId);
		assertThat(tasks).hasSize(10);
		assertThat(tasks).filteredOn(task -> task.maxPoints() == 18).singleElement()
				.satisfies(task -> assertThat(task.descriptionMarkdown()).startsWith("Discute, a partir del texto"));
		assertThat(requirements).hasSize(28);
		assertThat(requirements).filteredOn(LoeRequirement::bonus).hasSize(2)
				.extracting(LoeRequirement::descriptionMarkdown)
				.containsOnly("[erfüllt ein weiteres aufgabenbezogenes Kriterium.](crit:1?points=2)");
		assertThat(requirements).extracting(LoeRequirement::descriptionMarkdown)
				.anySatisfy(markdown -> assertThat(markdown).startsWith("[macht Angaben zu den Rahmenbedingungen des Projekts:"))
				.anySatisfy(markdown -> assertThat(markdown).startsWith("[fasst den Ablauf und die Zielsetzung des Projektes zusammen:"))
				.anySatisfy(markdown -> assertThat(markdown).contains("dependencia de la situación en el mercado laboral"))
				.anySatisfy(markdown -> assertThat(markdown).contains("1. macht grundlegende Angaben", "2. stellt das bilinguale Konzept vor:",
						"3. nennt die positiven Effekte des Konzeptes:", "4. konzentriert sich dabei - bezogen auf den situativen Kontext -"));
		assertThat(LoeValidator.validate(160, tasks, requirements).getResults()).isEmpty();
		final var materials = jdbc.queryForList("""
			select m.description_markdown from loe_material m join eh_part p on p.id = m.part_id
			where p.exam_id = ? order by p.sort_order, m.sort_order
			""", String.class, examId);
		assertThat(materials).hasSize(3);
		assertThat(materials.getFirst()).contains("Ein- und zweisprachiges Wörterbuch");
		assertThat(materials.subList(1, 3)).allSatisfy(markdown -> {
			assertThat(markdown).contains("> [!NUMBERED]", "[^", "**Materialgrundlage:**", "https://");
			assertThat(markdown.length()).isGreaterThan(3000);
		});
		assertThat(materials.get(1)).contains("Joves Futur+", "[^azulgrana]", "[^a26]");
		assertThat(materials.getLast()).contains("Tausendfüßler", "[^dw]", "[^paritaet]");
		assertThat(jdbc.queryForObject("select footer_markdown from exam where id = ?", String.class, examId))
				.isEmpty();
		assertThat(jdbc.queryForObject("select dedicated_page from loe_material where title = 'Zugelassene Hilfsmittel'", Boolean.class)).isFalse();
		assertThat(count("select count(*) from exam where title = 'Examen No 3: Migración'")).isZero();
	}

	@Test
	void reapplyingDemoSeedsPreservesAuthoredTaskTextAndBudgets() {
		initializationService.initialize(DatabaseInitializationMode.DEMO);
		final int taskId = jdbc.queryForObject("select min(id) from eh_task", Integer.class);
		jdbc.update("update eh_task set description_markdown = 'Eigener Aufgabentext', max_points = 17 where id = ?", taskId);
		new ResourceDatabasePopulator(new ClassPathResource("db/demo-data.sql")).execute(dataSource);
		assertThat(jdbc.queryForObject("select description_markdown from eh_task where id = ?", String.class, taskId))
				.isEqualTo("Eigener Aufgabentext");
		assertThat(jdbc.queryForObject("select max_points from eh_task where id = ?", Integer.class, taskId)).isEqualTo(17);
		assertThat(countRows("exam")).isEqualTo(2);
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

	private int legacyNoteTableCount() {
		return count("""
			select count(*)
			from information_schema.tables
			where table_name = 'EXAM_NOTE_SECTION'
			""");
	}

	private int count(final String sql) {
		final Integer result = jdbc.queryForObject(sql, Integer.class);
		return result == null ? 0 : result;
	}
}
