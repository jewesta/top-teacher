package de.westarps.topteacher.backend.authoring;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.ExamAuthoringResult;
import de.westarps.topteacher.model.loe.ExamAuthoringResult.Changes;
import de.westarps.topteacher.model.loe.ExamAuthoringResult.CreatedNode;
import de.westarps.topteacher.model.loe.ExamAuthoringResult.DeletionImpact;
import de.westarps.topteacher.model.loe.ExamAuthoringResult.SiblingOrder;
import de.westarps.topteacher.model.loe.ExamContentCommand;
import de.westarps.topteacher.model.loe.ExamNodeReference;
import de.westarps.topteacher.model.loe.LevelOfExpectationsCommand;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionParser;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoePointUnits;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;

/**
 * Transactional, exam-scoped authoring operations shared by the MCP boundary.
 * Requirements attach to existing tasks; pupil-facing edits never generate EH.
 */
@Service
public class ExamAuthoringService {

	private static final int MAX_COMMANDS = 500;
	private static final int MAX_LOCAL_KEY = 64;
	private static final int MAX_TITLE = 200;
	private static final int MAX_MARKDOWN = 50_000;
	private static final int MAX_POINTS = 100_000;
	private static final int MAX_NODES = 5_000;
	private static final long MAX_TOTAL_MARKDOWN = 2_000_000;
	private static final int MAX_PARTS = 20;
	private static final int MAX_CATEGORIES_PER_PART = 50;
	private static final int MAX_MATERIALS_PER_PART = 100;
	private static final int MAX_TASKS_PER_CATEGORY = 100;
	private static final int MAX_REQUIREMENTS_PER_TASK = 200;
	private static final int MAX_TOTAL_REQUIREMENTS = 1_000;
	private static final Parser MARKDOWN_PARSER = Parser.builder().build();

	private final ExamRepository exams;
	private final GradingScaleRepository scales;
	private final LevelOfExpectationsRepository repository;
	private final ExamDesignGuard guard;
	private final ExamMutationExecutor mutations;

	public ExamAuthoringService(final ExamRepository exams, final GradingScaleRepository scales,
			final LevelOfExpectationsRepository repository, final ExamDesignGuard guard,
			final ExamMutationExecutor mutations) {
		this.exams = Objects.requireNonNull(exams);
		this.scales = Objects.requireNonNull(scales);
		this.repository = Objects.requireNonNull(repository);
		this.guard = Objects.requireNonNull(guard);
		this.mutations = Objects.requireNonNull(mutations);
	}

	public static AuthoringLimits limits() {
		return new AuthoringLimits(MAX_COMMANDS, MAX_TITLE, MAX_MARKDOWN, MAX_POINTS, MAX_NODES, MAX_TOTAL_MARKDOWN,
				MAX_PARTS, MAX_CATEGORIES_PER_PART, MAX_MATERIALS_PER_PART, MAX_TASKS_PER_CATEGORY,
				MAX_REQUIREMENTS_PER_TASK, MAX_TOTAL_REQUIREMENTS);
	}

	public record AuthoringLimits(int maximumCommands, int maximumTitleLength, int maximumMarkdownLength,
			int maximumPoints, int maximumNodes, long maximumTotalMarkdown, int maximumParts,
			int maximumCategoriesPerPart, int maximumMaterialsPerPart, int maximumTasksPerCategory,
			int maximumRequirementsPerTask, int maximumTotalRequirements) {
	}

	public ExamAuthoringResult applyContentChanges(final int examId, final long expectedRevision,
			final String requestId, final List<ExamContentCommand> commands) {
		return applyContentChanges(examId, expectedRevision, requestId, commands, List.of());
	}

	@Transactional
	public ExamAuthoringResult applyContentChanges(final int examId, final long expectedRevision,
			final String requestId, final List<ExamContentCommand> commands,
			final List<LevelOfExpectationsCommand> requirementChanges) {
		validateBatch(commands, requirementChanges);
		final List<ExamContentCommand> content = List.copyOf(commands);
		final List<LevelOfExpectationsCommand> requirements = List.copyOf(requirementChanges);
		return execute(examId, expectedRevision, requestId, "EXAM_CONTENT",
				ExamMutationExecutor.fingerprint("EXAM_CONTENT", examId, expectedRevision, content, requirements),
				content, requirements);
	}

	@Transactional
	public ExamAuthoringResult applyLevelOfExpectationsChanges(final int examId, final long expectedRevision,
			final String requestId, final List<LevelOfExpectationsCommand> commands) {
		validateBatch(List.of(), commands);
		final List<LevelOfExpectationsCommand> requirements = List.copyOf(commands);
		return execute(examId, expectedRevision, requestId, "LEVEL_OF_EXPECTATIONS",
				ExamMutationExecutor.fingerprint("LEVEL_OF_EXPECTATIONS", examId, expectedRevision, requirements),
				List.of(), requirements);
	}

	/** Validates the final design inside the caller's guarded import transaction. */
	public void validateDesignLimits(final int examId) {
		guard.read(examId, () -> {
			validateFinal(examId, state(examId));
			return null;
		});
	}

	private ExamAuthoringResult execute(final int examId, final long expectedRevision, final String requestId,
			final String operation, final String fingerprint, final List<ExamContentCommand> commands,
			final List<LevelOfExpectationsCommand> requirementChanges) {
		return guard.write(examId, null, () -> {
			final boolean imageBatch = commands.stream().anyMatch(command -> command.type() == ExamContentCommand.Type.DELETE_PART
					|| command.type().name().contains("MATERIAL"));
			final var execution = mutations.execute(examId, expectedRevision, requestId, operation, fingerprint,
					() -> imageBatch ? repository.withMaterialImageBatch(examId,
							() -> applyChanges(examId, commands, requirementChanges))
							: applyChanges(examId, commands, requirementChanges),
					ExamAuthoringService::encode, ExamAuthoringService::decode);
			final State state = state(examId);
			final ChangeMetadata metadata = execution.metadata();
			return new ExamAuthoringResult(examId, guard.revision(examId), execution.revision(), execution.replayed(),
					metadata.created(), metadata.deletions(), diagnostics(examId, state), changes(examId, state, metadata));
		});
	}

	private ChangeMetadata applyChanges(final int examId, final List<ExamContentCommand> commands,
			final List<LevelOfExpectationsCommand> requirementChanges) {
		final PendingChanges pending = new PendingChanges();
		for (int index = 0; index < commands.size(); index++) {
			try {
				applyContent(examId, commands.get(index), state(examId), pending);
			} catch (final IllegalArgumentException exception) {
				throw new IllegalArgumentException("commands[" + index + "]: " + exception.getMessage(), exception);
			}
		}
		for (int index = 0; index < requirementChanges.size(); index++) {
			try {
				applyRequirement(examId, requirementChanges.get(index), state(examId), pending);
			} catch (final IllegalArgumentException exception) {
				throw new IllegalArgumentException("requirementChanges[" + index + "]: " + exception.getMessage(), exception);
			}
		}
		validateFinal(examId, state(examId));
		return pending.metadata();
	}

	private void applyContent(final int examId, final ExamContentCommand command, final State state,
			final PendingChanges pending) {
		Objects.requireNonNull(command.type(), "command type must not be null");
		if (repository.hasResultsForExam(examId)) {
			throw new IllegalStateException("Exam content is locked because results exist.");
		}
		final String operation = command.type().name();
		if (operation.equals("SET_FOOTER")) {
			fields(command, Set.of("markdown"));
			if (command.markdown() == null) {
				throw new IllegalArgumentException("SET_FOOTER requires markdown; use an empty string to clear it");
			}
			repository.saveExamFooterMarkdown(examId, markdown(command.markdown(), false));
			pending.footer = true;
			return;
		}
		final String suffix = operation.substring(operation.indexOf('_') + 1);
		final Kind kind = Kind.valueOf(suffix.equals("CATEGORIES") ? "CATEGORY" : suffix.replaceAll("S$", ""));
		if (operation.startsWith("CREATE_")) {
			final Set<String> allowed = new LinkedHashSet<>(Set.of("clientKey", "title"));
			if (kind != Kind.PART) {
				allowed.addAll(Set.of("parentId", "parentKey", "markdown"));
			}
			if (kind == Kind.TASK) {
				allowed.add("maxPoints");
			}
			fields(command, allowed);
			final int parent = kind == Kind.PART ? examId
					: parent(command.parentId(), command.parentKey(), kind.parent(), state, pending);
			final String title = title(command.title());
			final String markdown = markdown(command.markdown(), kind == Kind.MATERIAL);
			final int id = switch (kind) {
				case PART -> repository.savePart(new LoePart(null, examId, title, repository.nextPartSortOrder(examId))).id();
				case CATEGORY -> repository.saveCategory(new LoeCategory(null, parent, title, markdown,
						repository.nextCategorySortOrder(parent))).id();
				case TASK -> repository.saveTask(new LoeTask(null, parent, title, markdown,
						points(command.maxPoints(), true), repository.nextTaskSortOrder(parent))).id();
				case MATERIAL -> repository.saveMaterialWithImages(new LoeMaterial(null, parent, title, markdown,
						repository.nextMaterialSortOrder(parent)), List.of()).id();
				case REQUIREMENT -> throw new IllegalArgumentException("Use the requirement operations for EH");
			};
			pending.created(command.clientKey(), kind, id);
			pending.touch(kind, id, kind == Kind.PART ? null : parent);
		} else if (operation.startsWith("UPDATE_")) {
			final Set<String> allowed = new LinkedHashSet<>(Set.of("id", "title"));
			if (kind != Kind.PART) {
				allowed.add("markdown");
			}
			if (kind == Kind.TASK) {
				allowed.add("maxPoints");
			}
			fields(command, allowed);
			final int id = requiredId(command.id());
			state.require(kind, id);
			if (command.title() == null && command.markdown() == null && command.maxPoints() == null) {
				throw new IllegalArgumentException("An update must supply at least one editable field");
			}
			final String title = command.title() == null ? state.title(kind, id) : title(command.title());
			final String markdown = command.markdown() == null ? state.markdown(kind, id)
					: markdown(command.markdown(), kind == Kind.MATERIAL);
			switch (kind) {
				case PART -> {
					final LoePart old = state.parts().get(id);
					repository.savePart(new LoePart(id, examId, title, old.sortOrder()));
				}
				case CATEGORY -> {
					final LoeCategory old = state.categories().get(id);
					repository.saveCategory(new LoeCategory(id, old.partId(), title, markdown, old.sortOrder()));
				}
				case TASK -> {
					final LoeTask old = state.tasks().get(id);
					repository.saveTask(new LoeTask(id, old.categoryId(), title, markdown,
							command.maxPoints() == null ? old.maxPoints() : points(command.maxPoints(), true), old.sortOrder()));
				}
				case MATERIAL -> {
					final LoeMaterial old = state.materials().get(id);
					repository.saveMaterialWithImages(new LoeMaterial(id, old.partId(), title, markdown, old.sortOrder()), List.of());
				}
				case REQUIREMENT -> throw new IllegalArgumentException("Use the requirement operations for EH");
			}
			pending.touch(kind, id, state.parent(kind, id));
		} else if (operation.startsWith("DELETE_")) {
			fields(command, Set.of("id", "acknowledgeDeletion"));
			delete(kind, requiredId(command.id()), command.acknowledgeDeletion(), state, pending);
		} else {
			fields(command, kind == Kind.PART ? Set.of("order") : Set.of("parentId", "parentKey", "order"));
			final Integer parent = kind == Kind.PART ? null
					: parent(command.parentId(), command.parentKey(), kind.parent(), state, pending);
			reorder(kind, parent, command.order(), state, pending);
		}
	}

	private void applyRequirement(final int examId, final LevelOfExpectationsCommand command, final State state,
			final PendingChanges pending) {
		Objects.requireNonNull(command.type(), "command type must not be null");
		switch (command.type()) {
			case CREATE_REQUIREMENT -> {
				fields(command, Set.of("clientKey", "taskId", "taskKey", "markdown", "maxPoints", "bonus"));
				final int taskId = parent(command.taskId(), command.taskKey(), Kind.TASK, state, pending);
				final LoeRequirement saved = repository.saveRequirement(new LoeRequirement(null, taskId,
						requirementMarkdown(command.markdown()), points(command.maxPoints(), true),
						Boolean.TRUE.equals(command.bonus()), repository.nextRequirementSortOrder(taskId)));
				pending.created(command.clientKey(), Kind.REQUIREMENT, saved.id());
				pending.touch(Kind.REQUIREMENT, saved.id(), taskId);
			}
			case UPDATE_REQUIREMENT -> {
				fields(command, Set.of("id", "markdown", "maxPoints", "bonus"));
				final int id = requiredId(command.id());
				state.require(Kind.REQUIREMENT, id);
				if (command.markdown() == null && command.maxPoints() == null && command.bonus() == null) {
					throw new IllegalArgumentException("An update must supply at least one editable field");
				}
				final LoeRequirement old = state.requirements().get(id);
				repository.saveRequirement(new LoeRequirement(id, old.taskId(), command.markdown() == null
						? old.descriptionMarkdown() : requirementMarkdown(command.markdown()),
						command.maxPoints() == null ? old.maxPoints() : points(command.maxPoints(), true),
						command.bonus() == null ? old.bonus() : command.bonus(), old.sortOrder()));
				pending.touch(Kind.REQUIREMENT, id, old.taskId());
			}
			case DELETE_REQUIREMENT -> {
				fields(command, Set.of("id", "acknowledgeDeletion"));
				delete(Kind.REQUIREMENT, requiredId(command.id()), command.acknowledgeDeletion(), state, pending);
			}
			case REORDER_REQUIREMENTS -> {
				fields(command, Set.of("taskId", "taskKey", "order"));
				final int taskId = parent(command.taskId(), command.taskKey(), Kind.TASK, state, pending);
				reorder(Kind.REQUIREMENT, taskId, command.order(), state, pending);
			}
		}
	}

	private void delete(final Kind kind, final int id, final Boolean acknowledge, final State state,
			final PendingChanges pending) {
		state.require(kind, id);
		final Set<Integer> partIds = kind == Kind.PART ? Set.of(id) : Set.of();
		final Set<Integer> categoryIds = kind == Kind.CATEGORY ? Set.of(id) : state.categories().values().stream()
				.filter(item -> partIds.contains(item.partId())).map(LoeCategory::id).collect(java.util.stream.Collectors.toSet());
		final Set<Integer> taskIds = kind == Kind.TASK ? Set.of(id) : state.tasks().values().stream()
				.filter(item -> categoryIds.contains(item.categoryId())).map(LoeTask::id).collect(java.util.stream.Collectors.toSet());
		final Set<Integer> materialIds = kind == Kind.MATERIAL ? Set.of(id) : state.materials().values().stream()
				.filter(item -> partIds.contains(item.partId())).map(LoeMaterial::id).collect(java.util.stream.Collectors.toSet());
		final Set<Integer> requirementIds = kind == Kind.REQUIREMENT ? Set.of(id) : state.requirements().values().stream()
				.filter(item -> taskIds.contains(item.taskId())).map(LoeRequirement::id).collect(java.util.stream.Collectors.toSet());
		final int criteria = (int) state.criteria().stream().filter(item -> requirementIds.contains(item.requirementId())).count();
		final DeletionImpact impact = new DeletionImpact(kind.name(), id, partIds.size(), categoryIds.size(),
				taskIds.size(), materialIds.size(), requirementIds.size(), criteria);
		final int descendants = partIds.size() + categoryIds.size() + taskIds.size() + materialIds.size()
				+ requirementIds.size() + criteria - 1;
		if (descendants > 0 && !Boolean.TRUE.equals(acknowledge)) {
			throw new IllegalArgumentException("Deletion removes descendants: " + impact + "; set acknowledgeDeletion=true");
		}
		pending.touch(kind, id, state.parent(kind, id));
		pending.deletions.add(impact);
		switch (kind) {
			case PART -> repository.deletePart(id);
			case CATEGORY -> repository.deleteCategory(id);
			case TASK -> repository.deleteTask(id);
			case MATERIAL -> repository.deleteMaterial(id);
			case REQUIREMENT -> repository.deleteRequirement(id);
		}
	}

	private void reorder(final Kind kind, final Integer parent, final List<ExamNodeReference> order,
			final State state, final PendingChanges pending) {
		if (order == null) {
			throw new IllegalArgumentException("Reordering requires the complete ordered sibling list");
		}
		final List<Integer> ids = order.stream().map(reference -> resolve(reference, kind, state, pending)).toList();
		final List<Integer> siblings = state.siblings(kind, parent);
		if (new LinkedHashSet<>(ids).size() != ids.size() || ids.size() != siblings.size()
				|| !Set.copyOf(ids).equals(Set.copyOf(siblings))) {
			throw new IllegalArgumentException("order must contain every surviving sibling exactly once and no foreign IDs");
		}
		for (int index = 0; index < ids.size(); index++) {
			final int id = ids.get(index);
			switch (kind) {
				case PART -> {
					final LoePart old = state.parts().get(id);
					repository.savePart(new LoePart(id, old.examId(), old.title(), index));
				}
				case CATEGORY -> {
					final LoeCategory old = state.categories().get(id);
					repository.saveCategory(new LoeCategory(id, old.partId(), old.title(), old.descriptionMarkdown(), index));
				}
				case TASK -> {
					final LoeTask old = state.tasks().get(id);
					repository.saveTask(new LoeTask(id, old.categoryId(), old.title(), old.descriptionMarkdown(), old.maxPoints(), index));
				}
				case MATERIAL -> {
					final LoeMaterial old = state.materials().get(id);
					repository.saveMaterialWithImages(new LoeMaterial(id, old.partId(), old.title(), old.descriptionMarkdown(), index), List.of());
				}
				case REQUIREMENT -> {
					final LoeRequirement old = state.requirements().get(id);
					repository.saveRequirement(new LoeRequirement(id, old.taskId(), old.descriptionMarkdown(), old.maxPoints(), old.bonus(), index));
				}
			}
			pending.touch(kind, id, parent);
		}
		pending.orders.add(new Node(kind, parent));
	}

	private static int parent(final Integer id, final String key, final Kind kind, final State state,
			final PendingChanges pending) {
		return resolve(new ExamNodeReference(id, key), kind, state, pending);
	}

	private static int resolve(final ExamNodeReference reference, final Kind kind, final State state,
			final PendingChanges pending) {
		if (reference == null || (reference.id() == null) == (reference.clientKey() == null)) {
			throw new IllegalArgumentException("A reference must supply exactly one persisted ID or earlier client key");
		}
		final int id;
		if (reference.id() != null) {
			id = requiredId(reference.id());
		} else {
			final CreatedNode created = pending.keys.get(reference.clientKey());
			if (created == null || !created.type().equals(kind.name())) {
				throw new IllegalArgumentException("clientKey must refer to an earlier created " + kind);
			}
			id = created.id();
		}
		state.require(kind, id);
		return id;
	}

	private void validateFinal(final int examId, final State state) {
		bounded(repository.findExamFooterMarkdown(examId), MAX_MARKDOWN, "footerMarkdown");
		state.categories().values().forEach(item -> bounded(item.descriptionMarkdown(), MAX_MARKDOWN, "category markdown"));
		state.tasks().values().forEach(item -> bounded(item.descriptionMarkdown(), MAX_MARKDOWN, "task markdown"));
		state.materials().values().forEach(item -> bounded(item.descriptionMarkdown(), MAX_MARKDOWN, "material markdown"));
		state.requirements().values().forEach(item -> bounded(item.descriptionMarkdown(), MAX_MARKDOWN, "requirement markdown"));
		if (state.parts().size() > MAX_PARTS || state.requirements().size() > MAX_TOTAL_REQUIREMENTS || state.nodeCount() > MAX_NODES) {
			throw new IllegalArgumentException("Exam exceeds the aggregate node limit");
		}
		for (final int id : state.parts().keySet()) {
			if (state.siblings(Kind.CATEGORY, id).size() > MAX_CATEGORIES_PER_PART || state.siblings(Kind.MATERIAL, id).size() > MAX_MATERIALS_PER_PART) {
				throw new IllegalArgumentException("Part exceeds its category or material limit");
			}
		}
		for (final int id : state.categories().keySet()) {
			if (state.siblings(Kind.TASK, id).size() > MAX_TASKS_PER_CATEGORY) {
				throw new IllegalArgumentException("Category exceeds its task limit");
			}
		}
		for (final LoeTask task : state.tasks().values()) {
			if (state.siblings(Kind.REQUIREMENT, task.id()).size() > MAX_REQUIREMENTS_PER_TASK) {
				throw new IllegalArgumentException("Task exceeds its requirement limit");
			}
			if (state.regularPoints(task.id()) > task.maxPoints()) {
				throw new IllegalArgumentException("Task " + task.id() + " has over-allocated regular requirement points");
			}
		}
		for (final LoeRequirement requirement : state.requirements().values()) {
			final var parsed = LoeCriterionParser.analyze(requirement.id(), requirement.descriptionMarkdown());
			if (!parsed.issues().isEmpty()) {
				throw new IllegalArgumentException("Requirement " + requirement.id() + " contains invalid criterion declarations");
			}
			if (parsed.criteria().stream().mapToLong(LoeCriterion::pointUnits).sum() > (long) requirement.maxPoints() * 2) {
				throw new IllegalArgumentException("Requirement " + requirement.id() + " has over-allocated criterion points");
			}
		}
		final Integer scaleMaximum = scaleMaximum(examId);
		if (scaleMaximum != null && state.taskPoints() > scaleMaximum) {
			throw new IllegalArgumentException("Exam task points exceed its grading scale maximum");
		}
		final long markdownLength = repository.findExamFooterMarkdown(examId).length()
				+ state.categories().values().stream().mapToLong(item -> item.descriptionMarkdown().length()).sum()
				+ state.tasks().values().stream().mapToLong(item -> item.descriptionMarkdown().length()).sum()
				+ state.materials().values().stream().mapToLong(item -> item.descriptionMarkdown().length()).sum()
				+ state.requirements().values().stream().mapToLong(item -> item.descriptionMarkdown().length()).sum();
		if (markdownLength > MAX_TOTAL_MARKDOWN) {
			throw new IllegalArgumentException("Exam exceeds the aggregate Markdown size limit");
		}
	}

	private List<String> diagnostics(final int examId, final State state) {
		final List<String> messages = new ArrayList<>();
		if (state.tasks().isEmpty()) {
			messages.add("NO_TASKS");
		}
		final Integer maximum = scaleMaximum(examId);
		if (maximum == null) {
			messages.add("GRADING_SCALE_MISSING");
		} else if (state.taskPoints() != maximum) {
			messages.add("EXAM_POINT_MISMATCH: assigned=" + state.taskPoints() + ", expected=" + maximum);
		}
		for (final LoeTask task : state.tasks().values()) {
			final long allocated = state.regularPoints(task.id());
			if (allocated != task.maxPoints()) {
				messages.add("TASK_POINT_MISMATCH: taskId=" + task.id() + ", assigned=" + allocated + ", expected=" + task.maxPoints());
			}
		}
		for (final LoeRequirement requirement : state.requirements().values()) {
			final var parsed = LoeCriterionParser.analyze(requirement.id(), requirement.descriptionMarkdown());
			if (!parsed.issues().isEmpty()) {
				messages.add("INVALID_CRITERIA: requirementId=" + requirement.id());
			} else if (parsed.hasTags()) {
				final long allocated = parsed.criteria().stream().mapToLong(LoeCriterion::pointUnits).sum();
				if (allocated != LoePointUnits.fromWholePoints(requirement.maxPoints())) {
					messages.add("CRITERION_POINT_MISMATCH: requirementId=" + requirement.id());
				}
			}
		}
		return messages;
	}

	private Integer scaleMaximum(final int examId) {
		final Exam exam = exams.findById(examId).orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		return exam.gradingScaleId() == null ? null : scales.findById(exam.gradingScaleId()).map(scale -> scale.maxPoints()).orElse(null);
	}

	private State state(final int examId) {
		return new State(index(repository.findPartsByExamId(examId), LoePart::id),
				index(repository.findCategoriesByExamId(examId), LoeCategory::id),
				index(repository.findTasksByExamId(examId), LoeTask::id),
				index(repository.findMaterialsByExamId(examId), LoeMaterial::id),
				index(repository.findRequirementsByExamId(examId), LoeRequirement::id),
				repository.findActiveCriteriaByExamId(examId));
	}

	private Changes changes(final int examId, final State state, final ChangeMetadata metadata) {
		return new Changes(affected(state.parts(), Kind.PART, metadata), affected(state.categories(), Kind.CATEGORY, metadata),
				affected(state.tasks(), Kind.TASK, metadata), affected(state.materials(), Kind.MATERIAL, metadata),
				affected(state.requirements(), Kind.REQUIREMENT, metadata), state.criteria().stream()
						.filter(item -> metadata.nodes().contains(new Node(Kind.REQUIREMENT, item.requirementId()))).toList(),
				metadata.footer() ? repository.findExamFooterMarkdown(examId) : null,
				metadata.orders().stream().map(order -> new SiblingOrder(order.kind().name(), order.id(),
						state.siblings(order.kind(), order.id()))).toList());
	}

	private static <T> List<T> affected(final Map<Integer, T> values, final Kind kind, final ChangeMetadata metadata) {
		return values.entrySet().stream().filter(entry -> metadata.nodes().contains(new Node(kind, entry.getKey())))
				.map(Map.Entry::getValue).toList();
	}

	private static <T> Map<Integer, T> index(final List<T> items, final Function<T, Integer> key) {
		final Map<Integer, T> values = new LinkedHashMap<>();
		items.forEach(item -> values.put(key.apply(item), item));
		return values;
	}

	private static void validateBatch(final List<ExamContentCommand> commands,
			final List<LevelOfExpectationsCommand> requirements) {
		if (commands == null || requirements == null || commands.size() + requirements.size() == 0
				|| commands.size() + requirements.size() > MAX_COMMANDS
				|| commands.stream().anyMatch(Objects::isNull) || requirements.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Supply between 1 and " + MAX_COMMANDS + " non-null authoring commands");
		}
		/* Bound individual text before hashing or entering the transaction. */
		final long suppliedMarkdown = commands.stream().mapToLong(command -> command.markdown() == null ? 0 : command.markdown().length()).sum()
				+ requirements.stream().mapToLong(command -> command.markdown() == null ? 0 : command.markdown().length()).sum();
		if (suppliedMarkdown > MAX_TOTAL_MARKDOWN) {
			throw new IllegalArgumentException("Request exceeds the aggregate Markdown size limit");
		}
		commands.forEach(command -> {
			if (command.type() == null) throw new IllegalArgumentException("command type must not be null");
			localKey(command.clientKey(), "clientKey");
			localKey(command.parentKey(), "parentKey");
			bounded(command.title(), MAX_TITLE, "title");
			bounded(command.markdown(), MAX_MARKDOWN, "markdown");
			boundedOrder(command.order());
		});
		requirements.forEach(command -> {
			if (command.type() == null) throw new IllegalArgumentException("command type must not be null");
			localKey(command.clientKey(), "clientKey");
			localKey(command.taskKey(), "taskKey");
			bounded(command.markdown(), MAX_MARKDOWN, "markdown");
			boundedOrder(command.order());
		});
	}

	private static void boundedOrder(final List<ExamNodeReference> order) {
		if (order != null && order.size() > MAX_NODES) {
			throw new IllegalArgumentException("order exceeds the sibling limit");
		}
		if (order != null) {
			for (final ExamNodeReference reference : order) {
				if (reference == null) {
					throw new IllegalArgumentException("order must contain non-null references");
				}
				localKey(reference.clientKey(), "order clientKey");
			}
		}
	}

	private static void localKey(final String key, final String field) {
		bounded(key, MAX_LOCAL_KEY, field);
		if (key != null && !key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
			throw new IllegalArgumentException(field + " must match [A-Za-z][A-Za-z0-9_-]{0,63}");
		}
	}

	private static String title(final String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		bounded(value, MAX_TITLE, "title");
		return value.trim();
	}

	private static String markdown(final String value, final boolean material) {
		return validateMarkdown(value, material, false);
	}

	private static String requirementMarkdown(final String value) {
		return validateMarkdown(value, false, true);
	}

	private static String validateMarkdown(final String value, final boolean material, final boolean requirement) {
		final String text = value == null ? "" : value;
		bounded(text, MAX_MARKDOWN, "markdown");
		MARKDOWN_PARSER.parse(text).accept(new AbstractVisitor() {
			@Override
			public void visit(final Link link) {
				if (link.getDestination().startsWith("crit:") && !requirement) {
					throw new IllegalArgumentException("Criterion declarations are supported only in EH requirements");
				}
				if (link.getDestination().startsWith("img:")) {
					throw new IllegalArgumentException("Managed image references require image Markdown, not an ordinary link");
				}
				visitChildren(link);
			}

			@Override
			public void visit(final Image image) {
				if (image.getDestination().startsWith("crit:")) {
					throw new IllegalArgumentException("Criterion declarations require link Markdown, not image Markdown");
				}
				if (image.getDestination().startsWith("img:")) {
					if (!material) {
						throw new IllegalArgumentException("Managed images are supported only in Material");
					}
					if (!image.getDestination().matches("img:[a-z0-9]{8}(?:\\?width=(?:100|[1-9][0-9]?))?")) {
						throw new IllegalArgumentException("Invalid managed image ID or width; use the image ID returned by import");
					}
					final StringBuilder description = new StringBuilder();
					image.accept(new AbstractVisitor() {
						@Override public void visit(final Text item) { description.append(item.getLiteral()); }
						@Override public void visit(final Code item) { description.append(item.getLiteral()); }
					});
					if (description.toString().isBlank()) {
						throw new IllegalArgumentException("Managed images require a non-empty description");
					}
				}
				/* Alternative-text children are rendered as text, not additional links or images. */
			}
		});
		return text;
	}

	private static void bounded(final String value, final int maximum, final String field) {
		if (value != null && value.length() > maximum) {
			throw new IllegalArgumentException(field + " must not exceed " + maximum + " characters");
		}
	}

	private static int points(final Integer value, final boolean required) {
		if (value == null && required || value != null && (value < 0 || value > MAX_POINTS)) {
			throw new IllegalArgumentException("maxPoints must be between 0 and " + MAX_POINTS);
		}
		return value == null ? 0 : value;
	}

	private static int requiredId(final Integer value) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException("A positive persisted ID is required");
		}
		return value;
	}

	private static void fields(final Object command, final Set<String> allowed) {
		try {
			for (final var component : command.getClass().getRecordComponents()) {
				if (!component.getName().equals("type") && !allowed.contains(component.getName())
						&& component.getAccessor().invoke(command) != null) {
					throw new IllegalArgumentException(component.getName() + " is not allowed for this operation");
				}
			}
		} catch (final ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not validate authoring command fields", exception);
		}
	}

	private enum Kind {
		PART, CATEGORY, TASK, MATERIAL, REQUIREMENT;

		Kind parent() {
			return switch (this) {
				case CATEGORY, MATERIAL -> PART;
				case TASK -> CATEGORY;
				case REQUIREMENT -> TASK;
				case PART -> throw new IllegalArgumentException("Parts belong directly to the exam");
			};
		}
	}

	private record Node(Kind kind, Integer id) {
	}

	private record ChangeMetadata(List<CreatedNode> created, List<DeletionImpact> deletions,
			List<Node> nodes, List<Node> orders, boolean footer) {
	}

	private static final class PendingChanges {
		private final Map<String, CreatedNode> keys = new LinkedHashMap<>();
		private final List<CreatedNode> created = new ArrayList<>();
		private final List<DeletionImpact> deletions = new ArrayList<>();
		private final Set<Node> nodes = new LinkedHashSet<>();
		private final Set<Node> orders = new LinkedHashSet<>();
		private boolean footer;

		void created(final String key, final Kind kind, final int id) {
			if (key != null && (!key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || keys.containsKey(key))) {
				throw new IllegalArgumentException("clientKey must be unique and contain up to 64 letters, digits, underscores or hyphens");
			}
			final CreatedNode item = new CreatedNode(key, kind.name(), id);
			created.add(item);
			if (key != null) keys.put(key, item);
		}

		void touch(final Kind kind, final int id, final Integer parent) {
			nodes.add(new Node(kind, id));
			orders.add(new Node(kind, parent));
			if (parent != null) nodes.add(new Node(kind.parent(), parent));
		}

		ChangeMetadata metadata() {
			return new ChangeMetadata(List.copyOf(created), List.copyOf(deletions), List.copyOf(nodes), List.copyOf(orders), footer);
		}
	}

	private record State(Map<Integer, LoePart> parts, Map<Integer, LoeCategory> categories,
			Map<Integer, LoeTask> tasks, Map<Integer, LoeMaterial> materials,
			Map<Integer, LoeRequirement> requirements, List<LoeCriterion> criteria) {

		Map<Integer, ?> nodes(final Kind kind) {
			return switch (kind) {
				case PART -> parts;
				case CATEGORY -> categories;
				case TASK -> tasks;
				case MATERIAL -> materials;
				case REQUIREMENT -> requirements;
			};
		}

		void require(final Kind kind, final int id) {
			if (!nodes(kind).containsKey(id)) {
				throw new IllegalArgumentException(kind + " " + id + " does not belong to this exam");
			}
		}

		Integer parent(final Kind kind, final int id) {
			return switch (kind) {
				case PART -> null;
				case CATEGORY -> categories.get(id).partId();
				case TASK -> tasks.get(id).categoryId();
				case MATERIAL -> materials.get(id).partId();
			case REQUIREMENT -> requirements.get(id).taskId();
			};
		}

		int order(final Kind kind, final int id) {
			return switch (kind) {
				case PART -> parts.get(id).sortOrder();
				case CATEGORY -> categories.get(id).sortOrder();
				case TASK -> tasks.get(id).sortOrder();
				case MATERIAL -> materials.get(id).sortOrder();
			case REQUIREMENT -> requirements.get(id).sortOrder();
			};
		}

		List<Integer> siblings(final Kind kind, final Integer parent) {
			return nodes(kind).keySet().stream().filter(id -> Objects.equals(parent(kind, id), parent))
					.sorted(Comparator.<Integer>comparingInt(id -> order(kind, id)).thenComparingInt(id -> id)).toList();
		}

		String title(final Kind kind, final int id) {
			return switch (kind) {
				case PART -> parts.get(id).title();
				case CATEGORY -> categories.get(id).title();
				case TASK -> tasks.get(id).title();
				case MATERIAL -> materials.get(id).title();
			case REQUIREMENT -> "";
			};
		}

		String markdown(final Kind kind, final int id) {
			return switch (kind) {
				case PART -> "";
				case CATEGORY -> categories.get(id).descriptionMarkdown();
			case TASK -> tasks.get(id).descriptionMarkdown();
			case MATERIAL -> materials.get(id).descriptionMarkdown();
			case REQUIREMENT -> requirements.get(id).descriptionMarkdown();
			};
		}

		int nodeCount() {
			return parts.size() + categories.size() + tasks.size() + materials.size() + requirements.size() + criteria.size();
		}

		long taskPoints() {
			return tasks.values().stream().mapToLong(LoeTask::maxPoints).sum();
		}

		long regularPoints(final int taskId) {
			return requirements.values().stream().filter(item -> item.taskId() == taskId && !item.bonus())
					.mapToLong(LoeRequirement::maxPoints).sum();
		}
	}

	private static String encode(final ChangeMetadata metadata) {
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.writeInt(1);
				output.writeInt(metadata.created().size());
				for (final CreatedNode item : metadata.created()) {
					output.writeBoolean(item.clientKey() != null);
					if (item.clientKey() != null) output.writeUTF(item.clientKey());
					output.writeUTF(item.type());
					output.writeInt(item.id());
				}
				output.writeInt(metadata.deletions().size());
				for (final DeletionImpact item : metadata.deletions()) {
					output.writeUTF(item.type());
					output.writeInt(item.id());
					output.writeInt(item.parts());
					output.writeInt(item.categories());
					output.writeInt(item.tasks());
					output.writeInt(item.materials());
					output.writeInt(item.requirements());
					output.writeInt(item.criteria());
				}
				writeNodes(output, metadata.nodes());
				writeNodes(output, metadata.orders());
				output.writeBoolean(metadata.footer());
			}
			return Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (final IOException exception) {
			throw new IllegalStateException("Could not encode authoring receipt metadata", exception);
		}
	}

	private static ChangeMetadata decode(final String encoded) {
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
			if (input.readInt() != 1) throw new IOException("Unknown receipt format");
			final List<CreatedNode> created = new ArrayList<>();
			for (int count = input.readInt(); count > 0; count--) {
				created.add(new CreatedNode(input.readBoolean() ? input.readUTF() : null, input.readUTF(), input.readInt()));
			}
			final List<DeletionImpact> deletions = new ArrayList<>();
			for (int count = input.readInt(); count > 0; count--) {
				deletions.add(new DeletionImpact(input.readUTF(), input.readInt(), input.readInt(), input.readInt(),
						input.readInt(), input.readInt(), input.readInt(), input.readInt()));
			}
			return new ChangeMetadata(List.copyOf(created), List.copyOf(deletions), readNodes(input), readNodes(input), input.readBoolean());
		} catch (final IOException | IllegalArgumentException exception) {
			throw new IllegalStateException("Could not decode authoring receipt metadata", exception);
		}
	}

	private static void writeNodes(final DataOutputStream output, final List<Node> nodes) throws IOException {
		output.writeInt(nodes.size());
		for (final Node node : nodes) {
			output.writeUTF(node.kind().name());
			output.writeBoolean(node.id() != null);
			if (node.id() != null) output.writeInt(node.id());
		}
	}

	private static List<Node> readNodes(final DataInputStream input) throws IOException {
		final List<Node> nodes = new ArrayList<>();
		for (int count = input.readInt(); count > 0; count--) {
			nodes.add(new Node(Kind.valueOf(input.readUTF()), input.readBoolean() ? input.readInt() : null));
		}
		return List.copyOf(nodes);
	}
}
