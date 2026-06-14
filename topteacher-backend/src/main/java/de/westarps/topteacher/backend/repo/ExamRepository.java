package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.ExamNumber;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

@Repository
public class ExamRepository {

	private static final String RESULTS_EXIST_MESSAGE = """
			Für diese:n Schüler:in sind bereits Ergebnisse erfasst. Bitte lösche zuerst die Ergebnisse.
			""".trim();

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<Exam> rowMapper = this::mapExam;
	private final RowMapper<Pupil> pupilRowMapper = this::mapPupil;

	public ExamRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Exam> findByCourseId(final int courseId) {
		return jdbc.query("""
				select id, course_id, title, exam_date, original_exam_id, grading_scale_id
				from exam
				where course_id = :courseId
				order by exam_date, id
				""", Map.of("courseId", courseId), rowMapper);
	}

	public Optional<Exam> findById(final int id) {
		return jdbc.query("""
				select id, course_id, title, exam_date, original_exam_id, grading_scale_id
				from exam
				where id = :id
				""", Map.of("id", id), rowMapper).stream().findFirst();
	}

	public List<Exam> findMainExamsByCourseId(final int courseId) {
		return jdbc.query("""
				select id, course_id, title, exam_date, original_exam_id, grading_scale_id
				from exam
				where course_id = :courseId
				  and original_exam_id is null
				order by exam_date, id
				""", Map.of("courseId", courseId), rowMapper);
	}

	public Optional<ExamNumber> findNumberById(final int examId) {
		return findById(examId).map(exam -> findNumbersByCourseId(exam.courseId()).get(exam.id()));
	}

	public Map<Integer, ExamNumber> findNumbersByCourseId(final int courseId) {
		final Map<Integer, ExamNumber> numbersByExamId = new LinkedHashMap<>();
		final RowCallbackHandler numberMapper = resultSet -> numbersByExamId.put(resultSet.getInt("id"),
				new ExamNumber(resultSet.getInt("exam_number"), resultSet.getBoolean("makeup_exam")));
		jdbc.query("""
				with main_exam_numbers as (
				    select e.id,
				           row_number() over (order by e.exam_date, e.id) as exam_number
				    from exam e
				    where e.course_id = :courseId
				      and e.original_exam_id is null
				),
				group_exams as (
				    select e.id,
				           coalesce(main_exam.exam_number, original_exam.exam_number) as exam_number,
				           case when e.original_exam_id is null then false else true end as makeup_exam
				    from exam e
				    left join main_exam_numbers main_exam on main_exam.id = e.id
				    left join main_exam_numbers original_exam on original_exam.id = e.original_exam_id
				    where e.course_id = :courseId
				)
				select id, exam_number, makeup_exam
				from group_exams
				order by id
				""", Map.of("courseId", courseId), numberMapper);
		return numbersByExamId;
	}

	public List<Pupil> findPupils(final int examId) {
		return jdbc.query("""
				select p.id, p.name, p.surname, p.lifecycle
				from pupil p
				join exam_pupil ep on ep.pupil_id = p.id
				where ep.exam_id = :examId
				order by p.surname, p.name, p.id
				""", Map.of("examId", examId), pupilRowMapper);
	}

	public List<Pupil> findAssignablePupils(final int examId) {
		return jdbc.query("""
				select p.id, p.name, p.surname, p.lifecycle
				from pupil p
				join course_pupil cp on cp.pupil_id = p.id
				join exam e on e.course_id = cp.course_id
				where e.id = :examId
				  and not exists (
				      select 1
				      from exam_pupil ep
				      where ep.exam_id = e.id
				        and ep.pupil_id = p.id
				  )
				order by p.surname, p.name, p.id
				""", Map.of("examId", examId), pupilRowMapper);
	}

	public boolean hasPupil(final int examId, final int pupilId) {
		final Integer count = jdbc.queryForObject("""
				select count(*)
				from exam_pupil
				where exam_id = :examId
				  and pupil_id = :pupilId
				""", Map.of("examId", examId, "pupilId", pupilId), Integer.class);
		return count != null && count > 0;
	}

	public Map<Integer, String> findPupilRemovalLocks(final int examId) {
		final Map<Integer, String> locksByPupilId = new LinkedHashMap<>();
		jdbc.query("""
				select ep.pupil_id
				from exam_pupil ep
				where ep.exam_id = :examId
				  and (
				      exists (
				          select 1
				          from eh_requirement_result result
				          join eh_requirement requirement on requirement.id = result.requirement_id
				          join eh_task task on task.id = requirement.task_id
				          join eh_category category on category.id = task.category_id
				          join eh_part part on part.id = category.part_id
				          where part.exam_id = ep.exam_id
				            and result.pupil_id = ep.pupil_id
				      )
				      or exists (
				          select 1
				          from eh_criterion_result result
				          join eh_criterion criterion on criterion.id = result.criterion_id
				          join eh_requirement requirement on requirement.id = criterion.requirement_id
				          join eh_task task on task.id = requirement.task_id
				          join eh_category category on category.id = task.category_id
				          join eh_part part on part.id = category.part_id
				          where part.exam_id = ep.exam_id
				            and result.pupil_id = ep.pupil_id
				      )
				  )
				order by ep.pupil_id
				""", Map.of("examId", examId), (RowCallbackHandler) resultSet -> locksByPupilId
				.put(resultSet.getInt("pupil_id"), RESULTS_EXIST_MESSAGE));
		return locksByPupilId;
	}

	public boolean existsByCourseIdAndTitle(final int courseId, final String title) {
		final Integer count = jdbc.queryForObject("""
				select count(*)
				from exam
				where course_id = :courseId
				  and title = :title
				""", Map.of("courseId", courseId, "title", title), Integer.class);
		return count != null && count > 0;
	}

	@Transactional
	public Exam save(final Exam exam) {
		validateExam(exam);
		if (exam.id() == null) {
			return insert(exam);
		}

		update(exam);
		return exam;
	}

	@Transactional
	public Exam save(final Exam exam, final Collection<Integer> pupilIds) {
		final Exam savedExam = save(exam);
		replacePupils(savedExam.id(), pupilIds);
		return savedExam;
	}

	@Transactional
	public void assignPupil(final int examId, final int pupilId) {
		validatePupilsBelongToCourse(examId, Set.of(pupilId));
		jdbc.update("""
				merge into exam_pupil (exam_id, pupil_id)
				key (exam_id, pupil_id)
				values (:examId, :pupilId)
				""", Map.of("examId", examId, "pupilId", pupilId));
	}

	@Transactional
	public void removePupil(final int examId, final int pupilId) {
		validatePupilCanBeRemoved(examId, pupilId);
		jdbc.update("""
				delete from exam_pupil
				where exam_id = :examId
				  and pupil_id = :pupilId
				""", Map.of("examId", examId, "pupilId", pupilId));
	}

	@Transactional
	public void replacePupils(final int examId, final Collection<Integer> pupilIds) {
		final Set<Integer> uniquePupilIds = new LinkedHashSet<>(pupilIds == null ? List.of() : pupilIds);
		ensureExamExists(examId);
		validatePupilsBelongToCourse(examId, uniquePupilIds);
		validateLockedPupilsRemainAssigned(examId, uniquePupilIds);

		jdbc.update("""
				delete from exam_pupil
				where exam_id = :examId
				""", Map.of("examId", examId));
		if (uniquePupilIds.isEmpty()) {
			return;
		}

		jdbc.update("""
				insert into exam_pupil (exam_id, pupil_id)
				select e.id, cp.pupil_id
				from exam e
				join course_pupil cp on cp.course_id = e.course_id
				where e.id = :examId
				  and cp.pupil_id in (:pupilIds)
				""", Map.of("examId", examId, "pupilIds", uniquePupilIds));
	}

	private Exam insert(final Exam exam) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final Exam completeExam = exam.withGradingScaleId(resolveGradingScaleId(exam));
		final MapSqlParameterSource parameters = parameters(completeExam);

		jdbc.update("""
				insert into exam (course_id, title, exam_date, original_exam_id, grading_scale_id)
				values (:courseId, :title, :date, :originalExamId, :gradingScaleId)
				""", parameters, keyHolder, new String[] { "id" });

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Exam insert did not return a generated id");
		}

		final int examId = id.intValue();
		initializePupilsFromCourse(examId);
		return new Exam(examId, completeExam.courseId(), completeExam.title(), completeExam.date(),
				completeExam.originalExamId(), completeExam.gradingScaleId());
	}

	private void update(final Exam exam) {
		final Exam existingExam = findById(exam.id())
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + exam.id()));
		if (!existingExam.courseId().equals(exam.courseId())) {
			throw new IllegalArgumentException("Exam course can not be changed.");
		}
		if (exam.gradingScaleId() != null && !existingExam.gradingScaleId().equals(exam.gradingScaleId())) {
			throw new IllegalArgumentException(
					"Der Notenschlüssel einer bestehenden Klausur kann nicht geändert werden.");
		}

		jdbc.update("""
				update exam
				set title = :title,
				    exam_date = :date,
				    original_exam_id = :originalExamId
				where id = :id
				""", parameters(exam).addValue("id", exam.id()));
	}

	private MapSqlParameterSource parameters(final Exam exam) {
		return new MapSqlParameterSource().addValue("courseId", exam.courseId()).addValue("title", exam.title())
				.addValue("date", exam.date()).addValue("originalExamId", exam.originalExamId())
				.addValue("gradingScaleId", exam.gradingScaleId());
	}

	private Exam mapExam(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Exam(resultSet.getInt("id"), resultSet.getInt("course_id"), resultSet.getString("title"),
				resultSet.getObject("exam_date", LocalDate.class),
				resultSet.getObject("original_exam_id", Integer.class),
				resultSet.getObject("grading_scale_id", Integer.class));
	}

	private Pupil mapPupil(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new Pupil(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("surname"),
				Lifecycle.valueOf(resultSet.getString("lifecycle")));
	}

	private void validateExam(final Exam exam) {
		if (exam.id() != null && exam.originalExamId() != null && hasMakeupExams(exam.id())) {
			throw new IllegalArgumentException(
					"Eine Klausur mit Nachschreibeklausuren kann nicht selbst als Nachschreibeklausur markiert werden.");
		}
		if (exam.id() != null && exam.originalExamId() == null && hasMakeupExamBefore(exam.id(), exam.date())) {
			throw new IllegalArgumentException(
					"Das Datum der ursprünglichen Klausur darf nicht nach einer Nachschreibeklausur liegen.");
		}
		if (exam.originalExamId() != null) {
			validateOriginalExam(exam);
		}
	}

	private void validateOriginalExam(final Exam exam) {
		final Exam originalExam = findById(exam.originalExamId())
				.orElseThrow(() -> new IllegalArgumentException("Die ursprüngliche Klausur wurde nicht gefunden."));
		if (originalExam.originalExamId() != null) {
			throw new IllegalArgumentException(
					"Eine Nachschreibeklausur kann nicht auf eine andere Nachschreibeklausur verweisen.");
		}
		if (exam.date().isBefore(originalExam.date())) {
			throw new IllegalArgumentException(
					"Das Datum einer Nachschreibeklausur darf nicht vor dem Datum der ursprünglichen Klausur liegen.");
		}
		if (!exam.courseId().equals(originalExam.courseId())) {
			throw new IllegalArgumentException("Eine Nachschreibeklausur muss zum selben Kurs gehören.");
		}
	}

	private Integer resolveGradingScaleId(final Exam exam) {
		if (exam.gradingScaleId() != null) {
			return exam.gradingScaleId();
		}

		final Integer gradingScaleId = jdbc
				.query("""
						select grading_scale_id
						from course
						where id = :courseId
						""", Map.of("courseId", exam.courseId()),
						(resultSet, rowNumber) -> resultSet.getObject("grading_scale_id", Integer.class))
				.stream().findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
		if (gradingScaleId == null) {
			throw new IllegalArgumentException("Für den Kurs wurde kein Notenschlüssel gefunden.");
		}
		return gradingScaleId;
	}

	private boolean hasMakeupExams(final int examId) {
		final Integer count = jdbc.queryForObject("""
				select count(*)
				from exam
				where original_exam_id = :examId
				""", Map.of("examId", examId), Integer.class);
		return count != null && count > 0;
	}

	private boolean hasMakeupExamBefore(final int examId, final LocalDate date) {
		final Integer count = jdbc.queryForObject("""
				select count(*)
				from exam
				where original_exam_id = :examId
				  and exam_date < :date
				""", Map.of("examId", examId, "date", date), Integer.class);
		return count != null && count > 0;
	}

	private void initializePupilsFromCourse(final int examId) {
		jdbc.update("""
				insert into exam_pupil (exam_id, pupil_id)
				select e.id, cp.pupil_id
				from exam e
				join course_pupil cp on cp.course_id = e.course_id
				where e.id = :examId
				  and not exists (
				      select 1
				      from exam_pupil ep
				      where ep.exam_id = e.id
				        and ep.pupil_id = cp.pupil_id
				  )
				""", Map.of("examId", examId));
	}

	private void ensureExamExists(final int examId) {
		final Integer count = jdbc.queryForObject("""
				select count(*)
				from exam
				where id = :examId
				""", Map.of("examId", examId), Integer.class);
		if (count == null || count == 0) {
			throw new IllegalArgumentException("Exam does not exist: " + examId);
		}
	}

	private void validatePupilsBelongToCourse(final int examId, final Set<Integer> pupilIds) {
		if (pupilIds.isEmpty()) {
			return;
		}

		final Integer count = jdbc.queryForObject("""
				select count(distinct cp.pupil_id)
				from exam e
				join course_pupil cp on cp.course_id = e.course_id
				where e.id = :examId
				  and cp.pupil_id in (:pupilIds)
				""", Map.of("examId", examId, "pupilIds", pupilIds), Integer.class);
		if (count == null || count != pupilIds.size()) {
			throw new IllegalArgumentException("Alle teilnehmenden Schüler:innen müssen dem Kurs zugeordnet sein.");
		}
	}

	private void validatePupilCanBeRemoved(final int examId, final int pupilId) {
		if (findPupilRemovalLocks(examId).containsKey(pupilId)) {
			throw new IllegalArgumentException(RESULTS_EXIST_MESSAGE);
		}
	}

	private void validateLockedPupilsRemainAssigned(final int examId, final Set<Integer> pupilIds) {
		if (findPupilRemovalLocks(examId).keySet().stream().anyMatch(pupilId -> !pupilIds.contains(pupilId))) {
			throw new IllegalArgumentException(RESULTS_EXIST_MESSAGE);
		}
	}

}
