package de.topteacher.backend.pupil;

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

@Repository
public class PupilRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<Pupil> rowMapper = this::mapPupil;

	public PupilRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Pupil> findAll() {
		return jdbc.query("""
				select id, name, surname, lifecycle
				from pupil
				order by surname, name, id
				""", rowMapper);
	}

	public Optional<Pupil> findById(final int id) {
		return jdbc.query("""
				select id, name, surname, lifecycle
				from pupil
				where id = :id
				""", Map.of("id", id), rowMapper).stream().findFirst();
	}

	public Pupil save(final Pupil pupil) {
		if (pupil.id() == null) {
			return insert(pupil);
		}

		update(pupil);
		return pupil;
	}

	public void archive(final int id) {
		jdbc.update("""
				update pupil
				set lifecycle = :lifecycle
				where id = :id
				""", Map.of(
				"id", id,
				"lifecycle", PupilLifecycle.INACTIVE.name()
		));
	}

	private Pupil insert(final Pupil pupil) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("name", pupil.name())
				.addValue("surname", pupil.surname())
				.addValue("lifecycle", pupil.lifecycle().name());

		jdbc.update("""
				insert into pupil (name, surname, lifecycle)
				values (:name, :surname, :lifecycle)
				""", parameters, keyHolder, new String[] {"id"});

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Pupil insert did not return a generated id");
		}

		return new Pupil(id.intValue(), pupil.name(), pupil.surname(), pupil.lifecycle());
	}

	private void update(final Pupil pupil) {
		jdbc.update("""
				update pupil
				set name = :name,
				    surname = :surname,
				    lifecycle = :lifecycle
				where id = :id
				""", Map.of(
				"id", pupil.id(),
				"name", pupil.name(),
				"surname", pupil.surname(),
				"lifecycle", pupil.lifecycle().name()
		));
	}

	private Pupil mapPupil(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Pupil(
				resultSet.getInt("id"),
				resultSet.getString("name"),
				resultSet.getString("surname"),
				PupilLifecycle.valueOf(resultSet.getString("lifecycle"))
		);
	}
}

