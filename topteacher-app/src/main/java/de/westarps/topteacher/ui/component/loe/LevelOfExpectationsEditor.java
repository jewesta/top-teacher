package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeValidationTarget;
import de.westarps.topteacher.model.loe.LoeValidator;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.DesignerViewport;
import de.westarps.topteacher.ui.component.FullscreenButton;
import de.westarps.validate.TestResult;
import de.westarps.validate.ValidationResult;
import de.westarps.validate.ValidationResults;

public class LevelOfExpectationsEditor extends AbstractDesigner {

	public enum DesignState {
		INCOMPLETE,
		COMPLETE,
		LOCKED
	}

	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final FullscreenButton fullscreenButton;
	private final LoeSaveController saveController = new LoeSaveController();
	private final LoeSectionComponents components = new LoeSectionComponents(saveController);
	private final LoeCollapseState collapseState = new LoeCollapseState(components);
	private final DesignerViewport viewport = new DesignerViewport(content());
	private final LoeRequirementSection.Handler requirementHandler = new LoeRequirementSection.Handler() {

		@Override
		public void save(final LoeRequirement requirement, final String descriptionMarkdown, final int maxPoints,
				final boolean bonus) {
			LevelOfExpectationsEditor.this.save(requirement, descriptionMarkdown, maxPoints, bonus);
		}

		@Override
		public void move(final LoeRequirement requirement, final int offset) {
			LevelOfExpectationsEditor.this.move(requirement, offset);
		}

		@Override
		public void delete(final LoeRequirement requirement) {
			LevelOfExpectationsEditor.this.delete(requirement);
		}
	};

	private Exam exam;
	private List<LoePart> parts = List.of();
	private List<LoeCategory> categories = List.of();
	private List<LoeTask> tasks = List.of();
	private List<LoeRequirement> requirements = List.of();
	private LoePointBadge examPointsBadge;
	private final Span breadcrumb = new Span();
	private List<LoeReadOnlySection<LoePart>> partSections = List.of();
	private List<LoeRequirementSection> requirementSections = List.of();
	private final Map<Integer, LoeAllocationMessage> taskAllocationMessages = new HashMap<>();
	private boolean correctionMode;
	private Integer gradingScaleMaxPoints;
	private ValidationResults<LoeValidationTarget> validationResults = ValidationResults.pass();
	private DesignState designState = DesignState.INCOMPLETE;
	private Runnable changeHandler = () -> {
	};
	private Consumer<DesignState> designStateChangeHandler = state -> {
	};

	LevelOfExpectationsEditor(final LevelOfExpectationsRepository levelOfExpectationsRepository) {
		this(levelOfExpectationsRepository, null);
	}

	public LevelOfExpectationsEditor(final LevelOfExpectationsRepository levelOfExpectationsRepository,
			final GradingScaleRepository gradingScaleRepository) {
		super("tt-eh-editor");
		enableStatusTray();
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		fullscreenButton = new FullscreenButton(this);

		components.setValueChangeHandler(this::updateValidation);
		components.setPointsChangeHandler(this::refreshBadges);
		saveController.setDirtySupplier(this::isDirty);
		saveController.setSaveAllowedSupplier(this::isSaveAllowed);
		saveController.setSaveAction(this::saveDirtySections);
		saveController.setDiscardAction(this::discardDirtySections);
	}

	public void setExam(final Exam exam) {
		if (isDirty() && isDifferentExam(exam)) {
			Notification.show("Speichere oder verwirf zuerst die ungespeicherten Anforderungen.");
			return;
		}
		if (this.exam == null || exam == null || !this.exam.id().equals(exam.id())) {
			collapseState.clear();
		}
		this.exam = exam;
		gradingScaleMaxPoints = gradingScaleMaxPoints(exam);
		refresh();
	}

	private boolean isDifferentExam(final Exam exam) {
		return this.exam == null || exam == null || !this.exam.id().equals(exam.id());
	}

	public void setChangeHandler(final Runnable changeHandler) {
		this.changeHandler = changeHandler == null ? () -> {
		} : changeHandler;
	}

	public void setDesignStateChangeHandler(final Consumer<DesignState> designStateChangeHandler) {
		this.designStateChangeHandler = designStateChangeHandler == null ? state -> {
		} : designStateChangeHandler;
		this.designStateChangeHandler.accept(designState);
	}

	public DesignState getDesignState() {
		return designState;
	}

	public boolean hasUnsavedChanges() {
		return isDirty();
	}

	public boolean focusRequirement(final LoeNavigationTarget target) {
		if (!contains(target)) {
			return false;
		}
		collapseState.expand(List.of(target.partAnchor(), target.categoryAnchor(), target.taskAnchor()));
		viewport.scrollTo(target.requirementAnchor());
		return true;
	}

	private void refresh() {
		saveController.clearButtons();
		collapseState.clearRenderedComponents();
		examPointsBadge = null;
		partSections = List.of();
		requirementSections = new ArrayList<>();
		taskAllocationMessages.clear();
		resetDesigner();
		if (exam == null) {
			correctionMode = false;
			validationResults = ValidationResults.pass();
			setValidationResults(validationResults);
			updateDesignState();
			showDesignerMessage(new Span("Bitte wähle eine Klausur aus."));
			return;
		}

		correctionMode = levelOfExpectationsRepository.hasResultsForExam(exam.id());
		loadItems();
		if (!correctionMode && ensureRequiredChildren()) {
			loadItems();
		}

		if (parts.isEmpty()) {
			content().add(emptyState("Lege zuerst im Reiter Aufgaben einen Klausurteil an."));
		} else {
			partSections = parts.stream().map(this::createPartSection).toList();
			partSections.forEach(content()::add);
			content().add(levelOfExpectationsLight());
		}

		configureToolbar();
		showDesigner();
		updateValidation();
		saveController.update();
	}

	private void loadItems() {
		parts = levelOfExpectationsRepository.findPartsByExamId(exam.id());
		categories = levelOfExpectationsRepository.findCategoriesByExamId(exam.id());
		tasks = levelOfExpectationsRepository.findTasksByExamId(exam.id());
		requirements = levelOfExpectationsRepository.findRequirementsByExamId(exam.id());
	}

	private boolean ensureRequiredChildren() {
		boolean changed = false;
		for (final LoeTask task : tasks) {
			if (requirementsFor(task).isEmpty()) {
				addDefaultRequirement(task);
				changed = true;
			}
		}
		return changed;
	}

	private void configureToolbar() {
		final Button save = components.saveButton();
		final Button discard = components.discardButton();
		final Button reload = saveController
				.registerClean(components.iconButton("Neu laden", VaadinIcon.REFRESH, event -> refreshFromDatabase()));

		examPointsBadge = components.pointBadge("Gesamt", this::pointsForExam);
		breadcrumb.addClassName("tt-designer-breadcrumb");
		toolbar().add(save, discard, collapseState.toggleAllButton(allDetailKeys()), fullscreenButton,
				components.discardConfirmation(), reload);
		toolbarSummary().add(breadcrumb, examPointsBadge);
		toolbarSummary().expand(breadcrumb);
		viewport.bindBreadcrumb(breadcrumb, "EH", "Erwartungshorizont");
	}

	private LoeReadOnlySection<LoePart> createPartSection(final LoePart part) {
		final List<LoeReadOnlySection<LoeCategory>> categorySections = categoriesFor(part).stream()
				.map(this::createCategorySection).toList();
		final LoePercentageBadge percentageBadge = components.percentageBadge(() -> percentageForPart(part));
		final LoePointBadge pointBadge = components.pointBadge("Summe", () -> pointsForPart(part));
		final LoeReadOnlySection<LoePart> section = new LoeReadOnlySection<>(part, "tt-eh-part",
				components.partSummary(components.summaryTitle(part.title()), percentageBadge, pointBadge), pointBadge,
				List.of(), categorySections);
		collapseState.configure(section.getContent(), detailKey("part", part.id()), null,
				partDescendantDetailKeys(part), "Klausurteil", part.title());
		DesignerViewport.mark(section, detailKey("part", part.id()), part.title());
		return section;
	}

	private LoeReadOnlySection<LoeCategory> createCategorySection(final LoeCategory category) {
		final List<LoeReadOnlySection<LoeTask>> taskSections = tasksFor(category).stream()
				.map(this::createTaskSection).toList();
		final LoePointBadge pointBadge = components.pointBadge("Summe", () -> pointsForCategory(category));
		final Component description = components.markdownViewBlock("Beschreibung", category.descriptionMarkdown());
		final LoeReadOnlySection<LoeCategory> section = new LoeReadOnlySection<>(category, "tt-eh-category",
				components.summary("Leistungskategorie", components.summaryTitle(category.title()), pointBadge), pointBadge,
				List.of(description), taskSections);
		collapseState.configure(section.getContent(), detailKey("category", category.id()),
				detailKey("part", category.partId()), categoryDescendantDetailKeys(category),
				"Leistungskategorie", category.title());
		DesignerViewport.mark(section, detailKey("category", category.id()), category.title());
		return section;
	}

	private LoeReadOnlySection<LoeTask> createTaskSection(final LoeTask task) {
		final List<LoeRequirementSection> requirementSections = requirementsFor(task).stream()
				.map(this::createRequirementSection).toList();
		final LoePointDistributionBadge pointBadge = components.pointDistributionBadge(task.maxPoints(),
				() -> distributedPointsForTask(task));
		final LoeAllocationMessage allocationMessage = new LoeAllocationMessage("tt-eh-task-allocation-message");
		taskAllocationMessages.put(task.id(), allocationMessage);
		final Button addRequirement = components.commandButton("Anforderung hinzufügen", VaadinIcon.PLUS,
				event -> addRequirement(task));
		if (correctionMode) {
			components.lockCorrectionModeAction(addRequirement);
		}
		final Component description = components.markdownViewBlock("Aufgabentext", task.descriptionMarkdown());
		final Component actions = components.editorBlock(addRequirement);
		final LoeReadOnlySection<LoeTask> section = new LoeReadOnlySection<>(task, "tt-eh-task",
				components.summary("Aufgabe", components.summaryTitle(task.title()), allocationMessage, pointBadge), pointBadge,
				List.of(description, actions), requirementSections);
		section.setId(taskValidationTargetId(task.id()));
		collapseState.configure(section.getContent(), detailKey("task", task.id()),
				detailKey("category", task.categoryId()), List.of(), "Aufgabe", task.title());
		DesignerViewport.mark(section, detailKey("task", task.id()), task.title());
		return section;
	}

	private LoeRequirementSection createRequirementSection(final LoeRequirement requirement) {
		final List<LoeRequirement> siblings = requirementsFor(taskFor(requirement));
		final LoeRequirementSection section = new LoeRequirementSection(requirement, siblings, components,
				requirementHandler, requirementNumber(siblings, requirement), correctionMode);
		requirementSections.add(section);
		section.setId(requirementValidationTargetId(requirement.id()));
		DesignerViewport.mark(section, detailKey("requirement", requirement.id()),
				requirementNumber(siblings, requirement));
		return section;
	}

	private boolean contains(final LoeNavigationTarget target) {
		return parts.stream().anyMatch(part -> part.id().equals(target.partId()))
				&& categories.stream()
						.anyMatch(category -> category.id().equals(target.categoryId())
								&& category.partId().equals(target.partId()))
				&& tasks.stream().anyMatch(
						task -> task.id().equals(target.taskId()) && task.categoryId().equals(target.categoryId()))
				&& requirements.stream().anyMatch(requirement -> requirement.id().equals(target.requirementId())
						&& requirement.taskId().equals(target.taskId()));
	}

	private LoeRequirement addDefaultRequirement(final LoeTask task) {
		return levelOfExpectationsRepository.saveRequirement(new LoeRequirement(null, task.id(), "", 0, false,
				levelOfExpectationsRepository.nextRequirementSortOrder(task.id())));
	}

	private List<LoeCategory> categoriesFor(final LoePart part) {
		return categories.stream().filter(category -> category.partId().equals(part.id()))
				.sorted(Comparator.comparingInt(LoeCategory::sortOrder).thenComparing(LoeCategory::id)).toList();
	}

	private List<LoeTask> tasksFor(final LoeCategory category) {
		return tasks.stream().filter(task -> task.categoryId().equals(category.id()))
				.sorted(Comparator.comparingInt(LoeTask::sortOrder).thenComparing(LoeTask::id)).toList();
	}

	private List<LoeRequirement> requirementsFor(final LoeTask task) {
		return requirements.stream().filter(requirement -> requirement.taskId().equals(task.id()))
				.sorted(Comparator.comparingInt(LoeRequirement::sortOrder).thenComparing(LoeRequirement::id)).toList();
	}

	private LoePart partFor(final LoeCategory category) {
		return parts.stream().filter(part -> part.id().equals(category.partId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH part: " + category.partId()));
	}

	private LoeCategory categoryFor(final LoeTask task) {
		return categories.stream().filter(category -> category.id().equals(task.categoryId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH category: " + task.categoryId()));
	}

	private LoeTask taskFor(final LoeRequirement requirement) {
		return tasks.stream().filter(task -> task.id().equals(requirement.taskId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH task: " + requirement.taskId()));
	}

	private LoePoints pointsForExam() {
		return tasks.stream().map(this::pointsForTask).reduce(new LoePoints(0, 0), LoePoints::plus);
	}

	private LoePoints pointsForPart(final LoePart part) {
		return categoriesFor(part).stream().flatMap(category -> tasksFor(category).stream()).map(this::pointsForTask)
				.reduce(new LoePoints(0, 0), LoePoints::plus);
	}

	private LoePoints pointsForCategory(final LoeCategory category) {
		return tasksFor(category).stream().map(this::pointsForTask).reduce(new LoePoints(0, 0), LoePoints::plus);
	}

	private LoePoints pointsForTask(final LoeTask task) {
		return new LoePoints(task.maxPoints(), distributedPointsForTask(task).bonus());
	}

	private LoePoints distributedPointsForTask(final LoeTask task) {
		return sum(requirementsFor(task));
	}

	private LoePoints pointsForRequirement(final LoeRequirement requirement) {
		final LoeRequirement pendingRequirement = requirementSections.stream()
				.filter(section -> section.requirement().id().equals(requirement.id()))
				.map(LoeRequirementSection::pendingRequirement).findFirst().orElse(requirement);
		return pendingRequirement.bonus() ? new LoePoints(0, pendingRequirement.maxPoints())
				: new LoePoints(pendingRequirement.maxPoints(), 0);
	}

	private int percentageForPart(final LoePart part) {
		final int totalPoints = pointsForExam().total();
		if (totalPoints == 0) {
			return 0;
		}
		return Math.round(pointsForPart(part).total() * 100.0f / totalPoints);
	}

	private LoePoints sum(final List<LoeRequirement> requirements) {
		return requirements.stream().map(this::pointsForRequirement).reduce(new LoePoints(0, 0), LoePoints::plus);
	}

	private Component levelOfExpectationsLight() {
		final String sunrise = "\uD83C\uDF05"; // U+1F305 sunrise
		final Span text = new Span("Licht am Ende des Erwartungshorizonts " + sunrise);
		final HorizontalLayout gag = new HorizontalLayout(text);
		gag.addClassName("tt-eh-gag");
		gag.setAlignItems(Alignment.CENTER);
		gag.setPadding(false);
		return gag;
	}

	private String requirementNumber(final List<LoeRequirement> siblings, final LoeRequirement requirement) {
		for (int index = 0; index < siblings.size(); index++) {
			if (siblings.get(index).id().equals(requirement.id())) {
				return String.valueOf(index + 1);
			}
		}
		throw new IllegalStateException("Missing EH requirement: " + requirement.id());
	}

	private String detailKey(final String type, final Integer id) {
		return LoeNavigationTarget.anchor(type, id);
	}

	private List<String> allDetailKeys() {
		final List<String> keys = new ArrayList<>();
		parts.forEach(part -> keys.add(detailKey("part", part.id())));
		categories.forEach(category -> keys.add(detailKey("category", category.id())));
		tasks.forEach(task -> keys.add(detailKey("task", task.id())));
		return keys;
	}

	private List<String> partDescendantDetailKeys(final LoePart part) {
		final List<String> keys = new ArrayList<>();
		for (final LoeCategory category : categoriesFor(part)) {
			keys.add(detailKey("category", category.id()));
			tasksFor(category).forEach(task -> keys.add(detailKey("task", task.id())));
		}
		return keys;
	}

	private List<String> categoryDescendantDetailKeys(final LoeCategory category) {
		return tasksFor(category).stream().map(task -> detailKey("task", task.id())).toList();
	}

	private void replaceRequirement(final LoeRequirement requirement) {
		requirements = requirements.stream()
				.map(currentRequirement -> currentRequirement.id().equals(requirement.id()) ? requirement
						: currentRequirement)
				.toList();
	}

	private void addRequirement(final LoeTask task) {
		final LoeRequirement requirement = addDefaultRequirement(task);
		refreshAndScrollTo(detailKey("requirement", requirement.id()));
		notifyChanged();
	}

	private void save(final LoeRequirement requirement, final String descriptionMarkdown, final int maxPoints,
			final boolean bonus) {
		final LoeRequirement updatedRequirement = new LoeRequirement(requirement.id(), requirement.taskId(),
				descriptionMarkdown, maxPoints, bonus, requirement.sortOrder());
		levelOfExpectationsRepository.saveRequirement(updatedRequirement);
		replaceRequirement(updatedRequirement);
		refreshBadges();
	}

	private void move(final LoeRequirement requirement, final int offset) {
		levelOfExpectationsRepository.moveRequirement(requirement, requirementsFor(taskFor(requirement)), offset);
		refresh();
		notifyChanged();
	}

	private void delete(final LoeRequirement requirement) {
		deleteSafely(() -> levelOfExpectationsRepository.deleteRequirement(requirement.id()));
	}

	private void deleteSafely(final Runnable deleteAction) {
		try {
			deleteAction.run();
			refresh();
			notifyChanged();
		} catch (final IllegalStateException exception) {
			Notification.show(exception.getMessage());
		}
	}

	private Component emptyState(final String text) {
		final Span emptyState = new Span(text);
		emptyState.addClassName("tt-empty-state");
		return emptyState;
	}

	private void refreshBadges() {
		if (examPointsBadge != null) {
			examPointsBadge.refreshBadges();
		}
		partSections.forEach(LoeReadOnlySection::refreshBadges);
	}

	private boolean isDirty() {
		return partSections.stream().anyMatch(LoeReadOnlySection::isDirty);
	}

	private void saveDirtySections() {
		if (!isSaveAllowed()) {
			return;
		}
		try {
			for (final LoeReadOnlySection<LoePart> partSection : partSections) {
				if (!partSection.save()) {
					return;
				}
			}
		} catch (final IllegalStateException exception) {
			Notification.show(exception.getMessage());
			return;
		}
		refreshBadges();
		updateValidation();
		notifyChanged();
	}

	private void discardDirtySections() {
		refresh();
	}

	private void refreshFromDatabase() {
		if (!isDirty()) {
			viewport.refreshPreservingPosition(this::refresh);
		}
	}

	private void refreshAndScrollTo(final String anchorKey) {
		refresh();
		viewport.scrollTo(anchorKey);
	}

	private void notifyChanged() {
		changeHandler.run();
	}

	private Integer gradingScaleMaxPoints(final Exam exam) {
		if (exam == null || exam.gradingScaleId() == null || gradingScaleRepository == null) {
			return null;
		}
		return gradingScaleRepository.findById(exam.gradingScaleId()).map(scale -> scale.maxPoints()).orElse(null);
	}

	private void updateValidation() {
		if (exam == null) {
			validationResults = ValidationResults.pass();
		} else {
			final List<LoeRequirement> pendingRequirements = requirementSections.stream()
					.map(LoeRequirementSection::pendingRequirement).toList();
			final ValidationResults<LoeValidationTarget> allocationResults = gradingScaleMaxPoints == null
					? LoeValidator.validate(tasks, pendingRequirements)
					: LoeValidator.validate(gradingScaleMaxPoints, tasks, pendingRequirements);
			taskAllocationMessages.forEach((taskId, message) -> message.setValidation(allocationResults.getResults().stream()
					.filter(result -> result.getTarget().equals(LoeValidationTarget.taskPoints(taskId)))
					.flatMap(result -> result.getResults().stream()).toList()));
			requirementSections.forEach(section -> section.setCriterionValidation(section.hasValidMaxPoints()
					? allocationResults.getResults().stream()
							.filter(result -> result.getTarget().equals(
									LoeValidationTarget.requirementCriteria(section.requirement().id())))
							.flatMap(result -> result.getResults().stream()).toList()
					: List.of()));
			final ValidationResults.Builder<LoeValidationTarget> results = ValidationResults.builder();
			results.addAll(allocationResults.getResults());
			requirementSections.stream().filter(section -> !section.hasValidMaxPoints())
					.map(section -> ValidationResult.error(
							LoeValidationTarget.requirementCriteria(section.requirement().id()),
							"Die maximale Punktzahl einer Anforderung muss 0 oder größer sein."))
					.forEach(results::add);
			validationResults = addRequirementLabels(results.build());
		}
		setValidationResults(validationResults,
				target -> target.kind() != LoeValidationTarget.Kind.TOTAL_POINTS,
				this::validationTargetHref,
				this::showValidationTarget);
		updateDesignState();
	}

	private boolean isSaveAllowed() {
		return !validationResults.hasFailed();
	}

	private ValidationResults<LoeValidationTarget> addRequirementLabels(
			final ValidationResults<LoeValidationTarget> results) {
		final ValidationResults.Builder<LoeValidationTarget> labeledResults = ValidationResults.builder();
		results.forEach(result -> {
			if (result.getTarget().kind() == LoeValidationTarget.Kind.TOTAL_POINTS) {
				labeledResults.add(result);
				return;
			}
			final String prefix = result.getTarget().kind() == LoeValidationTarget.Kind.TASK_POINTS
					? taskLabel(result.getTarget().taskId())
					: requirementLabel(result.getTarget().requirementId());
			final ValidationResult.Builder<LoeValidationTarget> labeledResult = ValidationResult
					.builder(result.getTarget());
			result.getResults().stream()
					.map(testResult -> new TestResult(testResult.severity(), prefix + ": " + testResult.message()))
					.forEach(labeledResult::add);
			labeledResults.add(labeledResult.build());
		});
		return labeledResults.build();
	}

	private String requirementLabel(final Integer requirementId) {
		return requirements.stream().filter(requirement -> requirement.id().equals(requirementId)).findFirst()
				.map(requirement -> {
					final LoeTask task = taskFor(requirement);
					final LoeCategory category = categoryFor(task);
					return "Aufgabe " + taskNumber(tasksFor(category), task) + " · Anforderung "
							+ requirementNumber(requirementsFor(task), requirement);
				})
				.orElse("Anforderung");
	}

	private String taskLabel(final Integer taskId) {
		return tasks.stream().filter(task -> task.id().equals(taskId)).findFirst()
				.map(task -> "Aufgabe " + taskNumber(tasksFor(categoryFor(task)), task))
				.orElse("Aufgabe");
	}

	private String validationTargetHref(final LoeValidationTarget target) {
		return "exams#" + (target.kind() == LoeValidationTarget.Kind.TASK_POINTS
				? taskValidationTargetId(target.taskId())
				: requirementValidationTargetId(target.requirementId()));
	}

	private String requirementValidationTargetId(final Integer requirementId) {
		return "tt-eh-requirement-" + requirementId;
	}

	private String taskValidationTargetId(final Integer taskId) {
		return "tt-eh-task-" + taskId;
	}

	private void showValidationTarget(final LoeValidationTarget target) {
		if (target.kind() == LoeValidationTarget.Kind.TOTAL_POINTS) {
			return;
		}
		if (target.kind() == LoeValidationTarget.Kind.TASK_POINTS) {
			tasks.stream().filter(task -> task.id().equals(target.taskId())).findFirst().ifPresent(task -> {
				final LoeCategory category = categoryFor(task);
				final LoePart part = partFor(category);
				statusTray().peek();
				collapseState.expand(List.of(detailKey("part", part.id()), detailKey("category", category.id()),
						detailKey("task", task.id())));
				viewport.scrollTo(detailKey("task", task.id()));
			});
			return;
		}
		requirementSections.stream()
				.filter(section -> section.requirement().id().equals(target.requirementId())).findFirst()
				.ifPresent(section -> {
					final LoeRequirement requirement = section.requirement();
					final LoeTask task = taskFor(requirement);
					final LoeCategory category = categoryFor(task);
					final LoePart part = partFor(category);
					statusTray().peek();
					collapseState.expand(List.of(detailKey("part", part.id()), detailKey("category", category.id()),
							detailKey("task", task.id())));
					viewport.scrollTo(detailKey("requirement", requirement.id()));
					section.emphasize();
				});
	}

	private void updateDesignState() {
		final DesignState nextState = correctionMode ? DesignState.LOCKED
				: validationResults.hasAny() ? DesignState.INCOMPLETE : DesignState.COMPLETE;
		if (designState == nextState) {
			return;
		}
		designState = nextState;
		designStateChangeHandler.accept(designState);
	}

	private String taskNumber(final List<LoeTask> siblings, final LoeTask task) {
		for (int index = 0; index < siblings.size(); index++) {
			if (siblings.get(index).id().equals(task.id())) {
				return String.valueOf(index + 1);
			}
		}
		throw new IllegalStateException("Missing EH task: " + task.id());
	}
}
