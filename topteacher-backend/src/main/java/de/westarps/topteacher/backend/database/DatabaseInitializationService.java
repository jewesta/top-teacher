package de.westarps.topteacher.backend.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.settings.AppSettings;

@Service
public class DatabaseInitializationService {

	private static final String SCHEMA_SCRIPT = "classpath:db/schema.sql";
	private static final String BASE_DATA_SCRIPT = "classpath:db/base-data.sql";
	private static final String DEMO_DATA_SCRIPT = "classpath:db/demo-data.sql";
	private static final String PROMPT_ON_FIRST_START_PROPERTY = "tt.database.initialization.prompt-on-first-start";
	private static final String CORE_SUBJECT_VALUES = """
			values
			    ('Deutsch'),
			    ('Mathematik'),
			    ('Englisch'),
			    ('Französisch'),
			    ('Latein'),
			    ('Spanisch'),
			    ('Erdkunde'),
			    ('Geschichte'),
			    ('Politik'),
			    ('Biologie'),
			    ('Chemie'),
			    ('Physik'),
			    ('Informatik'),
			    ('Ev. Religionslehre'),
			    ('Kath. Religionslehre'),
			    ('Ethik'),
			    ('Philosophie'),
			    ('Kunst'),
			    ('Musik'),
			    ('Sport')
			""";
	private static final List<String> BUSINESS_TABLES = List.of("pupil", "course", "course_pupil", "exam", "eh_part",
			"eh_category", "eh_task", "eh_requirement", "eh_criterion", "eh_criterion_result", "eh_requirement_result",
			"exam_note_section");

	private final AppSettings appSettings;
	private final DataSource dataSource;
	private final Environment environment;
	private final JdbcTemplate jdbc;
	private final ResourceLoader resourceLoader;

	public DatabaseInitializationService(final AppSettings appSettings, final DataSource dataSource,
			final Environment environment, final JdbcTemplate jdbc, final ResourceLoader resourceLoader) {
		this.appSettings = appSettings;
		this.dataSource = dataSource;
		this.environment = environment;
		this.jdbc = jdbc;
		this.resourceLoader = resourceLoader;
	}

	public boolean shouldPromptForFirstStart() {
		if (!environment.getProperty(PROMPT_ON_FIRST_START_PROPERTY, Boolean.class, true)
				|| appSettings.ttDatabaseInitializationCompleted()) {
			return false;
		}
		if (containsDataBeyondBaseReferenceData()) {
			appSettings.saveTtDatabaseInitializationCompleted(true);
			return false;
		}
		return true;
	}

	public void initialize(final DatabaseInitializationMode mode) {
		try (Connection connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(connection, script(SCHEMA_SCRIPT));
			truncatePublicTables(connection);
			ScriptUtils.executeSqlScript(connection, script(SCHEMA_SCRIPT));
			ScriptUtils.executeSqlScript(connection, script(BASE_DATA_SCRIPT));
			if (mode == DatabaseInitializationMode.DEMO) {
				ScriptUtils.executeSqlScript(connection, script(DEMO_DATA_SCRIPT));
			}
		} catch (final SQLException | ScriptException exception) {
			throw new IllegalStateException("Could not initialize database.", exception);
		}

		appSettings.saveTtDatabaseInitializationCompleted(true);
	}

	public boolean containsDataBeyondBaseReferenceData() {
		return findBlockingData().isPresent();
	}

	public Optional<String> findBlockingData() {
		for (final String table : BUSINESS_TABLES) {
			if (countRows(table) > 0) {
				return Optional.of("data in table " + table);
			}
		}
		if (count("""
				select count(*)
				from grading_scale
				where name <> 'Standard'
				""") > 0) {
			return Optional.of("custom grading scales");
		}
		if (count("""
				select count(*)
				from grading_scale_range
				where grading_scale_id not in (
				    select id
				    from grading_scale
				    where name = 'Standard'
				)
				""") > 0) {
			return Optional.of("custom grading scale ranges");
		}
		if (hasCustomSubjectSetup()) {
			return Optional.of("custom subjects");
		}
		return Optional.empty();
	}

	private void truncatePublicTables(final Connection connection) throws SQLException {
		final List<String> tables = publicTables(connection);
		try (Statement statement = connection.createStatement()) {
			statement.execute("set referential_integrity false");
			for (final String table : tables) {
				statement.execute("truncate table " + table + " restart identity");
			}
		} finally {
			try (Statement statement = connection.createStatement()) {
				statement.execute("set referential_integrity true");
			}
		}
	}

	private List<String> publicTables(final Connection connection) throws SQLException {
		final List<String> tables = new ArrayList<>();
		try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("""
				select table_name
				from information_schema.tables
				where table_schema = 'PUBLIC'
				  and table_type = 'BASE TABLE'
				order by table_name
				""")) {
			while (resultSet.next()) {
				tables.add(quoteIdentifier(resultSet.getString("table_name")));
			}
		}
		return tables;
	}

	private boolean hasCustomSubjectSetup() {
		return count("""
				select count(*)
				from subject s
				where s.lifecycle <> 'ACTIVE'
				   or not exists (
				       select 1
				       from (
				""" + CORE_SUBJECT_VALUES + """
				       ) core_subject(name)
				       where core_subject.name = s.name
				   )
				""") > 0 || count("""
				select count(*)
				from (
				""" + CORE_SUBJECT_VALUES + """
				) core_subject(name)
				where not exists (
				    select 1
				    from subject s
				    where s.name = core_subject.name
				      and s.lifecycle = 'ACTIVE'
				)
				""") > 0;
	}

	private int countRows(final String table) {
		return count("select count(*) from " + table);
	}

	private int count(final String sql) {
		final Integer result = jdbc.queryForObject(sql, Integer.class);
		return result == null ? 0 : result;
	}

	private Resource script(final String location) {
		return resourceLoader.getResource(location);
	}

	private static String quoteIdentifier(final String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}
}
