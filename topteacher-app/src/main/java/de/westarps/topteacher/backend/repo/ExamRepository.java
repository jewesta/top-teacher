package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import de.westarps.topteacher.model.Exam;

@Repository
public class ExamRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<Exam> rowMapper = this::mapExam;

	public ExamRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Exam> findByCourseId(final int courseId) {
		return jdbc.query("""
				select id, course_id, title, exam_date, max_points
				from exam
				where course_id = :courseId
				order by exam_date, title, id
				""", Map.of("courseId", courseId), rowMapper);
	}

	public Optional<Exam> findById(final int id) {
		return jdbc.query("""
				select id, course_id, title, exam_date, max_points
				from exam
				where id = :id
				""", Map.of("id", id), rowMapper).stream().findFirst();
	}

	public Exam save(final Exam exam) {
		if (exam.id() == null) {
			return insert(exam);
		}

		update(exam);
		return exam;
	}

	private Exam insert(final Exam exam) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = parameters(exam);

		jdbc.update("""
				insert into exam (course_id, title, exam_date, max_points)
				values (:courseId, :title, :date, :maxPoints)
				""", parameters, keyHolder, new String[] { "id" });

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Exam insert did not return a generated id");
		}

		return new Exam(id.intValue(), exam.courseId(), exam.title(), exam.date(), exam.maxPoints());
	}

	private void update(final Exam exam) {
		final Exam existingExam = findById(exam.id())
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + exam.id()));
		if (!existingExam.courseId().equals(exam.courseId())) {
			throw new IllegalArgumentException("Exam course can not be changed.");
		}

		jdbc.update("""
				update exam
				set title = :title,
				    exam_date = :date,
				    max_points = :maxPoints
				where id = :id
				""", parameters(exam).addValue("id", exam.id()));
	}

	private MapSqlParameterSource parameters(final Exam exam) {
		return new MapSqlParameterSource().addValue("courseId", exam.courseId()).addValue("title", exam.title())
				.addValue("date", exam.date()).addValue("maxPoints", exam.maxPoints());
	}

	private Exam mapExam(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Exam(resultSet.getInt("id"), resultSet.getInt("course_id"), resultSet.getString("title"),
				resultSet.getObject("exam_date", LocalDate.class), resultSet.getInt("max_points"));
	}
}
