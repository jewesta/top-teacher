package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@Repository
public class CourseRepository {

	private static final String PUPIL_ASSIGNED_TO_EXAM_MESSAGE = """
			Diese:r Schüler:in ist bereits einer Klausur zugeordnet und kann nicht aus dem Kurs entfernt werden.
			""".trim();

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<Course> rowMapper = this::mapCourse;
	private final RowMapper<Pupil> pupilRowMapper = this::mapPupil;

	public CourseRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Course> findAll() {
		return jdbc.query("""
				select c.id, c.school_class, s.id as subject_id, s.name as subject_name,
				       s.lifecycle as subject_lifecycle, c.calendar_year, c.course_period, c.lifecycle,
				       c.grading_scale_id
				from course c
				join subject s on s.id = c.subject_id
				order by c.calendar_year desc, c.course_period, c.school_class, s.name, c.id
				""", rowMapper);
	}

	public List<Course> findActive() {
		return jdbc.query("""
				select c.id, c.school_class, s.id as subject_id, s.name as subject_name,
				       s.lifecycle as subject_lifecycle, c.calendar_year, c.course_period, c.lifecycle,
				       c.grading_scale_id
				from course c
				join subject s on s.id = c.subject_id
				where c.lifecycle = :lifecycle
				order by c.calendar_year desc, c.course_period, c.school_class, s.name, c.id
				""", Map.of("lifecycle", Lifecycle.ACTIVE.name()), rowMapper);
	}

	public Optional<Course> findById(final int id) {
		return jdbc.query("""
				select c.id, c.school_class, s.id as subject_id, s.name as subject_name,
				       s.lifecycle as subject_lifecycle, c.calendar_year, c.course_period, c.lifecycle,
				       c.grading_scale_id
				from course c
				join subject s on s.id = c.subject_id
				where c.id = :id
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

	public Map<Integer, String> findPupilRemovalLocks(final int courseId) {
		final Map<Integer, String> locksByPupilId = new LinkedHashMap<>();
		jdbc.query("""
				select distinct ep.pupil_id
				from exam e
				join exam_pupil ep on ep.exam_id = e.id
				where e.course_id = :courseId
				order by ep.pupil_id
				""", Map.of("courseId", courseId), (RowCallbackHandler) resultSet -> locksByPupilId
				.put(resultSet.getInt("pupil_id"), PUPIL_ASSIGNED_TO_EXAM_MESSAGE));
		return locksByPupilId;
	}

	public void assignPupil(final int courseId, final int pupilId) {
		jdbc.update("""
				merge into course_pupil (course_id, pupil_id)
				key (course_id, pupil_id)
				values (:courseId, :pupilId)
				""", Map.of("courseId", courseId, "pupilId", pupilId));
	}

	public void removePupil(final int courseId, final int pupilId) {
		validatePupilCanBeRemoved(courseId, pupilId);
		jdbc.update("""
				delete from course_pupil
				where course_id = :courseId
				  and pupil_id = :pupilId
				""", Map.of("courseId", courseId, "pupilId", pupilId));
	}

	@Transactional
	public void replacePupilsFromCourse(final int targetCourseId, final int sourceCourseId) {
		if (targetCourseId == sourceCourseId) {
			return;
		}

		final Map<String, Integer> parameters = Map.of("targetCourseId", targetCourseId, "sourceCourseId",
				sourceCourseId);
		validateReplacementKeepsExamPupils(parameters);
		jdbc.update("""
				delete from course_pupil
				where course_id = :targetCourseId
				""", parameters);
		jdbc.update("""
				insert into course_pupil (course_id, pupil_id)
				select :targetCourseId, pupil_id
				from course_pupil
				where course_id = :sourceCourseId
				""", parameters);
	}

	private Course insert(final Course course) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = parameters(course);

		jdbc.update("""
				insert into course (school_class, subject_id, calendar_year, course_period, lifecycle, grading_scale_id)
				values (:schoolClass, :subjectId, :calendarYear, :coursePeriod, :lifecycle, :gradingScaleId)
				""", parameters, keyHolder, new String[] { "id" });

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Course insert did not return a generated id");
		}

		return new Course(id.intValue(), course.schoolClass(), course.subject(), course.schoolYear(),
				course.coursePeriod(), course.lifecycle(), course.gradingScaleId());
	}

	private void update(final Course course) {
		final MapSqlParameterSource parameters = parameters(course).addValue("id", course.id());

		jdbc.update("""
				update course
				set school_class = :schoolClass,
				    subject_id = :subjectId,
				    calendar_year = :calendarYear,
				    course_period = :coursePeriod,
				    lifecycle = :lifecycle,
				    grading_scale_id = :gradingScaleId
				where id = :id
				""", parameters);
	}

	private MapSqlParameterSource parameters(final Course course) {
		return new MapSqlParameterSource().addValue("schoolClass", course.schoolClass().name())
				.addValue("subjectId", course.subject().id())
				.addValue("calendarYear", course.schoolYear().getCalendarYear())
				.addValue("coursePeriod", course.coursePeriod().name()).addValue("lifecycle", course.lifecycle().name())
				.addValue("gradingScaleId", course.gradingScaleId());
	}

	private Course mapCourse(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Course(resultSet.getInt("id"), SchoolClass.valueOf(resultSet.getString("school_class")),
				new Subject(resultSet.getInt("subject_id"), resultSet.getString("subject_name"),
						Lifecycle.valueOf(resultSet.getString("subject_lifecycle"))),
				new SchoolYear(resultSet.getInt("calendar_year")),
				CoursePeriod.valueOf(resultSet.getString("course_period")),
				Lifecycle.valueOf(resultSet.getString("lifecycle")),
				resultSet.getObject("grading_scale_id", Integer.class));
	}

	private Pupil mapPupil(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Pupil(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("surname"),
				Lifecycle.valueOf(resultSet.getString("lifecycle")));
	}

	private void validatePupilCanBeRemoved(final int courseId, final int pupilId) {
		if (findPupilRemovalLocks(courseId).containsKey(pupilId)) {
			throw new IllegalArgumentException(PUPIL_ASSIGNED_TO_EXAM_MESSAGE);
		}
	}

	private void validateReplacementKeepsExamPupils(final Map<String, Integer> parameters) {
		final Integer removedExamPupilCount = jdbc.queryForObject("""
				select count(distinct ep.pupil_id)
				from exam e
				join exam_pupil ep on ep.exam_id = e.id
				where e.course_id = :targetCourseId
				  and not exists (
				      select 1
				      from course_pupil source_cp
				      where source_cp.course_id = :sourceCourseId
				        and source_cp.pupil_id = ep.pupil_id
				  )
				""", parameters, Integer.class);
		if (removedExamPupilCount != null && removedExamPupilCount > 0) {
			throw new IllegalArgumentException(PUPIL_ASSIGNED_TO_EXAM_MESSAGE);
		}
	}
}
