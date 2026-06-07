package de.westarps.topteacher.backend.demo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tt.demo-data.create", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataInitializer.class);
	private static final String DEMO_DATA_SCRIPT = "classpath:db/demo-data.sql";
	private static final List<String> BLOCKING_TABLES = List.of("pupil", "course", "course_pupil", "exam",
			"eh_part", "eh_category", "eh_task", "eh_requirement", "eh_criterion", "eh_criterion_result",
			"eh_requirement_result", "exam_note_section");

	private final DataSource dataSource;
	private final JdbcTemplate jdbc;
	private final ResourceLoader resourceLoader;

	public DemoDataInitializer(final DataSource dataSource, final JdbcTemplate jdbc,
			final ResourceLoader resourceLoader) {
		this.dataSource = dataSource;
		this.jdbc = jdbc;
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void run(final ApplicationArguments args) {
		findBlockingData().ifPresent(reason -> {
			throw new IllegalStateException("Demo data can only be created in an empty database. Found " + reason + ".");
		});

		final Resource script = resourceLoader.getResource(DEMO_DATA_SCRIPT);
		LOGGER.info("Creating TopTeacher demo data from {}.", DEMO_DATA_SCRIPT);
		try (Connection connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(connection, script);
		} catch (final SQLException exception) {
			throw new IllegalStateException("Could not create TopTeacher demo data.", exception);
		}
		LOGGER.info("TopTeacher demo data created.");
	}

	private Optional<String> findBlockingData() {
		for (final String table : BLOCKING_TABLES) {
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
		return Optional.empty();
	}

	private int countRows(final String table) {
		return count("select count(*) from " + table);
	}

	private int count(final String sql) {
		final Integer result = jdbc.queryForObject(sql, Integer.class);
		return result == null ? 0 : result;
	}
}
