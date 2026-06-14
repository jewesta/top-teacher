package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;

@Repository
public class GradingScaleRepository {

	private static final String USED_BY_EXAM_MESSAGE = """
			Dieser Notenschlüssel wird bereits von Klausuren verwendet und kann nicht mehr geändert werden.
			""".trim();

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<GradingScale> gradingScaleRowMapper = this::mapGradingScale;
	private final RowMapper<GradingScaleRange> gradingScaleRangeRowMapper = this::mapGradingScaleRange;

	public GradingScaleRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<GradingScale> findAll() {
		return jdbc.query("""
				select id, name, max_points, lifecycle
				from grading_scale
				order by max_points, name, id
				""", gradingScaleRowMapper);
	}

	public List<GradingScale> findActive() {
		return jdbc.query("""
				select id, name, max_points, lifecycle
				from grading_scale
				where lifecycle = :lifecycle
				order by max_points, name, id
				""", Map.of("lifecycle", Lifecycle.ACTIVE.name()), gradingScaleRowMapper);
	}

	public Optional<GradingScale> findById(final int id) {
		return jdbc.query("""
				select id, name, max_points, lifecycle
				from grading_scale
				where id = :id
				""", Map.of("id", id), gradingScaleRowMapper).stream().findFirst();
	}

	public List<GradingScaleRange> findRangesByGradingScaleId(final int gradingScaleId) {
		return jdbc.query("""
				select id, grading_scale_id, grade_points, min_points, max_points
				from grading_scale_range
				where grading_scale_id = :gradingScaleId
				order by grade_points desc
				""", Map.of("gradingScaleId", gradingScaleId), gradingScaleRangeRowMapper);
	}

	public boolean isUsedByExam(final int gradingScaleId) {
		final Integer count = jdbc.queryForObject("""
				select count(*)
				from exam
				where grading_scale_id = :gradingScaleId
				""", Map.of("gradingScaleId", gradingScaleId), Integer.class);
		return count != null && count > 0;
	}

	public GradingScale save(final GradingScale gradingScale) {
		if (gradingScale.id() == null) {
			return insert(gradingScale);
		}

		update(gradingScale);
		return gradingScale;
	}

	@Transactional
	public GradingScale saveWithRanges(final GradingScale gradingScale, final List<GradingScaleRange> ranges) {
		validateCompleteScale(gradingScale, ranges);
		if (gradingScale.id() != null) {
			validateScaleNotUsed(gradingScale.id());
		}

		final GradingScale saved = save(gradingScale);
		replaceRanges(saved.id(), ranges);
		return saved;
	}

	public GradingScaleRange saveRange(final GradingScaleRange range) {
		validateScaleNotUsed(range.gradingScaleId());
		if (range.id() == null) {
			return insertRange(range);
		}

		updateRange(range);
		return range;
	}

	private GradingScale insert(final GradingScale gradingScale) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = gradingScaleParameters(gradingScale);

		jdbc.update("""
				insert into grading_scale (name, max_points, lifecycle)
				values (:name, :maxPoints, :lifecycle)
				""", parameters, keyHolder, new String[] { "id" });

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Grading scale insert did not return a generated id");
		}

		return new GradingScale(id.intValue(), gradingScale.name(), gradingScale.maxPoints(), gradingScale.lifecycle());
	}

	private void update(final GradingScale gradingScale) {
		validateScaleNotUsed(gradingScale.id());
		jdbc.update("""
				update grading_scale
				set name = :name,
				    max_points = :maxPoints,
				    lifecycle = :lifecycle
				where id = :id
				""", gradingScaleParameters(gradingScale).addValue("id", gradingScale.id()));
	}

	private void replaceRanges(final int gradingScaleId, final List<GradingScaleRange> ranges) {
		jdbc.update("""
				delete from grading_scale_range
				where grading_scale_id = :gradingScaleId
				""", Map.of("gradingScaleId", gradingScaleId));
		ranges.forEach(range -> insertRange(
				new GradingScaleRange(null, gradingScaleId, range.gradeLevel(), range.minPoints(), range.maxPoints())));
	}

	private GradingScaleRange insertRange(final GradingScaleRange range) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		final MapSqlParameterSource parameters = rangeParameters(range);

		jdbc.update("""
				insert into grading_scale_range (grading_scale_id, grade_points, min_points, max_points)
				values (:gradingScaleId, :gradePoints, :minPoints, :maxPoints)
				""", parameters, keyHolder, new String[] { "id" });

		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("Grading scale range insert did not return a generated id");
		}

		return new GradingScaleRange(id.intValue(), range.gradingScaleId(), range.gradeLevel(), range.minPoints(),
				range.maxPoints());
	}

	private void updateRange(final GradingScaleRange range) {
		jdbc.update("""
				update grading_scale_range
				set grading_scale_id = :gradingScaleId,
				    grade_points = :gradePoints,
				    min_points = :minPoints,
				    max_points = :maxPoints
				where id = :id
				""", rangeParameters(range).addValue("id", range.id()));
	}

	private MapSqlParameterSource gradingScaleParameters(final GradingScale gradingScale) {
		return new MapSqlParameterSource().addValue("name", gradingScale.name())
				.addValue("maxPoints", gradingScale.maxPoints()).addValue("lifecycle", gradingScale.lifecycle().name());
	}

	private MapSqlParameterSource rangeParameters(final GradingScaleRange range) {
		return new MapSqlParameterSource().addValue("gradingScaleId", range.gradingScaleId())
				.addValue("gradePoints", range.gradeLevel().getPoints()).addValue("minPoints", range.minPoints())
				.addValue("maxPoints", range.maxPoints());
	}

	private GradingScale mapGradingScale(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new GradingScale(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getInt("max_points"),
				Lifecycle.valueOf(resultSet.getString("lifecycle")));
	}

	private GradingScaleRange mapGradingScaleRange(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new GradingScaleRange(resultSet.getInt("id"), resultSet.getInt("grading_scale_id"),
				GradeLevel.fromPoints(resultSet.getInt("grade_points")), resultSet.getInt("min_points"),
				resultSet.getInt("max_points"));
	}

	private void validateCompleteScale(final GradingScale gradingScale, final List<GradingScaleRange> ranges) {
		if (ranges == null || ranges.size() != GradeLevel.values().length) {
			throw new IllegalArgumentException("Ein Notenschlüssel muss genau 16 Notenpunkte enthalten.");
		}

		final Map<GradeLevel, GradingScaleRange> rangesByGradeLevel = new EnumMap<>(GradeLevel.class);
		for (final GradingScaleRange range : ranges) {
			if (rangesByGradeLevel.put(range.gradeLevel(), range) != null) {
				throw new IllegalArgumentException("Ein Notenschlüssel darf jeden Notenpunkt nur einmal enthalten.");
			}
			if (range.maxPoints() > gradingScale.maxPoints()) {
				throw new IllegalArgumentException("Die Punktebereiche müssen innerhalb der Maximalpunktzahl liegen.");
			}
		}

		if (rangesByGradeLevel.size() != GradeLevel.values().length) {
			throw new IllegalArgumentException("Ein Notenschlüssel muss genau 16 Notenpunkte enthalten.");
		}

		int expectedMinPoints = 0;
		for (int gradePoints = 0; gradePoints <= 15; gradePoints++) {
			final GradingScaleRange range = rangesByGradeLevel.get(GradeLevel.fromPoints(gradePoints));
			if (range.minPoints() != expectedMinPoints) {
				throw new IllegalArgumentException(
						"Die Punktebereiche müssen lückenlos von 0 bis zur Maximalpunktzahl reichen.");
			}
			expectedMinPoints = range.maxPoints() + 1;
		}
		if (expectedMinPoints != gradingScale.maxPoints() + 1) {
			throw new IllegalArgumentException(
					"Die Punktebereiche müssen lückenlos von 0 bis zur Maximalpunktzahl reichen.");
		}
	}

	private void validateScaleNotUsed(final int gradingScaleId) {
		if (isUsedByExam(gradingScaleId)) {
			throw new IllegalArgumentException(USED_BY_EXAM_MESSAGE);
		}
	}
}
