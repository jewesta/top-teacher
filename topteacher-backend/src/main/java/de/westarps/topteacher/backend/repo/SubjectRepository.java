package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Subject;

@Repository
public class SubjectRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<Subject> rowMapper = this::mapSubject;

	public SubjectRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Subject> findAll() {
		return jdbc.query("""
				select id, name, lifecycle
				from subject
				order by name, id
				""", rowMapper);
	}

	public List<Subject> findActive() {
		return jdbc.query("""
				select id, name, lifecycle
				from subject
				where lifecycle = :lifecycle
				order by name, id
				""", Map.of("lifecycle", Lifecycle.ACTIVE.name()), rowMapper);
	}

	public Optional<Subject> findById(final int id) {
		return jdbc.query("""
				select id, name, lifecycle
				from subject
				where id = :id
				""", Map.of("id", id), rowMapper).stream().findFirst();
	}

	public Subject save(final Subject subject) {
		if (subject.id() == null) {
			return insert(subject);
		}

		update(subject);
		return subject;
	}

	public void archive(final int id) {
		jdbc.update("""
				update subject
				set lifecycle = :lifecycle
				where id = :id
				""", Map.of("id", id, "lifecycle", Lifecycle.INACTIVE.name()));
	}

	private Subject insert(final Subject subject) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = parameters(subject);

		jdbc.update("""
				insert into subject (name, lifecycle)
				values (:name, :lifecycle)
				""", parameters, keyHolder, new String[] { "id" });

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Subject insert did not return a generated id");
		}

		return new Subject(id.intValue(), subject.name(), subject.lifecycle());
	}

	private void update(final Subject subject) {
		jdbc.update("""
				update subject
				set name = :name,
				    lifecycle = :lifecycle
				where id = :id
				""", parameters(subject).addValue("id", subject.id()));
	}

	private MapSqlParameterSource parameters(final Subject subject) {
		return new MapSqlParameterSource().addValue("name", subject.name()).addValue("lifecycle",
				subject.lifecycle().name());
	}

	private Subject mapSubject(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Subject(resultSet.getInt("id"), resultSet.getString("name"),
				Lifecycle.valueOf(resultSet.getString("lifecycle")));
	}
}
