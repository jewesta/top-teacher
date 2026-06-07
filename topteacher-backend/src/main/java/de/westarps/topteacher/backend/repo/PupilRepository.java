package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;

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

	public Map<Integer, SchoolClass> findLatestSchoolClassByPupilId() {
		final Map<Integer, SchoolClass> schoolClassesByPupilId = new HashMap<>();
		final RowCallbackHandler collectSchoolClasses = resultSet -> schoolClassesByPupilId.put(
				resultSet.getInt("pupil_id"), SchoolClass.valueOf(resultSet.getString("school_class")));
		jdbc.query("""
				select pupil_id, school_class
				from (
				    select cp.pupil_id, c.school_class,
				           row_number() over (
				               partition by cp.pupil_id
				               order by c.calendar_year desc, c.id desc
				           ) as row_num
				    from course_pupil cp
				    join course c on c.id = cp.course_id
				)
				where row_num = 1
				""", collectSchoolClasses);
		return schoolClassesByPupilId;
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
				""", Map.of("id", id, "lifecycle", Lifecycle.INACTIVE.name()));
	}

	private Pupil insert(final Pupil pupil) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("name", pupil.name())
				.addValue("surname", pupil.surname()).addValue("lifecycle", pupil.lifecycle().name());

		jdbc.update("""
				insert into pupil (name, surname, lifecycle)
				values (:name, :surname, :lifecycle)
				""", parameters, keyHolder, new String[] { "id" });

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
				""", Map.of("id", pupil.id(), "name", pupil.name(), "surname", pupil.surname(), "lifecycle",
				pupil.lifecycle().name()));
	}

	private Pupil mapPupil(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Pupil(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("surname"),
				Lifecycle.valueOf(resultSet.getString("lifecycle")));
	}
}
