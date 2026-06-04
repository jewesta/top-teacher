package de.topteacher.ui.component;

import java.util.Comparator;
import java.util.List;

import com.flowingcode.vaadin.addons.markdown.MarkdownEditor;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.backend.repo.ExpectationHorizonRepository;
import de.topteacher.model.EhCategory;
import de.topteacher.model.EhPart;
import de.topteacher.model.EhRequirement;
import de.topteacher.model.EhTask;
import de.topteacher.model.Exam;

public class ExpectationHorizonEditor extends VerticalLayout {

	private final ExpectationHorizonRepository expectationHorizonRepository;

	private Exam exam;
	private List<EhPart> parts = List.of();
	private List<EhCategory> categories = List.of();
	private List<EhTask> tasks = List.of();
	private List<EhRequirement> requirements = List.of();

	public ExpectationHorizonEditor(final ExpectationHorizonRepository expectationHorizonRepository) {
		this.expectationHorizonRepository = expectationHorizonRepository;

		addClassName("tt-eh-editor");
		setPadding(false);
		setSpacing(false);
		setSizeFull();
	}

	public void setExam(final Exam exam) {
		this.exam = exam;
		refresh();
	}

	private void refresh() {
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
			parts.forEach(part -> content.add(createPartDetails(part)));
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
		final Button addPart = commandButton("Klausurteil hinzufügen", VaadinIcon.PLUS, event -> {
			final int sortOrder = expectationHorizonRepository.nextPartSortOrder(exam.id());
			final EhPart part = expectationHorizonRepository
					.savePart(new EhPart(null, exam.id(), "Klausurteil " + partLetter(sortOrder), sortOrder));
			addDefaultCategory(part);
			refresh();
		});
		addPart.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		final HorizontalLayout toolbar = new HorizontalLayout(addPart, summaryBadge("Gesamtpunktzahl", pointsForExam()));
		toolbar.addClassName("tt-eh-toolbar");
		toolbar.setAlignItems(Alignment.CENTER);
		toolbar.setPadding(false);
		toolbar.setWidthFull();
		return toolbar;
	}

	private Details createPartDetails(final EhPart part) {
		final List<EhPart> siblings = parts;
		final TextField title = summaryTitleField(part.title());
		title.addValueChangeListener(event -> {
			if (!event.isFromClient()) {
				return;
			}
			if (isBlank(event.getValue())) {
				Notification.show("Titel ist erforderlich.");
				title.setValue(part.title());
				return;
			}
			expectationHorizonRepository.savePart(new EhPart(part.id(), part.examId(), event.getValue(), part.sortOrder()));
		});
		final VerticalLayout content = sectionContent();
		content.add(createEditorActions(title, part, siblings));
		categoriesFor(part).forEach(category -> content.add(createCategoryDetails(category)));

		final Details details = new Details(summary("Klausurteil", title, pointsForPart(part)), content);
		details.addClassNames("tt-eh-details", "tt-eh-part");
		details.setOpened(true);
		return details;
	}

	private Component createEditorActions(final TextField title, final EhPart part, final List<EhPart> siblings) {
		final Button save = saveButton(event -> {
			if (isBlank(title.getValue())) {
				Notification.show("Titel ist erforderlich.");
				return;
			}
			expectationHorizonRepository.savePart(new EhPart(part.id(), part.examId(), title.getValue(), part.sortOrder()));
			refresh();
		});
		final Button addCategory = commandButton("Leistungskategorie hinzufügen", VaadinIcon.PLUS, event -> {
			addDefaultCategory(part);
			refresh();
		});
		final HorizontalLayout actions = new HorizontalLayout(save, addCategory, moveButton("Nach oben", -1, siblings, part),
				moveButton("Nach unten", 1, siblings, part), deleteButton(event -> {
					expectationHorizonRepository.deletePart(part.id());
					refresh();
				}));
		actions.addClassName("tt-eh-actions");
		actions.setPadding(false);

		final VerticalLayout layout = new VerticalLayout(actions);
		layout.addClassName("tt-eh-editor-block");
		layout.setPadding(false);
		layout.setWidthFull();
		return layout;
	}

	private Details createCategoryDetails(final EhCategory category) {
		final List<EhCategory> siblings = categoriesFor(partFor(category));
		final TextField title = summaryTitleField(category.title());
		title.addValueChangeListener(event -> {
			if (!event.isFromClient()) {
				return;
			}
			if (isBlank(event.getValue())) {
				Notification.show("Titel ist erforderlich.");
				title.setValue(category.title());
				return;
			}
			expectationHorizonRepository.saveCategory(new EhCategory(category.id(), category.partId(), event.getValue(),
					category.descriptionMarkdown(), category.sortOrder()));
		});
		final MarkdownEditor description = markdownEditor(category.descriptionMarkdown(), "Beschreibung");
		final VerticalLayout content = sectionContent();
		content.add(markdownBlock("Beschreibung", description), createCategoryActions(title, description, category, siblings));
		tasksFor(category).forEach(task -> content.add(createTaskDetails(task)));

		final Details details = new Details(summary("Leistungskategorie", title, pointsForCategory(category)),
				content);
		details.addClassNames("tt-eh-details", "tt-eh-category");
		details.setOpened(true);
		return details;
	}

	private Component createCategoryActions(final TextField title, final MarkdownEditor description,
			final EhCategory category, final List<EhCategory> siblings) {
		final Button save = saveButton(event -> {
			if (isBlank(title.getValue())) {
				Notification.show("Titel ist erforderlich.");
				return;
			}
			expectationHorizonRepository.saveCategory(new EhCategory(category.id(), category.partId(), title.getValue(),
					value(description), category.sortOrder()));
			refresh();
		});
		final Button addTask = commandButton("Teilaufgabe hinzufügen", VaadinIcon.PLUS, event -> {
			addDefaultTask(category);
			refresh();
		});
		final HorizontalLayout actions = new HorizontalLayout(save, addTask, moveButton("Nach oben", -1, siblings, category),
				moveButton("Nach unten", 1, siblings, category), deleteButton(event -> {
					expectationHorizonRepository.deleteCategory(category.id());
					refresh();
				}));
		actions.addClassName("tt-eh-actions");
		actions.setPadding(false);

		final VerticalLayout layout = new VerticalLayout(actions);
		layout.addClassName("tt-eh-editor-block");
		layout.setPadding(false);
		layout.setWidthFull();
		return layout;
	}

	private Details createTaskDetails(final EhTask task) {
		final List<EhTask> siblings = tasksFor(categoryFor(task));
		final TextField title = summaryTitleField(task.title());
		title.addValueChangeListener(event -> {
			if (!event.isFromClient()) {
				return;
			}
			if (isBlank(event.getValue())) {
				Notification.show("Titel ist erforderlich.");
				title.setValue(task.title());
				return;
			}
			expectationHorizonRepository.saveTask(new EhTask(task.id(), task.categoryId(), event.getValue(), task.sortOrder()));
		});
		final VerticalLayout content = sectionContent();
		content.add(createTaskActions(title, task, siblings));
		requirementsFor(task).forEach(requirement -> content.add(createRequirementEditor(requirement)));

		final Details details = new Details(summary("Teilaufgabe", title, pointsForTask(task)), content);
		details.addClassNames("tt-eh-details", "tt-eh-task");
		details.setOpened(true);
		return details;
	}

	private Component createTaskActions(final TextField title, final EhTask task, final List<EhTask> siblings) {
		final Button save = saveButton(event -> {
			if (isBlank(title.getValue())) {
				Notification.show("Titel ist erforderlich.");
				return;
			}
			expectationHorizonRepository.saveTask(new EhTask(task.id(), task.categoryId(), title.getValue(), task.sortOrder()));
			refresh();
		});
		final Button addRequirement = commandButton("Anforderung hinzufügen", VaadinIcon.PLUS, event -> {
			addDefaultRequirement(task);
			refresh();
		});
		final HorizontalLayout actions = new HorizontalLayout(save, addRequirement, moveButton("Nach oben", -1, siblings, task),
				moveButton("Nach unten", 1, siblings, task), deleteButton(event -> {
					expectationHorizonRepository.deleteTask(task.id());
					refresh();
				}));
		actions.addClassName("tt-eh-actions");
		actions.setPadding(false);

		final VerticalLayout layout = new VerticalLayout(actions);
		layout.addClassName("tt-eh-editor-block");
		layout.setPadding(false);
		layout.setWidthFull();
		return layout;
	}

	private Component createRequirementEditor(final EhRequirement requirement) {
		final List<EhRequirement> siblings = requirementsFor(taskFor(requirement));
		final MarkdownEditor description = markdownEditor(requirement.descriptionMarkdown(), "Beschreibung");
		final IntegerField maxPoints = new IntegerField("Max. Punkte");
		maxPoints.setMin(0);
		maxPoints.setStepButtonsVisible(true);
		maxPoints.setValue(requirement.maxPoints());
		maxPoints.setWidth("10rem");

		final Checkbox bonus = new Checkbox("Bonusaufgabe", requirement.bonus());
		final Button save = saveButton(event -> {
			if (maxPoints.getValue() == null || maxPoints.getValue() < 0) {
				Notification.show("Max. Punkte müssen 0 oder größer sein.");
				return;
			}
			expectationHorizonRepository.saveRequirement(new EhRequirement(requirement.id(), requirement.taskId(),
					value(description), maxPoints.getValue(), bonus.getValue(), requirement.sortOrder()));
			refresh();
		});

		final HorizontalLayout fields = new HorizontalLayout(maxPoints, bonus);
		fields.addClassName("tt-eh-requirement-fields");
		fields.setAlignItems(Alignment.END);
		fields.setPadding(false);

		final HorizontalLayout actions = new HorizontalLayout(save, moveButton("Nach oben", -1, siblings, requirement),
				moveButton("Nach unten", 1, siblings, requirement), deleteButton(event -> {
					expectationHorizonRepository.deleteRequirement(requirement.id());
					refresh();
				}));
		actions.addClassName("tt-eh-actions");
		actions.setPadding(false);

		final VerticalLayout editor = new VerticalLayout(summary("Anforderung", requirementSummary(requirement),
				pointsForRequirement(requirement)), markdownBlock("Beschreibung", description), fields, actions);
		editor.addClassName("tt-eh-requirement");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
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

	private Points pointsForExam() {
		return sum(requirements);
	}

	private Points pointsForPart(final EhPart part) {
		return sum(categoriesFor(part).stream().flatMap(category -> tasksFor(category).stream())
				.flatMap(task -> requirementsFor(task).stream()).toList());
	}

	private Points pointsForCategory(final EhCategory category) {
		return sum(tasksFor(category).stream().flatMap(task -> requirementsFor(task).stream()).toList());
	}

	private Points pointsForTask(final EhTask task) {
		return sum(requirementsFor(task));
	}

	private Points pointsForRequirement(final EhRequirement requirement) {
		return requirement.bonus() ? new Points(0, requirement.maxPoints()) : new Points(requirement.maxPoints(), 0);
	}

	private Points sum(final List<EhRequirement> requirements) {
		return requirements.stream().map(this::pointsForRequirement).reduce(new Points(0, 0), Points::plus);
	}

	private Component summary(final String type, final String title, final Points points) {
		final Span titleLabel = new Span(title);
		titleLabel.addClassName("tt-eh-summary-title");
		return summary(type, titleLabel, points);
	}

	private Component summary(final String type, final Component title, final Points points) {
		final Span typeLabel = new Span(type);
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title, summaryBadge("Summe", points));
		summary.addClassName("tt-eh-summary");
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	private Span summaryBadge(final String label, final Points points) {
		final Span badge = new Span(label + ": " + points.label());
		badge.addClassName("tt-eh-points");
		return badge;
	}

	private String requirementSummary(final EhRequirement requirement) {
		final String value = requirement.descriptionMarkdown().strip();
		if (value.isBlank()) {
			return "Neue Anforderung";
		}
		final String firstLine = value.lines().findFirst().orElse(value);
		return firstLine.length() <= 80 ? firstLine : firstLine.substring(0, 77) + "...";
	}

	private VerticalLayout sectionContent() {
		final VerticalLayout layout = new VerticalLayout();
		layout.addClassName("tt-eh-section-content");
		layout.setPadding(false);
		layout.setWidthFull();
		return layout;
	}

	private TextField summaryTitleField(final String value) {
		final TextField field = new TextField();
		field.addClassName("tt-eh-summary-title-field");
		field.setValue(value);
		field.setWidthFull();
		field.getElement().setAttribute("aria-label", "Titel");
		field.addAttachListener(event -> field.getElement().executeJs("""
				this.addEventListener('click', event => event.stopPropagation());
				this.addEventListener('keydown', event => event.stopPropagation());
				"""));
		return field;
	}

	private MarkdownEditor markdownEditor(final String value, final String placeholder) {
		final MarkdownEditor editor = new MarkdownEditor(value == null ? "" : value);
		editor.addClassName("tt-markdown-editor");
		editor.setPlaceholder(placeholder);
		editor.setWidthFull();
		editor.setHeight("14rem");
		return editor;
	}

	private Component markdownBlock(final String label, final MarkdownEditor editor) {
		final Span editorLabel = new Span(label);
		editorLabel.addClassName("tt-field-label");

		final VerticalLayout block = new VerticalLayout(editorLabel, editor);
		block.addClassName("tt-markdown-block");
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	private Button saveButton(final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = commandButton("Speichern", VaadinIcon.CHECK, listener);
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		return button;
	}

	private <T> Button moveButton(final String label, final int offset, final List<T> siblings, final T item) {
		final Button button = iconButton(label, offset < 0 ? VaadinIcon.ARROW_UP : VaadinIcon.ARROW_DOWN, event -> {
			if (item instanceof final EhPart part) {
				expectationHorizonRepository.movePart(part, offset);
			} else if (item instanceof final EhCategory category) {
				expectationHorizonRepository.moveCategory(category, castCategories(siblings), offset);
			} else if (item instanceof final EhTask task) {
				expectationHorizonRepository.moveTask(task, castTasks(siblings), offset);
			} else if (item instanceof final EhRequirement requirement) {
				expectationHorizonRepository.moveRequirement(requirement, castRequirements(siblings), offset);
			}
			refresh();
		});
		final int index = siblings.indexOf(item);
		button.setEnabled(index >= 0 && index + offset >= 0 && index + offset < siblings.size());
		return button;
	}

	private Button deleteButton(final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = iconButton("Löschen", VaadinIcon.TRASH, listener);
		button.addThemeVariants(ButtonVariant.LUMO_ERROR);
		return button;
	}

	private Button commandButton(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = new Button(text, icon.create(), listener);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL);
		return button;
	}

	private Button iconButton(final String label, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = new Button(icon.create(), listener);
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		return button;
	}

	private Component emptyState(final String text) {
		final Span emptyState = new Span(text);
		emptyState.addClassName("tt-empty-state");
		return emptyState;
	}

	private String value(final MarkdownEditor editor) {
		return editor.getValue() == null ? "" : editor.getValue();
	}

	private boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	private String partLetter(final int index) {
		return String.valueOf((char) ('A' + Math.min(index, 25)));
	}

	@SuppressWarnings("unchecked")
	private List<EhCategory> castCategories(final List<?> values) {
		return (List<EhCategory>) values;
	}

	@SuppressWarnings("unchecked")
	private List<EhTask> castTasks(final List<?> values) {
		return (List<EhTask>) values;
	}

	@SuppressWarnings("unchecked")
	private List<EhRequirement> castRequirements(final List<?> values) {
		return (List<EhRequirement>) values;
	}

	private record Points(int regular, int bonus) {

		Points plus(final Points other) {
			return new Points(regular + other.regular, bonus + other.bonus);
		}

		String label() {
			if (bonus == 0) {
				return regular + " P.";
			}
			return regular + " P. + " + bonus + " Bonus";
		}
	}
}
