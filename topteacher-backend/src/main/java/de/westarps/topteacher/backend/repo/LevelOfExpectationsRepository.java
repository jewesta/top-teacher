package de.westarps.topteacher.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCategoryOutline;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionParser;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialOutline;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageMetadata;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeTaskOutline;
import de.westarps.topteacher.model.loe.LoeValidator;

@Repository
public class LevelOfExpectationsRepository {

	private final ThreadLocal<Integer> materialBatchDepth = ThreadLocal.withInitial(() -> 0);

	private static final String CORRECTION_MODE_MESSAGE = "Der Erwartungshorizont ist im Korrekturmodus. Struktur, Punkte und Kriterien können nicht geändert werden.";
	private static final String RESULTS_BLOCKED_MESSAGE = "Ergebnisse können erst erfasst werden, wenn die regulären Anforderungspunkte jeder Aufgabe genau den Aufgabenpunkten entsprechen und die Aufgabensumme zur Notenskala passt.";
	private static final String MATERIAL_IMAGE_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

	private final NamedParameterJdbcTemplate jdbc;
	private final ExamDesignGuard designGuard;
	private final SecureRandom random = new SecureRandom();
	private final RowMapper<LoePart> partRowMapper = this::mapPart;
	private final RowMapper<LoeCategory> categoryRowMapper = this::mapCategory;
	private final RowMapper<LoeTask> taskRowMapper = this::mapTask;
	private final RowMapper<LoeMaterial> materialRowMapper = this::mapMaterial;
	private final RowMapper<LoeMaterialImage> materialImageRowMapper = this::mapMaterialImage;
	private final RowMapper<LoeRequirement> requirementRowMapper = this::mapRequirement;
	private final RowMapper<LoeCriterion> criterionRowMapper = this::mapCriterion;
	private final RowMapper<LoeCriterionResult> criterionResultRowMapper = this::mapCriterionResult;
	private final RowMapper<LoeRequirementResult> requirementResultRowMapper = this::mapRequirementResult;

	public LevelOfExpectationsRepository(final NamedParameterJdbcTemplate jdbc, final ExamDesignGuard designGuard) {
		this.jdbc = jdbc;
		this.designGuard = designGuard;
	}

	public long designRevision(final int examId) {
		return designGuard.revision(examId);
	}

	public <T> T withDesignRead(final int examId, final Supplier<T> action) {
		return designGuard.read(examId, action);
	}

	public <T> T withDesignWrite(final int examId, final Long expectedRevision, final Supplier<T> action) {
		return designGuard.write(examId, expectedRevision, action);
	}

	/** Retains shared image blobs until the final state of an atomic content batch. */
	@Transactional
	public <T> T withMaterialImageBatch(final int examId, final Supplier<T> action) {
		designGuard.lock(examId);
		lockMaterialImages();
		final int depth = materialBatchDepth.get();
		materialBatchDepth.set(depth + 1);
		try {
			final T result = action.get();
			materialBatchDepth.set(depth);
			if (depth == 0) {
				cleanupUnreferencedMaterialImages();
			}
			return result;
		} finally {
			if (depth == 0) {
				materialBatchDepth.remove();
			} else {
				materialBatchDepth.set(depth);
			}
		}
	}

	public String findExamFooterMarkdown(final int examId) {
		return jdbc.queryForList("""
			select footer_markdown
			from exam
			where id = :examId
			""", Map.of("examId", examId), String.class).stream().findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
	}

	@Transactional
	public void saveExamFooterMarkdown(final int examId, final String footerMarkdown) {
		designGuard.lockAndAdvance(examId);
		final String existing = findExamFooterMarkdown(examId);
		final String value = footerMarkdown == null ? "" : footerMarkdown;
		if (!existing.equals(value)) {
			assertNoResultsForExam(examId);
		}
		final int updated = jdbc.update("""
			update exam
			set footer_markdown = :footerMarkdown
			where id = :examId
			""", Map.of("examId", examId, "footerMarkdown", value));
		if (updated != 1) {
			throw new IllegalArgumentException("Exam does not exist: " + examId);
		}
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
			select c.id, c.part_id, c.title, c.description_markdown, c.sort_order, c.print_tasks_on_exam_sheet
			from eh_category c
			join eh_part p on p.id = c.part_id
			where p.exam_id = :examId
			order by p.sort_order, p.id, c.sort_order, c.id
			""", Map.of("examId", examId), categoryRowMapper);
	}

	public List<LoeCategoryOutline> findCategoryOutlinesByExamId(final int examId) {
		return jdbc.query("""
			select c.id, c.part_id, c.title, c.sort_order
			from eh_category c
			join eh_part p on p.id = c.part_id
			where p.exam_id = :examId
			order by p.sort_order, p.id, c.sort_order, c.id
			""", Map.of("examId", examId), (row, index) -> new LoeCategoryOutline(row.getInt("id"),
					row.getInt("part_id"), row.getString("title"), row.getInt("sort_order")));
	}

	public List<LoeTask> findTasksByExamId(final int examId) {
		return jdbc.query("""
			select t.id, t.category_id, t.title, t.description_markdown, t.max_points, t.sort_order
			from eh_task t
			join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id
			where p.exam_id = :examId
			order by p.sort_order, p.id, c.sort_order, c.id, t.sort_order, t.id
			""", Map.of("examId", examId), taskRowMapper);
	}

	public List<LoeTaskOutline> findTaskOutlinesByExamId(final int examId) {
		return jdbc.query("""
			select t.id, t.category_id, t.title, t.max_points, t.sort_order
			from eh_task t
			join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id
			where p.exam_id = :examId
			order by p.sort_order, p.id, c.sort_order, c.id, t.sort_order, t.id
			""", Map.of("examId", examId), (row, index) -> new LoeTaskOutline(row.getInt("id"),
					row.getInt("category_id"), row.getString("title"), row.getInt("max_points"),
					row.getInt("sort_order")));
	}

	public Optional<LoeTask> findTaskByExamIdAndId(final int examId, final int taskId) {
		return jdbc.query("""
			select t.id, t.category_id, t.title, t.description_markdown, t.max_points, t.sort_order
			from eh_task t
			join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id
			join exam e on e.id = p.exam_id
			where e.id = :examId and t.id = :taskId
			""", Map.of("examId", examId, "taskId", taskId), taskRowMapper).stream().findFirst();
	}

	public List<LoeMaterial> findMaterialsByExamId(final int examId) {
		return jdbc.query("""
			select material.id, material.part_id, material.title, material.description_markdown, material.sort_order, material.dedicated_page
			from loe_material material
			join eh_part part on part.id = material.part_id
			where part.exam_id = :examId
			order by part.sort_order, part.id, material.sort_order, material.id
			""", Map.of("examId", examId), materialRowMapper);
	}

	public List<LoeMaterialOutline> findMaterialOutlinesByExamId(final int examId) {
		return jdbc.query("""
			select material.id, material.part_id, material.title, material.sort_order
			from loe_material material
			join eh_part part on part.id = material.part_id
			where part.exam_id = :examId
			order by part.sort_order, part.id, material.sort_order, material.id
			""", Map.of("examId", examId), (row, index) -> new LoeMaterialOutline(row.getInt("id"),
					row.getInt("part_id"), row.getString("title"), row.getInt("sort_order")));
	}

	public Optional<LoeMaterial> findMaterialByExamIdAndId(final int examId, final int materialId) {
		return jdbc.query("""
			select material.id, material.part_id, material.title, material.description_markdown, material.sort_order, material.dedicated_page
			from loe_material material
			join eh_part part on part.id = material.part_id
			join exam e on e.id = part.exam_id
			where e.id = :examId and material.id = :materialId
			""", Map.of("examId", examId, "materialId", materialId), materialRowMapper).stream().findFirst();
	}

	public List<LoeMaterialImage> findMaterialImagesByIds(final Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return jdbc.query("""
			select id, content_hash, media_type, pixel_width, pixel_height, byte_size, image_data
			from loe_material_image
			where id in (:ids)
			order by id
			""", Map.of("ids", Set.copyOf(ids)), materialImageRowMapper);
	}

	public List<LoeMaterialImageMetadata> findMaterialImageMetadataByIds(final Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return jdbc.query("""
			select id, media_type, pixel_width, pixel_height, byte_size
			from loe_material_image where id in (:ids) order by id
			""", Map.of("ids", Set.copyOf(ids)), (row, index) -> new LoeMaterialImageMetadata(row.getString("id"),
					row.getString("media_type"), row.getInt("pixel_width"), row.getInt("pixel_height"), row.getLong("byte_size")));
	}

	public Optional<LoeMaterialImage> findMaterialImageByContentHash(final String contentHash) {
		return jdbc.query("""
			select id, content_hash, media_type, pixel_width, pixel_height, byte_size, image_data
			from loe_material_image
			where content_hash = :contentHash
			""", Map.of("contentHash", contentHash), materialImageRowMapper).stream().findFirst();
	}

	public Optional<LoeMaterialImage> findReferencedMaterialImageById(final String id) {
		final Optional<LoeMaterialImage> image = findMaterialImageById(id);
		if (image.isEmpty()) {
			return Optional.empty();
		}
		final boolean referenced = jdbc.queryForList("select description_markdown from loe_material", Map.of(),
				String.class).stream().map(LoeMaterialImageReferences::ids).anyMatch(ids -> ids.contains(id));
		return referenced ? image : Optional.empty();
	}

	public boolean materialImageIdExists(final String id) {
		final Integer count = jdbc.queryForObject("select count(*) from loe_material_image where id = :id",
				Map.of("id", id), Integer.class);
		return count != null && count > 0;
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

	public List<LoeRequirement> findRequirementsByExamIdAndTaskId(final int examId, final int taskId) {
		return jdbc.query("""
			select r.id, r.task_id, r.description_markdown, r.max_points, r.bonus, r.sort_order
			from eh_requirement r
			join eh_task t on t.id = r.task_id
			join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id
			join exam e on e.id = p.exam_id
			where e.id = :examId and t.id = :taskId
			order by r.sort_order, r.id
			""", Map.of("examId", examId, "taskId", taskId), requirementRowMapper);
	}

	public List<LoeCriterion> findActiveCriteriaByExamId(final int examId) {
		return jdbc.query("""
			select cr.id, cr.requirement_id, cr.criterion_key, cr.label, cr.point_units, cr.sort_order, cr.active
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

	public List<LoeCriterion> findActiveCriteriaByExamIdAndTaskId(final int examId, final int taskId) {
		return jdbc.query("""
			select cr.id, cr.requirement_id, cr.criterion_key, cr.label, cr.point_units, cr.sort_order, cr.active
			from eh_criterion cr
			join eh_requirement r on r.id = cr.requirement_id
			join eh_task t on t.id = r.task_id
			join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id
			join exam e on e.id = p.exam_id
			where e.id = :examId and t.id = :taskId and cr.active = true
			order by r.sort_order, r.id, cr.sort_order, cr.id
			""", Map.of("examId", examId, "taskId", taskId), criterionRowMapper);
	}

	public List<LoeCriterionResult> findCriterionResultsByExamAndPupil(final int examId, final int pupilId) {
		return jdbc.query("""
			select result.criterion_id, result.pupil_id, result.point_units
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
			select result.requirement_id, result.pupil_id, result.point_units, result.adjustment_units, result.comment_text
			from eh_requirement_result result
			join eh_requirement r on r.id = result.requirement_id
			join eh_task t on t.id = r.task_id
			join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id
			where p.exam_id = :examId
			  and result.pupil_id = :pupilId
			""", Map.of("examId", examId, "pupilId", pupilId), requirementResultRowMapper);
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
	public void copyDesign(final int sourceExamId, final int targetExamId) {
		designGuard.lock(Math.min(sourceExamId, targetExamId));
		designGuard.lock(Math.max(sourceExamId, targetExamId));
		designGuard.lockAndAdvance(targetExamId);
		lockMaterialImages();
		saveExamFooterMarkdown(targetExamId, findExamFooterMarkdown(sourceExamId));
		final Map<Integer, Integer> partIds = new HashMap<>();
		final Map<Integer, Integer> categoryIds = new HashMap<>();
		final Map<Integer, Integer> taskIds = new HashMap<>();

		for (final LoePart part : findPartsByExamId(sourceExamId)) {
			final LoePart copiedPart = savePart(new LoePart(null, targetExamId, part.title(), part.sortOrder()));
			partIds.put(part.id(), copiedPart.id());
		}

		for (final LoeMaterial material : findMaterialsByExamId(sourceExamId)) {
			saveMaterial(new LoeMaterial(null, partIds.get(material.partId()), material.title(),
					material.descriptionMarkdown(), material.sortOrder(), material.dedicatedPage()));
		}

		for (final LoeCategory category : findCategoriesByExamId(sourceExamId)) {
			final LoeCategory copiedCategory = saveCategory(new LoeCategory(null, partIds.get(category.partId()),
					category.title(), category.descriptionMarkdown(), category.sortOrder(),
					category.printTasksOnExamSheet()));
			categoryIds.put(category.id(), copiedCategory.id());
		}

		for (final LoeTask task : findTasksByExamId(sourceExamId)) {
			final LoeTask copiedTask = saveTask(new LoeTask(null, categoryIds.get(task.categoryId()), task.title(),
					task.descriptionMarkdown(), task.maxPoints(), task.sortOrder()));
			taskIds.put(task.id(), copiedTask.id());
		}

		for (final LoeRequirement requirement : findRequirementsByExamId(sourceExamId)) {
			saveRequirement(
					new LoeRequirement(null, taskIds.get(requirement.taskId()), requirement.descriptionMarkdown(),
							requirement.maxPoints(), requirement.bonus(), requirement.sortOrder()));
		}
	}

	@Transactional
	public LoePart savePart(final LoePart part) {
		final int examId = part.id() == null ? part.examId() : examIdForPart(part.id());
		if (examId != part.examId()) {
			throw new IllegalArgumentException("Part ownership cannot be changed.");
		}
		designGuard.lockAndAdvance(examId);
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

	@Transactional
	public LoeCategory saveCategory(final LoeCategory category) {
		if (category.id() != null && !findCategoryById(category.id()).partId().equals(category.partId())) {
			throw new IllegalArgumentException("Category ownership cannot be changed.");
		}
		designGuard.lockAndAdvance(examIdForPart(category.partId()));
		if (category.id() == null) {
			assertNoResultsForExam(examIdForPart(category.partId()));
			return insertCategory(category);
		}
		validateCategoryCorrectionMode(category);
		jdbc.update("""
			update eh_category
			set title = :title,
			    description_markdown = :descriptionMarkdown,
			    print_tasks_on_exam_sheet = :printTasksOnExamSheet,
			    sort_order = :sortOrder
			where id = :id
			""",
				new MapSqlParameterSource().addValue("id", category.id()).addValue("title", category.title())
						.addValue("descriptionMarkdown", category.descriptionMarkdown())
						.addValue("printTasksOnExamSheet", category.printTasksOnExamSheet())
						.addValue("sortOrder", category.sortOrder()));
		return category;
	}

	@Transactional
	public LoeTask saveTask(final LoeTask task) {
		if (task.id() != null && !findTaskById(task.id()).categoryId().equals(task.categoryId())) {
			throw new IllegalArgumentException("Task ownership cannot be changed.");
		}
		designGuard.lockAndAdvance(examIdForCategory(task.categoryId()));
		if (task.id() == null) {
			assertNoResultsForExam(examIdForCategory(task.categoryId()));
			return insertTask(task);
		}
		validateTaskCorrectionMode(task);
		jdbc.update("""
			update eh_task
			set title = :title,
			    description_markdown = :descriptionMarkdown,
			    max_points = :maxPoints,
			    sort_order = :sortOrder
			where id = :id
			""", new MapSqlParameterSource().addValue("id", task.id()).addValue("title", task.title())
			.addValue("descriptionMarkdown", task.descriptionMarkdown()).addValue("maxPoints", task.maxPoints())
				.addValue("sortOrder", task.sortOrder()));
		return task;
	}

	@Transactional
	public LoeMaterial saveMaterial(final LoeMaterial material) {
		final int examId = material.id() == null ? examIdForPart(material.partId())
				: examIdForPart(findMaterialById(material.id()).partId());
		designGuard.lockAndAdvance(examId);
		lockMaterialImages();
		if (material.id() == null) {
			assertNoResultsForExam(examIdForPart(material.partId()));
			return insertMaterial(material);
		}
		final LoeMaterial existing = findMaterialById(material.id());
		final int existingExamId = examIdForPart(existing.partId());
		final int targetExamId = examIdForPart(material.partId());
		assertNoResultsForExam(existingExamId);
		assertNoResultsForExam(targetExamId);
		if (existingExamId != targetExamId) {
			throw new IllegalStateException("Material kann nicht in eine andere Klausur verschoben werden.");
		}
		jdbc.update("""
			update loe_material
			set part_id = :partId,
			    title = :title,
			    description_markdown = :descriptionMarkdown,
			    sort_order = :sortOrder,
			    dedicated_page = :dedicatedPage
			where id = :id
			""", new MapSqlParameterSource().addValue("id", material.id()).addValue("partId", material.partId())
			.addValue("title", material.title()).addValue("descriptionMarkdown", material.descriptionMarkdown())
			.addValue("sortOrder", material.sortOrder()).addValue("dedicatedPage", material.dedicatedPage()));
		return material;
	}

	@Transactional
	public LoeMaterial saveMaterialWithImages(final LoeMaterial material,
			final Collection<LoeMaterialImage> pendingImages) {
		final int examId = material.id() == null ? examIdForPart(material.partId())
				: examIdForPart(findMaterialById(material.id()).partId());
		designGuard.lock(examId);
		lockMaterialImages();
		LoeMaterial canonicalMaterial = material;
		for (final LoeMaterialImage image : pendingImages == null ? List.<LoeMaterialImage>of() : pendingImages) {
			canonicalMaterial = savePendingMaterialImage(canonicalMaterial, image);
		}
		final LoeMaterial saved = saveMaterial(canonicalMaterial);
		assertMaterialImagesExist(saved.descriptionMarkdown());
		cleanupUnreferencedMaterialImages();
		return saved;
	}

	@Transactional
	public LoeRequirement saveRequirement(final LoeRequirement requirement) {
		if (requirement.id() != null && !findRequirementById(requirement.id()).taskId().equals(requirement.taskId())) {
			throw new IllegalArgumentException("Requirement ownership cannot be changed.");
		}
		designGuard.lockAndAdvance(examIdForTask(requirement.taskId()));
		if (requirement.id() == null) {
			assertCriterionAllocationSaveable(requirement);
			assertNoResultsForExam(examIdForTask(requirement.taskId()));
			final LoeRequirement insertedRequirement = insertRequirement(requirement);
			syncCriteria(insertedRequirement);
			return insertedRequirement;
		}
		validateRequirementCorrectionMode(requirement);
		assertCriterionAllocationSaveable(requirement);
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

	private static void assertCriterionAllocationSaveable(final LoeRequirement requirement) {
		final var validation = LoeValidator.validateRequirements(List.of(requirement));
		validation.getTestResults().stream().filter(result -> result.hasFailed()).findFirst().ifPresent(result -> {
			throw new IllegalArgumentException(result.message());
		});
	}

	@Transactional
	public void syncCriteriaForExam(final int examId) {
		designGuard.lock(examId);
		findRequirementsByExamId(examId).forEach(this::syncCriteria);
	}

	@Transactional
	public void saveCriterionResult(final LoeCriterionResult result) {
		designGuard.lockAndAdvance(examIdForCriterion(result.criterionId()));
		assertPointDistributionValidForExam(examIdForCriterion(result.criterionId()));
		final Integer availablePointUnits = jdbc.queryForObject("select point_units from eh_criterion where id = :id",
				Map.of("id", result.criterionId()), Integer.class);
		if (result.pointUnits() > availablePointUnits) {
			throw new IllegalArgumentException("awarded criterion points exceed the criterion value");
		}
		jdbc.update("""
			merge into eh_criterion_result (criterion_id, pupil_id, achieved, point_units)
			key (criterion_id, pupil_id)
			values (:criterionId, :pupilId, :achieved, :pointUnits)
			""", new MapSqlParameterSource().addValue("criterionId", result.criterionId())
				.addValue("pupilId", result.pupilId()).addValue("achieved", result.pointUnits() == availablePointUnits)
				.addValue("pointUnits", result.pointUnits()));
	}

	@Transactional
	public void saveRequirementResult(final LoeRequirementResult result) {
		designGuard.lockAndAdvance(examIdForRequirement(result.requirementId()));
		assertPointDistributionValidForExam(examIdForRequirement(result.requirementId()));
		jdbc.update("""
			merge into eh_requirement_result (requirement_id, pupil_id, points, point_units, adjustment_units, comment_text)
			key (requirement_id, pupil_id)
			values (:requirementId, :pupilId, :points, :pointUnits, :adjustmentUnits, :comment)
			""",
				new MapSqlParameterSource().addValue("requirementId", result.requirementId())
						.addValue("pupilId", result.pupilId()).addValue("points", result.points())
						.addValue("pointUnits", result.pointUnits())
						.addValue("adjustmentUnits", result.adjustmentUnits())
						.addValue("comment", result.comment()));
	}

	@Transactional
	public void deleteResultsByExamAndPupil(final int examId, final int pupilId) {
		designGuard.lockAndAdvance(examId);
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

	@Transactional
	public void deletePart(final int id) {
		designGuard.lockAndAdvance(examIdForPart(id));
		lockMaterialImages();
		assertNoResultsForExam(examIdForPart(id));
		assertNoCriterionResultsForPart(id);
		assertNoRequirementResultsForPart(id);
		deleteById("eh_part", id);
		cleanupUnreferencedMaterialImages();
	}

	@Transactional
	public void deleteCategory(final int id) {
		designGuard.lockAndAdvance(examIdForCategory(id));
		assertNoResultsForExam(examIdForCategory(id));
		assertNoCriterionResultsForCategory(id);
		assertNoRequirementResultsForCategory(id);
		deleteById("eh_category", id);
	}

	@Transactional
	public void deleteTask(final int id) {
		designGuard.lockAndAdvance(examIdForTask(id));
		assertNoResultsForExam(examIdForTask(id));
		assertNoCriterionResultsForTask(id);
		assertNoRequirementResultsForTask(id);
		deleteById("eh_task", id);
	}

	@Transactional
	public void deleteRequirement(final int id) {
		designGuard.lockAndAdvance(examIdForRequirement(id));
		assertNoResultsForExam(examIdForRequirement(id));
		assertNoCriterionResultsForRequirement(id);
		assertNoRequirementResultsForRequirement(id);
		deleteById("eh_requirement", id);
	}

	@Transactional
	public void deleteMaterial(final int id) {
		final LoeMaterial material = findMaterialById(id);
		designGuard.lockAndAdvance(examIdForPart(material.partId()));
		lockMaterialImages();
		assertNoResultsForExam(examIdForPart(material.partId()));
		deleteById("loe_material", id);
		cleanupUnreferencedMaterialImages();
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

	public int nextMaterialSortOrder(final int partId) {
		return nextSortOrder("""
			select coalesce(max(sort_order), -1) + 1
			from loe_material
			where part_id = :parentId
			""", partId);
	}

	@Transactional
	public void movePart(final LoePart part, final int offset) {
		final int examId = examIdForPart(part.id());
		designGuard.lockAndAdvance(examId);
		assertNoResultsForExam(examId);
		final List<LoePart> siblings = findPartsByExamId(examId);
		move("eh_part", siblings.stream().map(SortableItem::fromPart).toList(), part.id(), offset);
	}

	@Transactional
	public void moveCategory(final LoeCategory category, final List<LoeCategory> siblings, final int offset) {
		final int examId = examIdForCategory(category.id());
		designGuard.lockAndAdvance(examId);
		assertNoResultsForExam(examId);
		final LoeCategory existing = findCategoryById(category.id());
		move("eh_category", findCategoriesByExamId(examId).stream()
				.filter(item -> item.partId().equals(existing.partId())).map(SortableItem::fromCategory).toList(),
				category.id(), offset);
	}

	@Transactional
	public void moveTask(final LoeTask task, final List<LoeTask> siblings, final int offset) {
		final int examId = examIdForTask(task.id());
		designGuard.lockAndAdvance(examId);
		assertNoResultsForExam(examId);
		final LoeTask existing = findTaskById(task.id());
		move("eh_task", findTasksByExamId(examId).stream()
				.filter(item -> item.categoryId().equals(existing.categoryId())).map(SortableItem::fromTask).toList(),
				task.id(), offset);
	}

	@Transactional
	public void moveRequirement(final LoeRequirement requirement, final List<LoeRequirement> siblings,
			final int offset) {
		final int examId = examIdForRequirement(requirement.id());
		designGuard.lockAndAdvance(examId);
		assertNoResultsForExam(examId);
		final LoeRequirement existing = findRequirementById(requirement.id());
		move("eh_requirement", findRequirementsByExamId(examId).stream()
				.filter(item -> item.taskId().equals(existing.taskId())).map(SortableItem::fromRequirement).toList(),
				requirement.id(), offset);
	}

	@Transactional
	public void moveMaterial(final LoeMaterial material, final int offset) {
		final LoeMaterial existing = findMaterialById(material.id());
		final int examId = examIdForPart(existing.partId());
		designGuard.lockAndAdvance(examId);
		assertNoResultsForExam(examId);
		final List<SortableItem> persistedSiblings = findMaterialsByExamId(examId).stream()
				.filter(sibling -> sibling.partId().equals(existing.partId())).map(SortableItem::fromMaterial).toList();
		move("loe_material", persistedSiblings, existing.id(), offset);
	}

	public boolean isPointDistributionValidForExam(final int examId) {
		final Integer mismatchedTasks = jdbc.queryForObject("""
			select count(*)
			from (
			    select task.id
			    from eh_task task
			    join eh_category category on category.id = task.category_id
			    join eh_part part on part.id = category.part_id
			    left join eh_requirement requirement on requirement.task_id = task.id
			    where part.exam_id = :examId
			    group by task.id, task.max_points
			    having coalesce(sum(case when requirement.bonus = false then requirement.max_points else 0 end), 0)
			           <> task.max_points
			) mismatch
			""", Map.of("examId", examId), Integer.class);
		final Integer matchingScale = jdbc.queryForObject("""
			select count(*)
			from exam
			join grading_scale on grading_scale.id = exam.grading_scale_id
			where exam.id = :examId
			  and grading_scale.max_points = (
			      select coalesce(sum(task.max_points), 0)
			      from eh_task task
			      join eh_category category on category.id = task.category_id
			      join eh_part part on part.id = category.part_id
			      where part.exam_id = exam.id
			  )
			""", Map.of("examId", examId), Integer.class);
		return (mismatchedTasks == null || mismatchedTasks == 0) && matchingScale != null && matchingScale == 1;
	}

	public void assertPointDistributionValidForExam(final int examId) {
		if (!isPointDistributionValidForExam(examId)) {
			throw new IllegalStateException(RESULTS_BLOCKED_MESSAGE);
		}
	}

	private void validatePartCorrectionMode(final LoePart part) {
		final LoePart existing = findPartById(part.id());
		if (!hasResultsForExam(existing.examId())) {
			return;
		}
		if (!existing.equals(part)) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private void validateCategoryCorrectionMode(final LoeCategory category) {
		final LoeCategory existing = findCategoryById(category.id());
		final int examId = examIdForPart(existing.partId());
		if (!hasResultsForExam(examId)) {
			return;
		}
		if (!existing.equals(category)) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private void validateTaskCorrectionMode(final LoeTask task) {
		final LoeTask existing = findTaskById(task.id());
		final int examId = examIdForCategory(existing.categoryId());
		if (!hasResultsForExam(examId)) {
			return;
		}
		if (!existing.equals(task)) {
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
				|| !criterionDefinitions(existing).equals(criterionDefinitions(requirement))) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private List<String> criterionDefinitions(final LoeRequirement requirement) {
		return LoeCriterionParser.parse(requirement.id(), requirement.descriptionMarkdown()).stream()
				.map(criterion -> criterion.criterionKey() + ":" + criterion.pointUnits()).toList();
	}

	private void assertNoResultsForExam(final int examId) {
		if (hasResultsForExam(examId)) {
			throw new IllegalStateException(CORRECTION_MODE_MESSAGE);
		}
	}

	private void lockMaterialImages() {
		jdbc.queryForObject("select id from loe_material_image_lock where id = 1 for update", Map.of(), Integer.class);
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
			select id, part_id, title, description_markdown, sort_order, print_tasks_on_exam_sheet
			from eh_category
			where id = :id
			""", Map.of("id", id), categoryRowMapper);
	}

	private LoeTask findTaskById(final int id) {
		return jdbc.queryForObject("""
			select id, category_id, title, description_markdown, max_points, sort_order
			from eh_task
			where id = :id
			""", Map.of("id", id), taskRowMapper);
	}

	private LoeMaterial findMaterialById(final int id) {
		return jdbc.queryForObject("""
			select id, part_id, title, description_markdown, sort_order, dedicated_page
			from loe_material
			where id = :id
			""", Map.of("id", id), materialRowMapper);
	}

	private Optional<LoeMaterialImage> findMaterialImageById(final String id) {
		return jdbc.query("""
			select id, content_hash, media_type, pixel_width, pixel_height, byte_size, image_data
			from loe_material_image
			where id = :id
			""", Map.of("id", id), materialImageRowMapper).stream().findFirst();
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

	private int examIdForCriterion(final int criterionId) {
		return jdbc.queryForObject("""
			select p.exam_id
			from eh_criterion cr
			join eh_requirement r on r.id = cr.requirement_id
			join eh_task t on t.id = r.task_id
			join eh_category c on c.id = t.category_id
			join eh_part p on p.id = c.part_id
			where cr.id = :id
			""", Map.of("id", criterionId), Integer.class);
	}

	private LoePart insertPart(final LoePart part) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			insert into eh_part (exam_id, title, sort_order)
			values (:examId, :title, :sortOrder)
			""", new MapSqlParameterSource().addValue("examId", part.examId()).addValue("title", part.title())
				.addValue("sortOrder", part.sortOrder()), keyHolder, new String[] {
						"id"
		});
		return new LoePart(generatedId(keyHolder, "EH part"), part.examId(), part.title(), part.sortOrder());
	}

	private LoeCategory insertCategory(final LoeCategory category) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			insert into eh_category (part_id, title, description_markdown, sort_order, print_tasks_on_exam_sheet)
			values (:partId, :title, :descriptionMarkdown, :sortOrder, :printTasksOnExamSheet)
			""",
				new MapSqlParameterSource().addValue("partId", category.partId()).addValue("title", category.title())
						.addValue("descriptionMarkdown", category.descriptionMarkdown())
						.addValue("printTasksOnExamSheet", category.printTasksOnExamSheet())
						.addValue("sortOrder", category.sortOrder()),
				keyHolder, new String[] {
						"id"
				});
		return new LoeCategory(generatedId(keyHolder, "EH category"), category.partId(), category.title(),
				category.descriptionMarkdown(), category.sortOrder(), category.printTasksOnExamSheet());
	}

	private LoeTask insertTask(final LoeTask task) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update(
				"""
					insert into eh_task (category_id, title, description_markdown, max_points, sort_order)
					values (:categoryId, :title, :descriptionMarkdown, :maxPoints, :sortOrder)
					""", new MapSqlParameterSource().addValue("categoryId", task.categoryId())
						.addValue("title", task.title()).addValue("descriptionMarkdown", task.descriptionMarkdown())
						.addValue("maxPoints", task.maxPoints()).addValue("sortOrder", task.sortOrder()),
				keyHolder, new String[] {
						"id"
				});
		return new LoeTask(generatedId(keyHolder, "EH task"), task.categoryId(), task.title(),
				task.descriptionMarkdown(), task.maxPoints(), task.sortOrder());
	}

	private LoeMaterial insertMaterial(final LoeMaterial material) {
		final KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			insert into loe_material (part_id, title, description_markdown, sort_order, dedicated_page)
			values (:partId, :title, :descriptionMarkdown, :sortOrder, :dedicatedPage)
			""", new MapSqlParameterSource().addValue("partId", material.partId())
			.addValue("title", material.title()).addValue("descriptionMarkdown", material.descriptionMarkdown())
			.addValue("sortOrder", material.sortOrder()).addValue("dedicatedPage", material.dedicatedPage()), keyHolder, new String[] {
					"id"
			});
		return new LoeMaterial(generatedId(keyHolder, "material"), material.partId(), material.title(),
				material.descriptionMarkdown(), material.sortOrder(), material.dedicatedPage());
	}

	private void insertMaterialImage(final LoeMaterialImage image) {
		jdbc.update("""
			insert into loe_material_image
			    (id, content_hash, media_type, pixel_width, pixel_height, byte_size, image_data)
			values
			    (:id, :contentHash, :mediaType, :pixelWidth, :pixelHeight, :byteSize, :imageData)
			""", new MapSqlParameterSource().addValue("id", image.id()).addValue("contentHash", image.contentHash())
				.addValue("mediaType", image.mediaType()).addValue("pixelWidth", image.pixelWidth())
				.addValue("pixelHeight", image.pixelHeight()).addValue("byteSize", image.byteSize())
				.addValue("imageData", image.data()));
	}

	private LoeMaterial savePendingMaterialImage(final LoeMaterial material, final LoeMaterialImage pendingImage) {
		LoeMaterial canonicalMaterial = material;
		LoeMaterialImage candidate = pendingImage;
		for (int attempt = 0; attempt < 100; attempt++) {
			final Optional<LoeMaterialImage> existing = findMaterialImageByContentHash(candidate.contentHash());
			if (existing.isPresent()) {
				return replaceMaterialImageId(canonicalMaterial, candidate.id(), existing.orElseThrow().id());
			}
			if (materialImageIdExists(candidate.id())) {
				final String replacementId = randomMaterialImageId();
				canonicalMaterial = replaceMaterialImageId(canonicalMaterial, candidate.id(), replacementId);
				candidate = withId(candidate, replacementId);
				continue;
			}
			try {
				insertMaterialImage(candidate);
				return canonicalMaterial;
			} catch (final DuplicateKeyException exception) {
				final Optional<LoeMaterialImage> concurrentlyStored = findMaterialImageByContentHash(candidate.contentHash());
				if (concurrentlyStored.isPresent()) {
					return replaceMaterialImageId(canonicalMaterial, candidate.id(),
							concurrentlyStored.orElseThrow().id());
				}
				final String replacementId = randomMaterialImageId();
				canonicalMaterial = replaceMaterialImageId(canonicalMaterial, candidate.id(), replacementId);
				candidate = withId(candidate, replacementId);
			}
		}
		throw new IllegalStateException("Für das Bild konnte keine freie Kennung erzeugt werden.");
	}

	private void assertMaterialImagesExist(final String markdown) {
		final Set<String> referencedIds = LoeMaterialImageReferences.ids(markdown);
		final Set<String> storedIds = findMaterialImagesByIds(referencedIds).stream().map(LoeMaterialImage::id)
				.collect(java.util.stream.Collectors.toSet());
		if (!storedIds.equals(referencedIds)) {
			throw new IllegalArgumentException("Das Material verweist auf ein nicht vorhandenes Bild.");
		}
	}

	private void cleanupUnreferencedMaterialImages() {
		if (materialBatchDepth.get() > 0) {
			return;
		}
		final Set<String> referencedIds = new HashSet<>();
		jdbc.queryForList("select description_markdown from loe_material", Map.of(), String.class).stream()
				.map(LoeMaterialImageReferences::ids).forEach(referencedIds::addAll);
		if (referencedIds.isEmpty()) {
			jdbc.update("delete from loe_material_image", Map.of());
			return;
		}
		jdbc.update("delete from loe_material_image where id not in (:ids)", Map.of("ids", referencedIds));
	}

	private static LoeMaterial withDescription(final LoeMaterial material, final String descriptionMarkdown) {
		return new LoeMaterial(material.id(), material.partId(), material.title(), descriptionMarkdown,
				material.sortOrder(), material.dedicatedPage());
	}

	private static LoeMaterial replaceMaterialImageId(final LoeMaterial material, final String currentId,
			final String replacementId) {
		return withDescription(material,
				LoeMaterialImageReferences.replaceId(material.descriptionMarkdown(), currentId, replacementId));
	}

	private static LoeMaterialImage withId(final LoeMaterialImage image, final String id) {
		return new LoeMaterialImage(id, image.contentHash(), image.mediaType(), image.pixelWidth(), image.pixelHeight(),
				image.byteSize(), image.data());
	}

	private String randomMaterialImageId() {
		final StringBuilder id = new StringBuilder(8);
		for (int index = 0; index < 8; index++) {
			id.append(MATERIAL_IMAGE_ID_ALPHABET.charAt(random.nextInt(MATERIAL_IMAGE_ID_ALPHABET.length())));
		}
		return id.toString();
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
				keyHolder, new String[] {
						"id"
				});
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
			insert into eh_criterion (requirement_id, criterion_key, label, point_units, sort_order, active)
			values (:requirementId, :criterionKey, :label, :pointUnits, :sortOrder, :active)
			""", criterionParameters(criterion), keyHolder, new String[] {
				"id"
		});
		return new LoeCriterion(generatedId(keyHolder, "EH criterion"), criterion.requirementId(),
				criterion.criterionKey(), criterion.label(), criterion.pointUnits(), criterion.sortOrder(),
				criterion.active());
	}

	private void updateCriterion(final int id, final LoeCriterion criterion) {
		jdbc.update("""
			update eh_criterion
			set label = :label,
			    point_units = :pointUnits,
			    sort_order = :sortOrder,
			    active = :active
			where id = :id
			""", criterionParameters(criterion).addValue("id", id));
	}

	private MapSqlParameterSource criterionParameters(final LoeCriterion criterion) {
		return new MapSqlParameterSource().addValue("requirementId", criterion.requirementId())
				.addValue("criterionKey", criterion.criterionKey()).addValue("label", criterion.label())
				.addValue("pointUnits", criterion.pointUnits()).addValue("sortOrder", criterion.sortOrder())
				.addValue("active", criterion.active());
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
				resultSet.getString("description_markdown"), resultSet.getInt("sort_order"),
				resultSet.getBoolean("print_tasks_on_exam_sheet"));
	}

	private LoeTask mapTask(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeTask(resultSet.getInt("id"), resultSet.getInt("category_id"), resultSet.getString("title"),
				resultSet.getString("description_markdown"), resultSet.getInt("max_points"),
				resultSet.getInt("sort_order"));
	}

	private LoeMaterial mapMaterial(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeMaterial(resultSet.getInt("id"), resultSet.getInt("part_id"), resultSet.getString("title"),
				resultSet.getString("description_markdown"), resultSet.getInt("sort_order"), resultSet.getBoolean("dedicated_page"));
	}

	private LoeMaterialImage mapMaterialImage(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeMaterialImage(resultSet.getString("id"), resultSet.getString("content_hash"),
				resultSet.getString("media_type"), resultSet.getInt("pixel_width"), resultSet.getInt("pixel_height"),
				resultSet.getLong("byte_size"), resultSet.getBytes("image_data"));
	}

	private LoeRequirement mapRequirement(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeRequirement(resultSet.getInt("id"), resultSet.getInt("task_id"),
				resultSet.getString("description_markdown"), resultSet.getInt("max_points"),
				resultSet.getBoolean("bonus"), resultSet.getInt("sort_order"));
	}

	private LoeCriterion mapCriterion(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeCriterion(resultSet.getInt("id"), resultSet.getInt("requirement_id"),
				resultSet.getString("criterion_key"), resultSet.getString("label"), resultSet.getInt("point_units"),
				resultSet.getInt("sort_order"), resultSet.getBoolean("active"));
	}

	private LoeCriterionResult mapCriterionResult(final ResultSet resultSet, final int rowNumber) throws SQLException {
		return new LoeCriterionResult(resultSet.getInt("criterion_id"), resultSet.getInt("pupil_id"),
				resultSet.getInt("point_units"));
	}

	private LoeRequirementResult mapRequirementResult(final ResultSet resultSet, final int rowNumber)
			throws SQLException {
		return new LoeRequirementResult(resultSet.getInt("requirement_id"), resultSet.getInt("pupil_id"),
				resultSet.getInt("point_units"), resultSet.getInt("adjustment_units"),
				resultSet.getString("comment_text"));
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

		static SortableItem fromMaterial(final LoeMaterial material) {
			return new SortableItem(material.id(), material.sortOrder());
		}
	}
}
