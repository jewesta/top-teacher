package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.model.loe.ExamNoteSection;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionParser;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@Repository
public class LevelOfExpectationsRepository {

	private static final String CORRECTION_MODE_MESSAGE = "Der Erwartungshorizont ist im Korrekturmodus. Struktur, Punkte und Kriteriennummern können nicht geändert werden.";

	private final NamedParameterJdbcTemplate jdbc;
	private final RowMapper<LoePart> partRowMapper = this::mapPart;
	private final RowMapper<LoeCategory> categoryRowMapper = this::mapCategory;
	private final RowMapper<LoeTask> taskRowMapper = this::mapTask;
	private final RowMapper<LoeRequirement> requirementRowMapper = this::mapRequirement;
	private final RowMapper<LoeCriterion> criterionRowMapper = this::mapCriterion;
	private final RowMapper<LoeCriterionResult> criterionResultRowMapper = this::mapCriterionResult;
	private final RowMapper<LoeRequirementResult> requirementResultRowMapper = this::mapRequirementResult;
	private final RowMapper<ExamNoteSection> noteSectionRowMapper = this::mapNoteSection;

	public LevelOfExpectationsRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<LoePart> findPartsByExamId(final int examId) {
		return jdbc.query("""
				select id, exam_id, title, sort_order
				from eh_part
				where exam_id = :examId
				order by sort_order, id
				""", Map.of("examId", examId), partRowMapper);
	}

	public List<LoeCategory> findCategoriesByExamId(final int examId) {
		return jdbc.query("""
				select c.id, c.part_id, c.title, c.description_markdown, c.sort_order
				from eh_category c
				join eh_part p on p.id = c.part_id
				where p.exam_id = :examId
				order by p.sort_order, p.id, c.sort_order, c.id
				""", Map.of("examId", examId), categoryRowMapper);
	}

	public List<LoeTask> findTasksByExamId(final int examId) {
		return jdbc.query("""
				select t.id, t.category_id, t.title, t.sort_order
				from eh_task t
				join eh_category c on c.id = t.category_id
				join eh_part p on p.id = c.part_id
				where p.exam_id = :examId
				order by p.sort_order, p.id, c.sort_order, c.id, t.sort_order, t.id
				""", Map.of("examId", examId), taskRowMapper);
	}

	public List<LoeRequirement> findRequirementsByExamId(final int examId) {
		return jdbc.query("""
				select r.id, r.task_id, r.description_markdown, r.max_points, r.bonus, r.sort_order
				from eh_requirement r
				join eh_task t on t.id = r.task_id
				join eh_category c on c.id = t.category_id
				join eh_part p on p.id = c.part_id
				where p.exam_id = :examId
				order by p.sort_order, p.id, c.sort_order, c.id, t.sort_order, t.id, r.sort_order, r.id
				""", Map.of("examId", examId), requirementRowMapper);
	}

	public List<LoeCriterion> findActiveCriteriaByExamId(final int examId) {
		return jdbc.query("""
				select cr.id, cr.requirement_id, cr.criterion_key, cr.label, cr.sort_order, cr.active
				from eh_criterion cr
				join eh_requirement r on r.id = cr.requirement_id
				join eh_task t on t.id = r.task_id
				join eh_category c on c.id = t.category_id
				join eh_part p on p.id = c.part_id
				where p.exam_id = :examId
				  and cr.active = true
				order by p.sort_order, p.id, c.sort_order, c.id, t.sort_order, t.id, r.sort_order, r.id,
				         cr.sort_order, cr.id
				""", Map.of("examId", examId), criterionRowMapper);
	}

	public List<LoeCriterionResult> findCriterionResultsByExamAndPupil(final int examId, final int pupilId) {
		return jdbc.query("""
				select result.criterion_id, result.pupil_id, result.achieved
				from eh_criterion_result result
				join eh_criterion cr on cr.id = result.criterion_id
				join eh_requirement r on r.id = cr.requirement_id
				join eh_task t on t.id = r.task_id
				join eh_category c on c.id = t.category_id
				join eh_part p on p.id = c.part_id
				where p.exam_id = :examId
				  and result.pupil_id = :pupilId
				""", Map.of("examId", examId, "pupilId", pupilId), criterionResultRowMapper);
	}

	public List<LoeRequirementResult> findRequirementResultsByExamAndPupil(final int examId, final int pupilId) {
		return jdbc.query("""
				select result.requirement_id, result.pupil_id, result.points, result.comment_text
				from eh_requirement_result result
				join eh_requirement r on r.id = result.requirement_id
				join eh_task t on t.id = r.task_id
				join eh_category c on c.id = t.category_id
				join eh_part p on p.id = c.part_id
				where p.exam_id = :examId
				  and result.pupil_id = :pupilId
				""", Map.of("examId", examId, "pupilId", pupilId), requirementResultRowMapper);
	}

	public List<ExamNoteSection> findNoteSectionsByExamId(final int examId) {
		return jdbc.query("""
				select id, exam_id, title, description_markdown, sort_order
				from exam_note_section
				where exam_id = :examId
				order by sort_order, id
				""", Map.of("examId", examId), noteSectionRowMapper);
	}

	public boolean hasResultsForExam(final int examId) {
		final Integer count = jdbc.queryForObject("""
				select count(*)
				from (
				    select 1
				    from eh_requirement_result result
				    join eh_requirement r on r.id = result.requirement_id
				    join eh_task t on t.id = r.task_id
				    join eh_category c on c.id = t.category_id
				    join eh_part p on p.id = c.part_id
				    where p.exam_id = :examId
				    union all
				    select 1
				    from eh_criterion_result result
				    join eh_criterion cr on cr.id = result.criterion_id
				    join eh_requirement r on r.id = cr.requirement_id
				    join eh_task t on t.id = r.task_id
				    join eh_category c on c.id = t.category_id
				    join eh_part p on p.id = c.part_id
				    where p.exam_id = :examId
				) results
				""", Map.of("examId", examId), Integer.class);
		return count != null && count > 0;
	}

	@Transactional
	public void copyDesignAndNotes(final int sourceExamId, final int targetExamId) {
		final Map<Integer, Integer> partIds = new HashMap<>();
		final Map<Integer, Integer> categoryIds = new HashMap<>();
		final Map<Integer, Integer> taskIds = new HashMap<>();

		for (final LoePart part : findPartsByExamId(sourceExamId)) {
			final LoePart copiedPart = savePart(new LoePart(null, targetExamId, part.title(), part.sortOrder()));
			partIds.put(part.id(), copiedPart.id());
		}

		for (final LoeCategory category : findCategoriesByExamId(sourceExamId)) {
			final LoeCategory copiedCategory = saveCategory(new LoeCategory(null, partIds.get(category.partId()),
					category.title(), category.descriptionMarkdown(), category.sortOrder()));
			categoryIds.put(category.id(), copiedCategory.id());
		}

		for (final LoeTask task : findTasksByExamId(sourceExamId)) {
			final LoeTask copiedTask = saveTask(
					new LoeTask(null, categoryIds.get(task.categoryId()), task.title(), task.sortOrder()));
			taskIds.put(task.id(), copiedTask.id());
		}

		for (final LoeRequirement requirement : findRequirementsByExamId(sourceExamId)) {
			saveRequirement(
					new LoeRequirement(null, taskIds.get(requirement.taskId()), requirement.descriptionMarkdown(),
							requirement.maxPoints(), requirement.bonus(), requirement.sortOrder()));
		}

		for (final ExamNoteSection noteSection : findNoteSectionsByExamId(sourceExamId)) {
			saveNoteSection(new ExamNoteSection(null, targetExamId, noteSection.title(),
					noteSection.descriptionMarkdown(), noteSection.sortOrder()));
		}
	}

	public LoePart savePart(final LoePart part) {
		if (part.id() == null) {
			assertNoResultsForExam(part.examId());
			return insertPart(part);
		}
		validatePartCorrectionMode(part);
		jdbc.update("""
				update eh_part
				set title = :title,
				    sort_order = :sortOrder
				where id = :id
				""", new MapSqlParameterSource().addValue("id", part.id()).addValue("title", part.title())
				.addValue("sortOrder", part.sortOrder()));
		return part;
	}

	public LoeCategory saveCategory(final LoeCategory category) {
		if (category.id() == null) {
			assertNoResultsForExam(examIdForPart(category.partId()));
			return insertCategory(category);
		}
		validateCategoryCorrectionMode(category);
		jdbc.update("""
				update eh_category
				set title = :title,
				    description_markdown = :descriptionMarkdown,
				    sort_order = :sortOrder
				where id = :id
				""",
				new MapSqlParameterSource().addValue("id", category.id()).addValue("title", category.title())
						.addValue("descriptionMarkdown", category.descriptionMarkdown())
						.addValue("sortOrder", category.sortOrder()));
		return category;
	}

	public LoeTask saveTask(final LoeTask task) {
		if (task.id() == null) {
			assertNoResultsForExam(examIdForCategory(task.categoryId()));
			return insertTask(task);
		}
		validateTaskCorrectionMode(task);
		jdbc.update("""
				update eh_task
				set title = :title,
				    sort_order = :sortOrder
				where id = :id
				""", new MapSqlParameterSource().addValue("id", task.id()).addValue("title", task.title())
				.addValue("sortOrder", task.sortOrder()));
		return task;
	}

	public LoeRequirement saveRequirement(final LoeRequirement requirement) {
		if (requirement.id() == null) {
			assertNoResultsForExam(examIdForTask(requirement.taskId()));
			final LoeRequirement insertedRequirement = insertRequirement(requirement);
			syncCriteria(insertedRequirement);
			return insertedRequirement;
		}
		validateRequirementCorrectionMode(requirement);
		jdbc.update("""
				update eh_requirement
				set description_markdown = :descriptionMarkdown,
				    max_points = :maxPoints,
				    bonus = :bonus,
				    sort_order = :sortOrder
				where id = :id
				""",
				new MapSqlParameterSource().addValue("id", requirement.id())
						.addValue("descriptionMarkdown", requirement.descriptionMarkdown())
						.addValue("maxPoints", requirement.maxPoints()).addValue("bonus", requirement.bonus())
						.addValue("sortOrder", requirement.sortOrder()));
		syncCriteria(requirement);
		return requirement;
	}

	public void syncCriteriaForExam(final int examId) {
		findRequirementsByExamId(examId).forEach(this::syncCriteria);
	}

	public void saveCriterionResult(final LoeCriterionResult result) {
		jdbc.update("""
				merge into eh_criterion_result (criterion_id, pupil_id, achieved)
				key (criterion_id, pupil_id)
				values (:criterionId, :pupilId, :achieved)
				""", new MapSqlParameterSource().addValue("criterionId", result.criterionId())
				.addValue("pupilId", result.pupilId()).addValue("achieved", result.achieved()));
	}

	public void saveRequirementResult(final LoeRequirementResult result) {
		jdbc.update("""
				merge into eh_requirement_result (requirement_id, pupil_id, points, comment_text)
				key (requirement_id, pupil_id)
				values (:requirementId, :pupilId, :points, :comment)
				""",
				new MapSqlParameterSource().addValue("requirementId", result.requirementId())
						.addValue("pupilId", result.pupilId()).addValue("points", result.points())
						.addValue("comment", result.comment()));
	}

	@Transactional
	public void deleteResultsByExamAndPupil(final int examId, final int pupilId) {
		final MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("examId", examId)
				.addValue("pupilId", pupilId);
		jdbc.update("""
				delete from eh_criterion_result
				where pupil_id = :pupilId
				  and criterion_id in (
				      select cr.id
				      from eh_criterion cr
				      join eh_requirement r on r.id = cr.requirement_id
				      join eh_task t on t.id = r.task_id
				      join eh_category c on c.id = t.category_id
				      join eh_part p on p.id = c.part_id
				      where p.exam_id = :examId
				  )
				""", parameters);
		jdbc.update("""
				delete from eh_requirement_result
				where pupil_id = :pupilId
				  and requirement_id in (
				      select r.id
				      from eh_requirement r
				      join eh_task t on t.id = r.task_id
				      join eh_category c on c.id = t.category_id
				      join eh_part p on p.id = c.part_id
				      where p.exam_id = :examId
				  )
				""", parameters);
	}

	public ExamNoteSection saveNoteSection(final ExamNoteSection noteSection) {
		if (noteSection.id() == null) {
			return insertNoteSection(noteSection);
		}
		jdbc.update("""
				update exam_note_section
				set title = :title,
				    description_markdown = :descriptionMarkdown,
				    sort_order = :sortOrder
				where id = :id
				""",
				new MapSqlParameterSource().addValue("id", noteSection.id()).addValue("title", noteSection.title())
						.addValue("descriptionMarkdown", noteSection.descriptionMarkdown())
						.addValue("sortOrder", noteSection.sortOrder()));
		return noteSection;
	}

	public void deletePart(final int id) {
		assertNoResultsForExam(examIdForPart(id));
		assertNoCriterionResultsForPart(id);
		assertNoRequirementResultsForPart(id);
		deleteById("eh_part", id);
	}

	public void deleteCategory(final int id) {
		assertNoResultsForExam(examIdForCategory(id));
		assertNoCriterionResultsForCategory(id);
		assertNoRequirementResultsForCategory(id);
		deleteById("eh_category", id);
	}

	public void deleteTask(final int id) {
		assertNoResultsForExam(examIdForTask(id));
		assertNoCriterionResultsForTask(id);
		assertNoRequirementResultsForTask(id);
		deleteById("eh_task", id);
	}

	public void deleteRequirement(final int id) {
		assertNoResultsForExam(examIdForRequirement(id));
		assertNoCriterionResultsForRequirement(id);
		assertNoRequirementResultsForRequirement(id);
		deleteById("eh_requirement", id);
	}

	public void deleteNoteSection(final int id) {
		deleteById("exam_note_section", id);
	}

	public int nextPartSortOrder(final int examId) {
		return nextSortOrder("""
				select coalesce(max(sort_order), -1) + 1
				from eh_part
				where exam_id = :parentId
				""", examId);
	}

	public int nextCategorySortOrder(final int partId) {
		return nextSortOrder("""
				select coalesce(max(sort_order), -1) + 1
				from eh_category
				where part_id = :parentId
				""", partId);
	}

	public int nextTaskSortOrder(final int categoryId) {
		return nextSortOrder("""
				select coalesce(max(sort_order), -1) + 1
				from eh_task
				where category_id = :parentId
				""", categoryId);
	}

	public int nextRequirementSortOrder(final int taskId) {
		return nextSortOrder("""
				select coalesce(max(sort_order), -1) + 1
				from eh_requirement
				where task_id = :parentId
				""", taskId);
	}

	public int nextNoteSectionSortOrder(final int examId) {
		return nextSortOrder("""
				select coalesce(max(sort_order), -1) + 1
				from exam_note_section
				where exam_id = :parentId
				""", examId);
	}

	public void movePart(final LoePart part, final int offset) {
		assertNoResultsForExam(part.examId());
		final List<LoePart> siblings = findPartsByExamId(part.examId());
		move("eh_part", siblings.stream().map(SortableItem::fromPart).toList(), part.id(), offset);
	}

	public void moveCategory(final LoeCategory category, final List<LoeCategory> siblings, final int offset) {
		assertNoResultsForExam(examIdForCategory(category.id()));
		move("eh_category", siblings.stream().map(SortableItem::fromCategory).toList(), category.id(), offset);
	}

	public void moveTask(final LoeTask task, final List<LoeTask> siblings, final int offset) {
		assertNoResultsForExam(examIdForTask(task.id()));
		move("eh_task", siblings.stream().map(SortableItem::fromTask).toList(), task.id(), offset);
	}

	public void moveRequirement(final LoeRequirement requirement, final List<LoeRequirement> siblings,
			final int offset) {
		assertNoResultsForExam(examIdForRequirement(requirement.id()));
		move("eh_requirement", siblings.stream().map(SortableItem::fromRequirement).toList(), requirement.id(), offset);
	}

	public void moveNoteSection(final ExamNoteSection noteSection, final int offset) {
		final List<ExamNoteSection> siblings = findNoteSectionsByExamId(noteSection.examId());
		move("exam_note_section", siblings.stream().map(SortableItem::fromNoteSection).toList(), noteSection.id(),
				offset);
	}

	private void validatePartCorrectionMode(final LoePart part) {
		final LoePart existing = findPartById(part.id());
		if (!hasResultsForExam(existing.examId())) {
			return;
		}
		if (!existing.examId().equals(part.examId()) || existing.sortOrder() != part.sortOrder()) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private void validateCategoryCorrectionMode(final LoeCategory category) {
		final LoeCategory existing = findCategoryById(category.id());
		final int examId = examIdForPart(existing.partId());
		if (!hasResultsForExam(examId)) {
			return;
		}
		if (!existing.partId().equals(category.partId()) || existing.sortOrder() != category.sortOrder()) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private void validateTaskCorrectionMode(final LoeTask task) {
		final LoeTask existing = findTaskById(task.id());
		final int examId = examIdForCategory(existing.categoryId());
		if (!hasResultsForExam(examId)) {
			return;
		}
		if (!existing.categoryId().equals(task.categoryId()) || existing.sortOrder() != task.sortOrder()) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private void validateRequirementCorrectionMode(final LoeRequirement requirement) {
		final LoeRequirement existing = findRequirementById(requirement.id());
		final int examId = examIdForTask(existing.taskId());
		if (!hasResultsForExam(examId)) {
			return;
		}
		if (!existing.taskId().equals(requirement.taskId()) || existing.maxPoints() != requirement.maxPoints()
				|| existing.bonus() != requirement.bonus() || existing.sortOrder() != requirement.sortOrder()
				|| !criterionKeys(existing).equals(criterionKeys(requirement))) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private List<String> criterionKeys(final LoeRequirement requirement) {
		return LoeCriterionParser.parse(requirement.id(), requirement.descriptionMarkdown()).stream()
				.map(LoeCriterion::criterionKey).toList();
	}

	private void assertNoResultsForExam(final int examId) {
		if (hasResultsForExam(examId)) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private LoePart findPartById(final int id) {
		return jdbc.queryForObject("""
				select id, exam_id, title, sort_order
				from eh_part
				where id = :id
				""", Map.of("id", id), partRowMapper);
	}

	private LoeCategory findCategoryById(final int id) {
		return jdbc.queryForObject("""
				select id, part_id, title, description_markdown, sort_order
				from eh_category
				where id = :id
				""", Map.of("id", id), categoryRowMapper);
	}

	private LoeTask findTaskById(final int id) {
		return jdbc.queryForObject("""
				select id, category_id, title, sort_order
				from eh_task
				where id = :id
				""", Map.of("id", id), taskRowMapper);
	}

	private LoeRequirement findRequirementById(final int id) {
		return jdbc.queryForObject("""
				select id, task_id, description_markdown, max_points, bonus, sort_order
				from eh_requirement
				where id = :id
				""", Map.of("id", id), requirementRowMapper);
	}

	private int examIdForPart(final int partId) {
		return jdbc.queryForObject("""
				select exam_id
				from eh_part
				where id = :id
				""", Map.of("id", partId), Integer.class);
	}

	private int examIdForCategory(final int categoryId) {
		return jdbc.queryForObject("""
				select p.exam_id
				from eh_category c
				join eh_part p on p.id = c.part_id
				where c.id = :id
				""", Map.of("id", categoryId), Integer.class);
	}

	private int examIdForTask(final int taskId) {
		return jdbc.queryForObject("""
				select p.exam_id
				from eh_task t
				join eh_category c on c.id = t.category_id
				join eh_part p on p.id = c.part_id
				where t.id = :id
				""", Map.of("id", taskId), Integer.class);
	}

	private int examIdForRequirement(final int requirementId) {
		return jdbc.queryForObject("""
				select p.exam_id
				from eh_requirement r
				join eh_task t on t.id = r.task_id
				join eh_category c on c.id = t.category_id
				join eh_part p on p.id = c.part_id
				where r.id = :id
				""", Map.of("id", requirementId), Integer.class);
	}

	private LoePart insertPart(final LoePart part) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update(
				"""
						insert into eh_part (exam_id, title, sort_order)
						values (:examId, :title, :sortOrder)
						""", new MapSqlParameterSource().addValue("examId", part.examId())
						.addValue("title", part.title()).addValue("sortOrder", part.sortOrder()),
				keyHolder, new String[] { "id" });
		return new LoePart(generatedId(keyHolder, "EH part"), part.examId(), part.title(), part.sortOrder());
	}

	private LoeCategory insertCategory(final LoeCategory category) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
				insert into eh_category (part_id, title, description_markdown, sort_order)
				values (:partId, :title, :descriptionMarkdown, :sortOrder)
				""",
				new MapSqlParameterSource().addValue("partId", category.partId()).addValue("title", category.title())
						.addValue("descriptionMarkdown", category.descriptionMarkdown())
						.addValue("sortOrder", category.sortOrder()),
				keyHolder, new String[] { "id" });
		return new LoeCategory(generatedId(keyHolder, "EH category"), category.partId(), category.title(),
				category.descriptionMarkdown(), category.sortOrder());
	}

	private LoeTask insertTask(final LoeTask task) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update(
				"""
						insert into eh_task (category_id, title, sort_order)
						values (:categoryId, :title, :sortOrder)
						""", new MapSqlParameterSource().addValue("categoryId", task.categoryId())
						.addValue("title", task.title()).addValue("sortOrder", task.sortOrder()),
				keyHolder, new String[] { "id" });
		return new LoeTask(generatedId(keyHolder, "EH task"), task.categoryId(), task.title(), task.sortOrder());
	}

	private LoeRequirement insertRequirement(final LoeRequirement requirement) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
				insert into eh_requirement (task_id, description_markdown, max_points, bonus, sort_order)
				values (:taskId, :descriptionMarkdown, :maxPoints, :bonus, :sortOrder)
				""",
				new MapSqlParameterSource().addValue("taskId", requirement.taskId())
						.addValue("descriptionMarkdown", requirement.descriptionMarkdown())
						.addValue("maxPoints", requirement.maxPoints()).addValue("bonus", requirement.bonus())
						.addValue("sortOrder", requirement.sortOrder()),
				keyHolder, new String[] { "id" });
		return new LoeRequirement(generatedId(keyHolder, "EH requirement"), requirement.taskId(),
				requirement.descriptionMarkdown(), requirement.maxPoints(), requirement.bonus(),
				requirement.sortOrder());
	}

	private void syncCriteria(final LoeRequirement requirement) {
		jdbc.update("""
				update eh_criterion
				set active = false
				where requirement_id = :requirementId
				""", Map.of("requirementId", requirement.id()));

		for (final LoeCriterion criterion : LoeCriterionParser.parse(requirement.id(),
				requirement.descriptionMarkdown())) {
			final List<Integer> existingIds = jdbc.queryForList("""
					select id
					from eh_criterion
					where requirement_id = :requirementId
					  and criterion_key = :criterionKey
					""", new MapSqlParameterSource().addValue("requirementId", criterion.requirementId())
					.addValue("criterionKey", criterion.criterionKey()), Integer.class);
			if (existingIds.isEmpty()) {
				insertCriterion(criterion);
			} else {
				updateCriterion(existingIds.getFirst(), criterion);
			}
		}
	}

	private LoeCriterion insertCriterion(final LoeCriterion criterion) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
				insert into eh_criterion (requirement_id, criterion_key, label, sort_order, active)
				values (:requirementId, :criterionKey, :label, :sortOrder, :active)
				""", criterionParameters(criterion), keyHolder, new String[] { "id" });
		return new LoeCriterion(generatedId(keyHolder, "EH criterion"), criterion.requirementId(),
				criterion.criterionKey(), criterion.label(), criterion.sortOrder(), criterion.active());
	}

	private void updateCriterion(final int id, final LoeCriterion criterion) {
		jdbc.update("""
				update eh_criterion
				set label = :label,
				    sort_order = :sortOrder,
				    active = :active
				where id = :id
				""", criterionParameters(criterion).addValue("id", id));
	}

	private MapSqlParameterSource criterionParameters(final LoeCriterion criterion) {
		return new MapSqlParameterSource().addValue("requirementId", criterion.requirementId())
				.addValue("criterionKey", criterion.criterionKey()).addValue("label", criterion.label())
				.addValue("sortOrder", criterion.sortOrder()).addValue("active", criterion.active());
	}

	private ExamNoteSection insertNoteSection(final ExamNoteSection noteSection) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
				insert into exam_note_section (exam_id, title, description_markdown, sort_order)
				values (:examId, :title, :descriptionMarkdown, :sortOrder)
				""",
				new MapSqlParameterSource().addValue("examId", noteSection.examId())
						.addValue("title", noteSection.title())
						.addValue("descriptionMarkdown", noteSection.descriptionMarkdown())
						.addValue("sortOrder", noteSection.sortOrder()),
				keyHolder, new String[] { "id" });
		return new ExamNoteSection(generatedId(keyHolder, "note section"), noteSection.examId(), noteSection.title(),
				noteSection.descriptionMarkdown(), noteSection.sortOrder());
	}

	private void deleteById(final String table, final int id) {
		jdbc.update("delete from " + table + " where id = :id", Map.of("id", id));
	}

	private void assertNoCriterionResultsForPart(final int partId) {
		assertNoResults("""
				select count(*)
				from eh_criterion_result result
				join eh_criterion cr on cr.id = result.criterion_id
				join eh_requirement r on r.id = cr.requirement_id
				join eh_task t on t.id = r.task_id
				join eh_category c on c.id = t.category_id
				where c.part_id = :id
				""", partId);
	}

	private void assertNoCriterionResultsForCategory(final int categoryId) {
		assertNoResults("""
				select count(*)
				from eh_criterion_result result
				join eh_criterion cr on cr.id = result.criterion_id
				join eh_requirement r on r.id = cr.requirement_id
				join eh_task t on t.id = r.task_id
				where t.category_id = :id
				""", categoryId);
	}

	private void assertNoCriterionResultsForTask(final int taskId) {
		assertNoResults("""
				select count(*)
				from eh_criterion_result result
				join eh_criterion cr on cr.id = result.criterion_id
				join eh_requirement r on r.id = cr.requirement_id
				where r.task_id = :id
				""", taskId);
	}

	private void assertNoCriterionResultsForRequirement(final int requirementId) {
		assertNoResults("""
				select count(*)
				from eh_criterion_result result
				join eh_criterion cr on cr.id = result.criterion_id
				where cr.requirement_id = :id
				""", requirementId);
	}

	private void assertNoRequirementResultsForPart(final int partId) {
		assertNoResults("""
				select count(*)
				from eh_requirement_result result
				join eh_requirement r on r.id = result.requirement_id
				join eh_task t on t.id = r.task_id
				join eh_category c on c.id = t.category_id
				where c.part_id = :id
				""", partId);
	}

	private void assertNoRequirementResultsForCategory(final int categoryId) {
		assertNoResults("""
				select count(*)
				from eh_requirement_result result
				join eh_requirement r on r.id = result.requirement_id
				join eh_task t on t.id = r.task_id
				where t.category_id = :id
				""", categoryId);
	}

	private void assertNoRequirementResultsForTask(final int taskId) {
		assertNoResults("""
				select count(*)
				from eh_requirement_result result
				join eh_requirement r on r.id = result.requirement_id
				where r.task_id = :id
				""", taskId);
	}

	private void assertNoRequirementResultsForRequirement(final int requirementId) {
		assertNoResults("""
				select count(*)
				from eh_requirement_result result
				where result.requirement_id = :id
				""", requirementId);
	}

	private void assertNoResults(final String sql, final int id) {
		final Integer count = jdbc.queryForObject(sql, Map.of("id", id), Integer.class);
		if (count != null && count > 0) {
			throw new IllegalStateException("Für diesen Bereich wurden bereits Ergebnisse erfasst.");
		}
	}

	private int nextSortOrder(final String sql, final int parentId) {
		final Integer nextSortOrder = jdbc.queryForObject(sql, Map.of("parentId", parentId), Integer.class);
		return nextSortOrder == null ? 0 : nextSortOrder;
	}

	private void move(final String table, final List<SortableItem> siblings, final Integer id, final int offset) {
		final int currentIndex = indexOf(siblings, id);
		final int targetIndex = currentIndex + offset;
		if (currentIndex < 0 || targetIndex < 0 || targetIndex >= siblings.size()) {
			return;
		}

		final SortableItem current = siblings.get(currentIndex);
		final SortableItem target = siblings.get(targetIndex);
		jdbc.update("""
				update %s
				set sort_order = case
				    when id = :currentId then :targetSortOrder
				    when id = :targetId then :currentSortOrder
				    else sort_order
				end
				where id in (:currentId, :targetId)
				""".formatted(table),
				new MapSqlParameterSource().addValue("currentId", current.id()).addValue("targetId", target.id())
						.addValue("currentSortOrder", current.sortOrder())
						.addValue("targetSortOrder", target.sortOrder()));
	}

	private int indexOf(final List<SortableItem> siblings, final Integer id) {
		for (int index = 0; index < siblings.size(); index++) {
			if (siblings.get(index).id().equals(id)) {
				return index;
			}
		}
		return -1;
	}

	private int generatedId(final KeyHolder keyHolder, final String label) {
		final Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException(label + " insert did not return a generated id");
		}
		return id.intValue();
	}

	private LoePart mapPart(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoePart(resultSet.getInt("id"), resultSet.getInt("exam_id"), resultSet.getString("title"),
				resultSet.getInt("sort_order"));
	}

	private LoeCategory mapCategory(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeCategory(resultSet.getInt("id"), resultSet.getInt("part_id"), resultSet.getString("title"),
				resultSet.getString("description_markdown"), resultSet.getInt("sort_order"));
	}

	private LoeTask mapTask(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeTask(resultSet.getInt("id"), resultSet.getInt("category_id"), resultSet.getString("title"),
				resultSet.getInt("sort_order"));
	}

	private LoeRequirement mapRequirement(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeRequirement(resultSet.getInt("id"), resultSet.getInt("task_id"),
				resultSet.getString("description_markdown"), resultSet.getInt("max_points"),
				resultSet.getBoolean("bonus"), resultSet.getInt("sort_order"));
	}

	private LoeCriterion mapCriterion(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeCriterion(resultSet.getInt("id"), resultSet.getInt("requirement_id"),
				resultSet.getString("criterion_key"), resultSet.getString("label"), resultSet.getInt("sort_order"),
				resultSet.getBoolean("active"));
	}

	private LoeCriterionResult mapCriterionResult(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeCriterionResult(resultSet.getInt("criterion_id"), resultSet.getInt("pupil_id"),
				resultSet.getBoolean("achieved"));
	}

	private LoeRequirementResult mapRequirementResult(final ResultSet resultSet, final int rowNumber)
			throws SQLException {
		return new LoeRequirementResult(resultSet.getInt("requirement_id"), resultSet.getInt("pupil_id"),
				resultSet.getInt("points"), resultSet.getString("comment_text"));
	}

	private ExamNoteSection mapNoteSection(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new ExamNoteSection(resultSet.getInt("id"), resultSet.getInt("exam_id"), resultSet.getString("title"),
				resultSet.getString("description_markdown"), resultSet.getInt("sort_order"));
	}

	private record SortableItem(Integer id, int sortOrder) {

		static SortableItem fromPart(final LoePart part) {
			return new SortableItem(part.id(), part.sortOrder());
		}

		static SortableItem fromCategory(final LoeCategory category) {
			return new SortableItem(category.id(), category.sortOrder());
		}

		static SortableItem fromTask(final LoeTask task) {
			return new SortableItem(task.id(), task.sortOrder());
		}

		static SortableItem fromRequirement(final LoeRequirement requirement) {
			return new SortableItem(requirement.id(), requirement.sortOrder());
		}

		static SortableItem fromNoteSection(final ExamNoteSection noteSection) {
			return new SortableItem(noteSection.id(), noteSection.sortOrder());
		}
	}
}
