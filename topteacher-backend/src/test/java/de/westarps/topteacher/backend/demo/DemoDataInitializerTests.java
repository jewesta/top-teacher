package de.westarps.topteacher.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class DemoDataInitializerTests {

	private SingleConnectionDataSource dataSource;
	private JdbcTemplate jdbc;
	private DemoDataInitializer initializer;

	@BeforeEach
	void setUp() {
		final String databaseName = "topteacher-demo-test-" + UUID.randomUUID();
		dataSource = new SingleConnectionDataSource(
				"jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "", true);
		jdbc = new JdbcTemplate(dataSource);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"))
				.execute(dataSource);
		initializer = new DemoDataInitializer(dataSource, jdbc, new DefaultResourceLoader());
	}

	@AfterEach
	void tearDown() {
		jdbc.execute("shutdown");
		dataSource.destroy();
	}

	@Test
	void createsDemoDataWhenDatabaseContainsOnlyBaseData() {
		assertThat(countRows("pupil")).isZero();

		initializer.run(null);

		assertThat(countRows("pupil")).isEqualTo(20);
		assertThat(countRows("course")).isGreaterThan(0);
		assertThat(countRows("exam")).isGreaterThan(0);
		assertThat(count("""
				select count(*)
				from exam
				where title = 'Klausur Nr. 4'
				""")).isEqualTo(1);
	}

	@Test
	void refusesToCreateDemoDataWhenBusinessDataExists() {
		jdbc.update("""
				insert into pupil (name, surname, lifecycle)
				values ('Real', 'Pupil', 'ACTIVE')
				""");

		assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("empty database").hasMessageContaining("pupil");

		assertThat(countRows("course")).isZero();
	}

	@Test
	void refusesToCreateDemoDataWhenCustomReferenceDataExists() {
		jdbc.update("""
				insert into grading_scale (name, max_points, lifecycle)
				values ('Custom', 150, 'ACTIVE')
				""");

		assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("custom grading scales");

		assertThat(countRows("pupil")).isZero();
	}

	private int countRows(final String table) {
		return count("select count(*) from " + table);
	}

	private int count(final String sql) {
		final Integer result = jdbc.queryForObject(sql, Integer.class);
		return result == null ? 0 : result;
	}
}
