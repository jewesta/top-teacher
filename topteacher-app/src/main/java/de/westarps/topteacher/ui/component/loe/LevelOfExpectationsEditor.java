package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.FullscreenButton;

public class LevelOfExpectationsEditor extends AbstractDesigner {

	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final FullscreenButton fullscreenButton;
	private final LoeSaveController saveController = new LoeSaveController();
	private final LoeSectionComponents components = new LoeSectionComponents(saveController);
	private final LoeCollapseState collapseState = new LoeCollapseState(components);
	private final LoePartSection.Handler partHandler = new LoePartSection.Handler() {

		@Override
		public void saveTitle(final LoePart part, final String title) {
			LevelOfExpectationsEditor.this.saveTitle(part, title);
		}

		@Override
		public void addCategory(final LoePart part) {
			LevelOfExpectationsEditor.this.addCategory(part);
		}

		@Override
		public void move(final LoePart part, final int offset) {
			LevelOfExpectationsEditor.this.move(part, offset);
		}

		@Override
		public void delete(final LoePart part) {
			LevelOfExpectationsEditor.this.delete(part);
		}
	};
	private final LoeCategorySection.Handler categoryHandler = new LoeCategorySection.Handler() {

		@Override
		public void saveTitle(final LoeCategory category, final String title) {
			LevelOfExpectationsEditor.this.saveTitle(category, title);
		}

		@Override
		public void save(final LoeCategory category, final String title, final String descriptionMarkdown) {
			LevelOfExpectationsEditor.this.save(category, title, descriptionMarkdown);
		}

		@Override
		public void addTask(final LoeCategory category) {
			LevelOfExpectationsEditor.this.addTask(category);
		}

		@Override
		public void move(final LoeCategory category, final int offset) {
			LevelOfExpectationsEditor.this.move(category, offset);
		}

		@Override
		public void delete(final LoeCategory category) {
			LevelOfExpectationsEditor.this.delete(category);
		}
	};
	private final LoeTaskSection.Handler taskHandler = new LoeTaskSection.Handler() {

		@Override
		public void saveTitle(final LoeTask task, final String title) {
			LevelOfExpectationsEditor.this.saveTitle(task, title);
		}

		@Override
		public void addRequirement(final LoeTask task) {
			LevelOfExpectationsEditor.this.addRequirement(task);
		}

		@Override
		public void move(final LoeTask task, final int offset) {
			LevelOfExpectationsEditor.this.move(task, offset);
		}

		@Override
		public void delete(final LoeTask task) {
			LevelOfExpectationsEditor.this.delete(task);
		}
	};
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
	private List<LoePartSection> partSections = List.of();
	private boolean correctionMode;
	private Runnable changeHandler = () -> {
	};

	public LevelOfExpectationsEditor(final LevelOfExpectationsRepository levelOfExpectationsRepository) {
		super("tt-eh-editor");
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
		fullscreenButton = new FullscreenButton(this);

		saveController.setDirtySupplier(this::isDirty);
		saveController.setSaveAction(this::saveDirtySections);
		saveController.setDiscardAction(this::discardDirtySections);
	}

	public void setExam(final Exam exam) {
		if (this.exam == null || exam == null || !this.exam.id().equals(exam.id())) {
			collapseState.clear();
		}
		this.exam = exam;
		refresh();
	}

	public void setChangeHandler(final Runnable changeHandler) {
		this.changeHandler = changeHandler == null ? () -> {
		} : changeHandler;
	}

	private void refresh() {
		saveController.clearButtons();
		collapseState.clearRenderedComponents();
		examPointsBadge = null;
		partSections = List.of();
		resetDesigner();
		if (exam == null) {
			correctionMode = false;
			showDesignerMessage(new Span("Bitte wähle eine Klausur aus."));
			return;
		}

		correctionMode = levelOfExpectationsRepository.hasResultsForExam(exam.id());
		loadItems();
		if (!correctionMode && ensureRequiredChildren()) {
			loadItems();
		}

		if (parts.isEmpty()) {
			content().add(emptyState("Noch kein Erwartungshorizont angelegt."));
		} else {
			partSections = parts.stream().map(this::createPartSection).toList();
			partSections.forEach(content()::add);
			content().add(levelOfExpectationsLight());
		}

		configureToolbar();
		showDesigner();
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
		for (final LoePart part : parts) {
			if (categoriesFor(part).isEmpty()) {
				addDefaultCategory(part);
				changed = true;
			}
		}
		for (final LoeCategory category : categories) {
			if (tasksFor(category).isEmpty()) {
				addDefaultTask(category);
				changed = true;
			}
		}
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
		final Button addPart = components.commandButton("Klausurteil hinzufügen", VaadinIcon.PLUS, event -> {
			final int sortOrder = levelOfExpectationsRepository.nextPartSortOrder(exam.id());
			final LoePart part = levelOfExpectationsRepository
					.savePart(new LoePart(null, exam.id(), "Klausurteil " + partLetter(sortOrder), sortOrder));
			addDefaultCategory(part);
			refresh();
			notifyChanged();
		});
		if (correctionMode) {
			components.lockCorrectionModeAction(addPart);
		}

		examPointsBadge = components.pointBadge("Gesamt", this::pointsForExam);
		toolbar().add(save, discard, addPart, collapseState.toggleButton(allDetailKeys()), fullscreenButton);
		toolbarSummary().add(examPointsBadge);
	}

	private LoePartSection createPartSection(final LoePart part) {
		final List<LoeCategorySection> categorySections = categoriesFor(part).stream().map(this::createCategorySection)
				.toList();
		final LoePartSection section = new LoePartSection(part, parts, categorySections, components, collapseState,
				partHandler, () -> percentageForPart(part), () -> pointsForPart(part), partDescendantDetailKeys(part),
				correctionMode);
		collapseState.configure(section.getContent(), detailKey("part", part.id()));
		return section;
	}

	private LoeCategorySection createCategorySection(final LoeCategory category) {
		final List<LoeTaskSection> taskSections = tasksFor(category).stream().map(this::createTaskSection).toList();
		final LoeCategorySection section = new LoeCategorySection(category, categoriesFor(partFor(category)),
				taskSections, components, collapseState, categoryHandler, () -> pointsForCategory(category),
				categoryDescendantDetailKeys(category), correctionMode);
		collapseState.configure(section.getContent(), detailKey("category", category.id()));
		return section;
	}

	private LoeTaskSection createTaskSection(final LoeTask task) {
		final List<LoeRequirementSection> requirementSections = requirementsFor(task).stream()
				.map(this::createRequirementSection).toList();
		final LoeTaskSection section = new LoeTaskSection(task, tasksFor(categoryFor(task)), requirementSections,
				components, collapseState, taskHandler, () -> pointsForTask(task),
				List.of(detailKey("task", task.id())), correctionMode);
		collapseState.configure(section.getContent(), detailKey("task", task.id()));
		return section;
	}

	private LoeRequirementSection createRequirementSection(final LoeRequirement requirement) {
		final List<LoeRequirement> siblings = requirementsFor(taskFor(requirement));
		return new LoeRequirementSection(requirement, siblings, components, requirementHandler,
				requirementNumber(siblings, requirement), correctionMode);
	}

	private void addDefaultCategory(final LoePart part) {
		final LoeCategory category = levelOfExpectationsRepository.saveCategory(new LoeCategory(null, part.id(),
				"Leistungskategorie", "", levelOfExpectationsRepository.nextCategorySortOrder(part.id())));
		addDefaultTask(category);
	}

	private void addDefaultTask(final LoeCategory category) {
		final int sortOrder = levelOfExpectationsRepository.nextTaskSortOrder(category.id());
		final LoeTask task = levelOfExpectationsRepository
				.saveTask(new LoeTask(null, category.id(), "Teilaufgabe " + (sortOrder + 1), sortOrder));
		addDefaultRequirement(task);
	}

	private void addDefaultRequirement(final LoeTask task) {
		levelOfExpectationsRepository.saveRequirement(new LoeRequirement(null, task.id(), "", 0, false,
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
		return sum(requirements);
	}

	private LoePoints pointsForPart(final LoePart part) {
		return sum(categoriesFor(part).stream().flatMap(category -> tasksFor(category).stream())
				.flatMap(task -> requirementsFor(task).stream()).toList());
	}

	private LoePoints pointsForCategory(final LoeCategory category) {
		return sum(tasksFor(category).stream().flatMap(task -> requirementsFor(task).stream()).toList());
	}

	private LoePoints pointsForTask(final LoeTask task) {
		return sum(requirementsFor(task));
	}

	private LoePoints pointsForRequirement(final LoeRequirement requirement) {
		return requirement.bonus() ? new LoePoints(0, requirement.maxPoints())
				: new LoePoints(requirement.maxPoints(), 0);
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
		return type + ":" + id;
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

	private LoeCategory categoryById(final Integer id) {
		return categories.stream().filter(category -> category.id().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH category: " + id));
	}

	private void replacePart(final LoePart part) {
		parts = parts.stream().map(currentPart -> currentPart.id().equals(part.id()) ? part : currentPart).toList();
	}

	private void replaceCategory(final LoeCategory category) {
		categories = categories.stream()
				.map(currentCategory -> currentCategory.id().equals(category.id()) ? category : currentCategory)
				.toList();
	}

	private void replaceTask(final LoeTask task) {
		tasks = tasks.stream().map(currentTask -> currentTask.id().equals(task.id()) ? task : currentTask).toList();
	}

	private void replaceRequirement(final LoeRequirement requirement) {
		requirements = requirements.stream()
				.map(currentRequirement -> currentRequirement.id().equals(requirement.id()) ? requirement
						: currentRequirement)
				.toList();
	}

	private void saveTitle(final LoePart part, final String title) {
		final LoePart updatedPart = new LoePart(part.id(), part.examId(), title, part.sortOrder());
		levelOfExpectationsRepository.savePart(updatedPart);
		replacePart(updatedPart);
	}

	private void addCategory(final LoePart part) {
		addDefaultCategory(part);
		refresh();
		notifyChanged();
	}

	private void move(final LoePart part, final int offset) {
		levelOfExpectationsRepository.movePart(part, offset);
		refresh();
		notifyChanged();
	}

	private void delete(final LoePart part) {
		deleteSafely(() -> levelOfExpectationsRepository.deletePart(part.id()));
	}

	private void saveTitle(final LoeCategory category, final String title) {
		final LoeCategory currentCategory = categoryById(category.id());
		final LoeCategory updatedCategory = new LoeCategory(category.id(), category.partId(), title,
				currentCategory.descriptionMarkdown(), category.sortOrder());
		levelOfExpectationsRepository.saveCategory(updatedCategory);
		replaceCategory(updatedCategory);
	}

	private void save(final LoeCategory category, final String title, final String descriptionMarkdown) {
		final LoeCategory updatedCategory = new LoeCategory(category.id(), category.partId(), title,
				descriptionMarkdown, category.sortOrder());
		levelOfExpectationsRepository.saveCategory(updatedCategory);
		replaceCategory(updatedCategory);
	}

	private void addTask(final LoeCategory category) {
		addDefaultTask(category);
		refresh();
		notifyChanged();
	}

	private void move(final LoeCategory category, final int offset) {
		levelOfExpectationsRepository.moveCategory(category, categoriesFor(partFor(category)), offset);
		refresh();
		notifyChanged();
	}

	private void delete(final LoeCategory category) {
		deleteSafely(() -> levelOfExpectationsRepository.deleteCategory(category.id()));
	}

	private void saveTitle(final LoeTask task, final String title) {
		final LoeTask updatedTask = new LoeTask(task.id(), task.categoryId(), title, task.sortOrder());
		levelOfExpectationsRepository.saveTask(updatedTask);
		replaceTask(updatedTask);
	}

	private void addRequirement(final LoeTask task) {
		addDefaultRequirement(task);
		refresh();
		notifyChanged();
	}

	private void move(final LoeTask task, final int offset) {
		levelOfExpectationsRepository.moveTask(task, tasksFor(categoryFor(task)), offset);
		refresh();
		notifyChanged();
	}

	private void delete(final LoeTask task) {
		deleteSafely(() -> levelOfExpectationsRepository.deleteTask(task.id()));
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
		partSections.forEach(LoePartSection::refreshBadges);
	}

	private boolean isDirty() {
		return partSections.stream().anyMatch(LoePartSection::isDirty);
	}

	private void saveDirtySections() {
		try {
			for (final LoePartSection partSection : partSections) {
				if (!partSection.save()) {
					return;
				}
			}
		} catch (final IllegalStateException exception) {
			Notification.show(exception.getMessage());
			return;
		}
		refreshBadges();
		notifyChanged();
	}

	private void discardDirtySections() {
		refresh();
	}

	private void notifyChanged() {
		changeHandler.run();
	}

	private String partLetter(final int index) {
		return String.valueOf((char) ('A' + Math.min(index, 25)));
	}
}
