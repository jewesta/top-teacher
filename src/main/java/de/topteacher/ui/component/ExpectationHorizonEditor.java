package de.topteacher.ui.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import de.topteacher.backend.repo.ExpectationHorizonRepository;
import de.topteacher.model.EhCategory;
import de.topteacher.model.EhPart;
import de.topteacher.model.EhRequirement;
import de.topteacher.model.EhTask;
import de.topteacher.model.Exam;

public class ExpectationHorizonEditor extends VerticalLayout {

	private final ExpectationHorizonRepository expectationHorizonRepository;
	private final EhSectionComponents components = new EhSectionComponents();
	private final EhCollapseState collapseState = new EhCollapseState(components);
	private final EhPartSection.Handler partHandler = new EhPartSection.Handler() {

		@Override
		public void saveTitle(final EhPart part, final String title) {
			ExpectationHorizonEditor.this.saveTitle(part, title);
		}

		@Override
		public void addCategory(final EhPart part) {
			ExpectationHorizonEditor.this.addCategory(part);
		}

		@Override
		public void move(final EhPart part, final int offset) {
			ExpectationHorizonEditor.this.move(part, offset);
		}

		@Override
		public void delete(final EhPart part) {
			ExpectationHorizonEditor.this.delete(part);
		}
	};
	private final EhCategorySection.Handler categoryHandler = new EhCategorySection.Handler() {

		@Override
		public void saveTitle(final EhCategory category, final String title) {
			ExpectationHorizonEditor.this.saveTitle(category, title);
		}

		@Override
		public void save(final EhCategory category, final String title, final String descriptionMarkdown) {
			ExpectationHorizonEditor.this.save(category, title, descriptionMarkdown);
		}

		@Override
		public void addTask(final EhCategory category) {
			ExpectationHorizonEditor.this.addTask(category);
		}

		@Override
		public void move(final EhCategory category, final int offset) {
			ExpectationHorizonEditor.this.move(category, offset);
		}

		@Override
		public void delete(final EhCategory category) {
			ExpectationHorizonEditor.this.delete(category);
		}
	};
	private final EhTaskSection.Handler taskHandler = new EhTaskSection.Handler() {

		@Override
		public void saveTitle(final EhTask task, final String title) {
			ExpectationHorizonEditor.this.saveTitle(task, title);
		}

		@Override
		public void addRequirement(final EhTask task) {
			ExpectationHorizonEditor.this.addRequirement(task);
		}

		@Override
		public void move(final EhTask task, final int offset) {
			ExpectationHorizonEditor.this.move(task, offset);
		}

		@Override
		public void delete(final EhTask task) {
			ExpectationHorizonEditor.this.delete(task);
		}
	};
	private final EhRequirementSection.Handler requirementHandler = new EhRequirementSection.Handler() {

		@Override
		public void save(final EhRequirement requirement, final String descriptionMarkdown, final int maxPoints,
				final boolean bonus) {
			ExpectationHorizonEditor.this.save(requirement, descriptionMarkdown, maxPoints, bonus);
		}

		@Override
		public void move(final EhRequirement requirement, final int offset) {
			ExpectationHorizonEditor.this.move(requirement, offset);
		}

		@Override
		public void delete(final EhRequirement requirement) {
			ExpectationHorizonEditor.this.delete(requirement);
		}
	};

	private Exam exam;
	private List<EhPart> parts = List.of();
	private List<EhCategory> categories = List.of();
	private List<EhTask> tasks = List.of();
	private List<EhRequirement> requirements = List.of();
	private EhPointBadge examPointsBadge;
	private List<EhPartSection> partSections = List.of();

	public ExpectationHorizonEditor(final ExpectationHorizonRepository expectationHorizonRepository) {
		this.expectationHorizonRepository = expectationHorizonRepository;

		addClassName("tt-eh-editor");
		setPadding(false);
		setSpacing(false);
		setSizeFull();
	}

	public void setExam(final Exam exam) {
		if (this.exam == null || exam == null || !this.exam.id().equals(exam.id())) {
			collapseState.clear();
		}
		this.exam = exam;
		refresh();
	}

	private void refresh() {
		collapseState.clearRenderedComponents();
		examPointsBadge = null;
		partSections = List.of();
		removeAll();
		if (exam == null) {
			add(new Span("Bitte wählen Sie eine Klausur aus."));
			return;
		}

		loadItems();
		if (ensureRequiredChildren()) {
			loadItems();
		}

		final VerticalLayout content = new VerticalLayout(createToolbar());
		content.addClassName("tt-eh-scroll-area");
		content.setPadding(false);
		content.setSizeFull();

		if (parts.isEmpty()) {
			content.add(emptyState("Noch kein Erwartungshorizont angelegt."));
		} else {
			partSections = parts.stream().map(this::createPartSection).toList();
			partSections.forEach(content::add);
			content.add(expectationHorizonLight());
		}

		add(content);
		expand(content);
	}

	private void loadItems() {
		parts = expectationHorizonRepository.findPartsByExamId(exam.id());
		categories = expectationHorizonRepository.findCategoriesByExamId(exam.id());
		tasks = expectationHorizonRepository.findTasksByExamId(exam.id());
		requirements = expectationHorizonRepository.findRequirementsByExamId(exam.id());
	}

	private boolean ensureRequiredChildren() {
		boolean changed = false;
		for (final EhPart part : parts) {
			if (categoriesFor(part).isEmpty()) {
				addDefaultCategory(part);
				changed = true;
			}
		}
		for (final EhCategory category : categories) {
			if (tasksFor(category).isEmpty()) {
				addDefaultTask(category);
				changed = true;
			}
		}
		for (final EhTask task : tasks) {
			if (requirementsFor(task).isEmpty()) {
				addDefaultRequirement(task);
				changed = true;
			}
		}
		return changed;
	}

	private Component createToolbar() {
		final Button addPart = components.commandButton("Klausurteil hinzufügen", VaadinIcon.PLUS, event -> {
			final int sortOrder = expectationHorizonRepository.nextPartSortOrder(exam.id());
			final EhPart part = expectationHorizonRepository
					.savePart(new EhPart(null, exam.id(), "Klausurteil " + partLetter(sortOrder), sortOrder));
			addDefaultCategory(part);
			refresh();
		});
		addPart.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		examPointsBadge = components.pointBadge("Gesamtpunktzahl", this::pointsForExam);
		final HorizontalLayout toolbar = new HorizontalLayout(addPart, collapseState.toggleButton(allDetailKeys()),
				examPointsBadge);
		toolbar.addClassName("tt-eh-toolbar");
		toolbar.setAlignItems(Alignment.CENTER);
		toolbar.setPadding(false);
		toolbar.setWidthFull();
		return toolbar;
	}

	private EhPartSection createPartSection(final EhPart part) {
		final List<EhCategorySection> categorySections = categoriesFor(part).stream().map(this::createCategorySection)
				.toList();
		final EhPartSection section = new EhPartSection(part, parts, categorySections, components, collapseState,
				partHandler, () -> percentageForPart(part), () -> pointsForPart(part), partDescendantDetailKeys(part));
		collapseState.configure(section.getContent(), detailKey("part", part.id()));
		return section;
	}

	private EhCategorySection createCategorySection(final EhCategory category) {
		final List<EhTaskSection> taskSections = tasksFor(category).stream().map(this::createTaskSection).toList();
		final EhCategorySection section = new EhCategorySection(category, categoriesFor(partFor(category)),
				taskSections, components, collapseState, categoryHandler, () -> pointsForCategory(category),
				categoryDescendantDetailKeys(category));
		collapseState.configure(section.getContent(), detailKey("category", category.id()));
		return section;
	}

	private EhTaskSection createTaskSection(final EhTask task) {
		final List<EhRequirementSection> requirementSections = requirementsFor(task).stream()
				.map(this::createRequirementSection).toList();
		final EhTaskSection section = new EhTaskSection(task, tasksFor(categoryFor(task)), requirementSections,
				components, collapseState, taskHandler, () -> pointsForTask(task),
				List.of(detailKey("task", task.id())));
		collapseState.configure(section.getContent(), detailKey("task", task.id()));
		return section;
	}

	private EhRequirementSection createRequirementSection(final EhRequirement requirement) {
		final List<EhRequirement> siblings = requirementsFor(taskFor(requirement));
		return new EhRequirementSection(requirement, siblings, components, requirementHandler,
				requirementNumber(siblings, requirement), () -> requirementPointsLabelById(requirement.id()));
	}

	private void addDefaultCategory(final EhPart part) {
		final EhCategory category = expectationHorizonRepository.saveCategory(new EhCategory(null, part.id(),
				"Leistungskategorie", "", expectationHorizonRepository.nextCategorySortOrder(part.id())));
		addDefaultTask(category);
	}

	private void addDefaultTask(final EhCategory category) {
		final int sortOrder = expectationHorizonRepository.nextTaskSortOrder(category.id());
		final EhTask task = expectationHorizonRepository
				.saveTask(new EhTask(null, category.id(), "Teilaufgabe " + (sortOrder + 1), sortOrder));
		addDefaultRequirement(task);
	}

	private void addDefaultRequirement(final EhTask task) {
		expectationHorizonRepository.saveRequirement(new EhRequirement(null, task.id(), "", 0, false,
				expectationHorizonRepository.nextRequirementSortOrder(task.id())));
	}

	private List<EhCategory> categoriesFor(final EhPart part) {
		return categories.stream().filter(category -> category.partId().equals(part.id()))
				.sorted(Comparator.comparingInt(EhCategory::sortOrder).thenComparing(EhCategory::id)).toList();
	}

	private List<EhTask> tasksFor(final EhCategory category) {
		return tasks.stream().filter(task -> task.categoryId().equals(category.id()))
				.sorted(Comparator.comparingInt(EhTask::sortOrder).thenComparing(EhTask::id)).toList();
	}

	private List<EhRequirement> requirementsFor(final EhTask task) {
		return requirements.stream().filter(requirement -> requirement.taskId().equals(task.id()))
				.sorted(Comparator.comparingInt(EhRequirement::sortOrder).thenComparing(EhRequirement::id)).toList();
	}

	private EhPart partFor(final EhCategory category) {
		return parts.stream().filter(part -> part.id().equals(category.partId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH part: " + category.partId()));
	}

	private EhCategory categoryFor(final EhTask task) {
		return categories.stream().filter(category -> category.id().equals(task.categoryId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH category: " + task.categoryId()));
	}

	private EhTask taskFor(final EhRequirement requirement) {
		return tasks.stream().filter(task -> task.id().equals(requirement.taskId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH task: " + requirement.taskId()));
	}

	private EhPoints pointsForExam() {
		return sum(requirements);
	}

	private EhPoints pointsForPart(final EhPart part) {
		return sum(categoriesFor(part).stream().flatMap(category -> tasksFor(category).stream())
				.flatMap(task -> requirementsFor(task).stream()).toList());
	}

	private EhPoints pointsForCategory(final EhCategory category) {
		return sum(tasksFor(category).stream().flatMap(task -> requirementsFor(task).stream()).toList());
	}

	private EhPoints pointsForTask(final EhTask task) {
		return sum(requirementsFor(task));
	}

	private EhPoints pointsForRequirement(final EhRequirement requirement) {
		return requirement.bonus() ? new EhPoints(0, requirement.maxPoints())
				: new EhPoints(requirement.maxPoints(), 0);
	}

	private int percentageForPart(final EhPart part) {
		final int totalPoints = pointsForExam().total();
		if (totalPoints == 0) {
			return 0;
		}
		return Math.round(pointsForPart(part).total() * 100.0f / totalPoints);
	}

	private EhPoints sum(final List<EhRequirement> requirements) {
		return requirements.stream().map(this::pointsForRequirement).reduce(new EhPoints(0, 0), EhPoints::plus);
	}

	private Component expectationHorizonLight() {
		final String sunrise = "\uD83C\uDF05"; // U+1F305 sunrise
		final Span text = new Span("Licht am Ende des Erwartungshorizonts " + sunrise);
		final HorizontalLayout gag = new HorizontalLayout(text);
		gag.addClassName("tt-eh-gag");
		gag.setAlignItems(Alignment.CENTER);
		gag.setPadding(false);
		return gag;
	}

	private String requirementPointsLabelById(final Integer id) {
		return requirements.stream().filter(requirement -> requirement.id().equals(id)).findFirst()
				.map(this::requirementPointsLabel).orElse("0");
	}

	private String requirementPointsLabel(final EhRequirement requirement) {
		return requirement.bonus() ? "(+ " + requirement.maxPoints() + ")" : String.valueOf(requirement.maxPoints());
	}

	private String requirementNumber(final List<EhRequirement> siblings, final EhRequirement requirement) {
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

	private List<String> partDescendantDetailKeys(final EhPart part) {
		final List<String> keys = new ArrayList<>();
		for (final EhCategory category : categoriesFor(part)) {
			keys.add(detailKey("category", category.id()));
			tasksFor(category).forEach(task -> keys.add(detailKey("task", task.id())));
		}
		return keys;
	}

	private List<String> categoryDescendantDetailKeys(final EhCategory category) {
		return tasksFor(category).stream().map(task -> detailKey("task", task.id())).toList();
	}

	private EhCategory categoryById(final Integer id) {
		return categories.stream().filter(category -> category.id().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing EH category: " + id));
	}

	private void replacePart(final EhPart part) {
		parts = parts.stream().map(currentPart -> currentPart.id().equals(part.id()) ? part : currentPart).toList();
	}

	private void replaceCategory(final EhCategory category) {
		categories = categories.stream()
				.map(currentCategory -> currentCategory.id().equals(category.id()) ? category : currentCategory)
				.toList();
	}

	private void replaceTask(final EhTask task) {
		tasks = tasks.stream().map(currentTask -> currentTask.id().equals(task.id()) ? task : currentTask).toList();
	}

	private void replaceRequirement(final EhRequirement requirement) {
		requirements = requirements.stream()
				.map(currentRequirement -> currentRequirement.id().equals(requirement.id()) ? requirement
						: currentRequirement)
				.toList();
	}

	private void saveTitle(final EhPart part, final String title) {
		final EhPart updatedPart = new EhPart(part.id(), part.examId(), title, part.sortOrder());
		expectationHorizonRepository.savePart(updatedPart);
		replacePart(updatedPart);
	}

	private void addCategory(final EhPart part) {
		addDefaultCategory(part);
		refresh();
	}

	private void move(final EhPart part, final int offset) {
		expectationHorizonRepository.movePart(part, offset);
		refresh();
	}

	private void delete(final EhPart part) {
		expectationHorizonRepository.deletePart(part.id());
		refresh();
	}

	private void saveTitle(final EhCategory category, final String title) {
		final EhCategory currentCategory = categoryById(category.id());
		final EhCategory updatedCategory = new EhCategory(category.id(), category.partId(), title,
				currentCategory.descriptionMarkdown(), category.sortOrder());
		expectationHorizonRepository.saveCategory(updatedCategory);
		replaceCategory(updatedCategory);
	}

	private void save(final EhCategory category, final String title, final String descriptionMarkdown) {
		final EhCategory updatedCategory = new EhCategory(category.id(), category.partId(), title, descriptionMarkdown,
				category.sortOrder());
		expectationHorizonRepository.saveCategory(updatedCategory);
		replaceCategory(updatedCategory);
	}

	private void addTask(final EhCategory category) {
		addDefaultTask(category);
		refresh();
	}

	private void move(final EhCategory category, final int offset) {
		expectationHorizonRepository.moveCategory(category, categoriesFor(partFor(category)), offset);
		refresh();
	}

	private void delete(final EhCategory category) {
		expectationHorizonRepository.deleteCategory(category.id());
		refresh();
	}

	private void saveTitle(final EhTask task, final String title) {
		final EhTask updatedTask = new EhTask(task.id(), task.categoryId(), title, task.sortOrder());
		expectationHorizonRepository.saveTask(updatedTask);
		replaceTask(updatedTask);
	}

	private void addRequirement(final EhTask task) {
		addDefaultRequirement(task);
		refresh();
	}

	private void move(final EhTask task, final int offset) {
		expectationHorizonRepository.moveTask(task, tasksFor(categoryFor(task)), offset);
		refresh();
	}

	private void delete(final EhTask task) {
		expectationHorizonRepository.deleteTask(task.id());
		refresh();
	}

	private void save(final EhRequirement requirement, final String descriptionMarkdown, final int maxPoints,
			final boolean bonus) {
		final EhRequirement updatedRequirement = new EhRequirement(requirement.id(), requirement.taskId(),
				descriptionMarkdown, maxPoints, bonus, requirement.sortOrder());
		expectationHorizonRepository.saveRequirement(updatedRequirement);
		replaceRequirement(updatedRequirement);
		refreshBadges();
	}

	private void move(final EhRequirement requirement, final int offset) {
		expectationHorizonRepository.moveRequirement(requirement, requirementsFor(taskFor(requirement)), offset);
		refresh();
	}

	private void delete(final EhRequirement requirement) {
		expectationHorizonRepository.deleteRequirement(requirement.id());
		refresh();
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
		partSections.forEach(EhPartSection::refreshBadges);
	}

	private String partLetter(final int index) {
		return String.valueOf((char) ('A' + Math.min(index, 25)));
	}
}
