package de.topteacher.backend.repo;

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

import de.topteacher.model.Course;
import de.topteacher.model.Half;
import de.topteacher.model.Lifecycle;
import de.topteacher.model.Pupil;
import de.topteacher.model.SchoolClass;
import de.topteacher.model.SchoolYear;
import de.topteacher.model.Subject;
import de.topteacher.model.Term;

@Repository
public class CourseRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<Course> rowMapper = this::mapCourse;
	private final RowMapper<Pupil> pupilRowMapper = this::mapPupil;

	public CourseRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Course> findAll() {
		return jdbc.query("""
				select id, school_class, subject, calendar_year, half, lifecycle
				from course
				order by calendar_year desc, half, school_class, subject, id
				""", rowMapper);
	}

	public Optional<Course> findById(final int id) {
		return jdbc.query("""
				select id, school_class, subject, calendar_year, half, lifecycle
				from course
				where id = :id
				""", Map.of("id", id), rowMapper).stream().findFirst();
	}

	public Course save(final Course course) {
		if (course.id() == null) {
			return insert(course);
		}

		update(course);
		return course;
	}

	public void archive(final int id) {
		jdbc.update("""
				update course
				set lifecycle = :lifecycle
				where id = :id
				""", Map.of("id", id, "lifecycle", Lifecycle.INACTIVE.name()));
	}

	public List<Pupil> findPupils(final int courseId) {
		return jdbc.query("""
				select p.id, p.name, p.surname, p.lifecycle
				from pupil p
				join course_pupil cp on cp.pupil_id = p.id
				where cp.course_id = :courseId
				order by p.surname, p.name, p.id
				""", Map.of("courseId", courseId), pupilRowMapper);
	}

	public List<Pupil> findAssignablePupils(final int courseId) {
		return jdbc.query("""
				select p.id, p.name, p.surname, p.lifecycle
				from pupil p
				where p.lifecycle = :lifecycle
				  and exists (
				      select 1
				      from course c
				      where c.id = :courseId
				  )
				  and not exists (
				      select 1
				      from course_pupil cp
				      where cp.course_id = :courseId
				        and cp.pupil_id = p.id
				  )
				order by p.surname, p.name, p.id
				""", Map.of("courseId", courseId, "lifecycle", Lifecycle.ACTIVE.name()), pupilRowMapper);
	}

	public void assignPupil(final int courseId, final int pupilId) {
		jdbc.update("""
				merge into course_pupil (course_id, pupil_id)
				key (course_id, pupil_id)
				values (:courseId, :pupilId)
				""", Map.of("courseId", courseId, "pupilId", pupilId));
	}

	public void removePupil(final int courseId, final int pupilId) {
		jdbc.update("""
				delete from course_pupil
				where course_id = :courseId
				  and pupil_id = :pupilId
				""", Map.of("courseId", courseId, "pupilId", pupilId));
	}

	private Course insert(final Course course) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = parameters(course);

		jdbc.update("""
				insert into course (school_class, subject, calendar_year, half, lifecycle)
				values (:schoolClass, :subject, :calendarYear, :half, :lifecycle)
				""", parameters, keyHolder, new String[] { "id" });

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Course insert did not return a generated id");
		}

		return new Course(id.intValue(), course.schoolClass(), course.subject(), course.term(), course.lifecycle());
	}

	private void update(final Course course) {
		final MapSqlParameterSource parameters = parameters(course).addValue("id", course.id());

		jdbc.update("""
				update course
				set school_class = :schoolClass,
				    subject = :subject,
				    calendar_year = :calendarYear,
				    half = :half,
				    lifecycle = :lifecycle
				where id = :id
				""", parameters);
	}

	private MapSqlParameterSource parameters(final Course course) {
		return new MapSqlParameterSource().addValue("schoolClass", course.schoolClass().name())
				.addValue("subject", course.subject().name())
				.addValue("calendarYear", course.term().getSchoolYear().getCalendarYear())
				.addValue("half", course.term().getHalf().name()).addValue("lifecycle", course.lifecycle().name());
	}

	private Course mapCourse(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Course(resultSet.getInt("id"), SchoolClass.valueOf(resultSet.getString("school_class")),
				Subject.valueOf(resultSet.getString("subject")),
				new Term(new SchoolYear(resultSet.getInt("calendar_year")), Half.valueOf(resultSet.getString("half"))),
				Lifecycle.valueOf(resultSet.getString("lifecycle")));
	}

	private Pupil mapPupil(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Pupil(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("surname"),
				Lifecycle.valueOf(resultSet.getString("lifecycle")));
	}
}
