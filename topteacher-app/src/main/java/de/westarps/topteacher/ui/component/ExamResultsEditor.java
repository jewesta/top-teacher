package de.westarps.topteacher.ui.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExpectationHorizonRepository;
import de.westarps.topteacher.model.EhCategory;
import de.westarps.topteacher.model.EhCriterion;
import de.westarps.topteacher.model.EhCriterionResult;
import de.westarps.topteacher.model.EhPart;
import de.westarps.topteacher.model.EhRequirement;
import de.westarps.topteacher.model.EhRequirementResult;
import de.westarps.topteacher.model.EhTask;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.vaadin.markdown.MarkdownViewer;
import de.westarps.vaadin.markdown.MarkdownTagRenderMode;

public class ExamResultsEditor extends VerticalLayout {

	private final CourseRepository courseRepository;
	private final ExpectationHorizonRepository expectationHorizonRepository;
	private final StepperComboBox<Pupil> pupilSelector = new StepperComboBox<>();
	private final Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
	private final VerticalLayout results = new VerticalLayout();
	private final EhSaveController saveController = new EhSaveController();
	private final List<EhPointBadge> pointBadges = new ArrayList<>();
	private final Map<Integer, IntegerField> requirementPointFields = new HashMap<>();
	private final Map<Integer, MarkdownViewer> requirementDescriptions = new HashMap<>();
	private final Map<Integer, Boolean> editedCriterionResults = new HashMap<>();
	private final Map<Integer, Integer> editedRequirementResults = new HashMap<>();

	private Exam exam;
	private Pupil selectedPupil;
	private boolean refreshing;
	private boolean applyingResultState;
	private List<EhPart> parts = List.of();
	private List<EhCategory> categories = List.of();
	private List<EhTask> tasks = List.of();
	private List<EhRequirement> requirements = List.of();
	private List<EhCriterion> criteria = List.of();
	private EhPointBadge examPointsBadge;
	private Map<Integer, Boolean> persistedCriterionResults = Map.of();
	private Map<Integer, Integer> persistedRequirementResults = Map.of();

	public ExamResultsEditor(final CourseRepository courseRepository,
			final ExpectationHorizonRepository expectationHorizonRepository) {
		this.courseRepository = courseRepository;
		this.expectationHorizonRepository = expectationHorizonRepository;

		addClassName("tt-exam-results-editor");
		setPadding(false);
		setSpacing(false);
		setSizeFull();

		configurePupilSelector();
		configureSaveButton();
		configureResults();
	}

	public void setExam(final Exam exam) {
		if (this.exam == null || exam == null || !this.exam.id().equals(exam.id())) {
			selectedPupil = null;
		}
		this.exam = exam;
		refresh();
	}

	private void configurePupilSelector() {
		pupilSelector.setItemLabelGenerator(this::pupilLabel);
		pupilSelector.setWidth("24rem");
		pupilSelector.setMaxWidth("100%");
		pupilSelector.setAriaLabel("Schüler");
		pupilSelector.addValueChangeListener(event -> {
			if (refreshing) {
				selectedPupil = event.getValue();
				return;
			}
			if (isDirty()) {
				Notification.show("Bitte speichern Sie die Ergebnisse zuerst.");
				refreshing = true;
				pupilSelector.setValue(event.getOldValue());
				refreshing = false;
				return;
			}
			selectedPupil = event.getValue();
			loadSelectedPupilResults();
		});
	}

	private void configureSaveButton() {
		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
		saveButton.addClickListener(event -> saveController.save());
		saveController.setDirtySupplier(this::isDirty);
		saveController.setSaveAction(this::saveResults);
		saveController.register(saveButton);
	}

	private void configureResults() {
		results.addClassName("tt-results-list");
		results.setPadding(false);
		results.setWidthFull();
	}

	private void refresh() {
		removeAll();
		clearRenderedResults();
		examPointsBadge = null;

		if (exam == null) {
			clearResultState();
			saveController.update();
			add(emptyState("Bitte wählen Sie eine Klausur aus."));
			return;
		}

		expectationHorizonRepository.syncCriteriaForExam(exam.id());
		loadItems();
		refreshPupils();
		add(createToolbar(), results);
		expand(results);
		renderResultStructure();
		loadSelectedPupilResults();
	}

	private Component createToolbar() {
		examPointsBadge = new EhPointBadge("Gesamtpunkte", this::pointsForExam);

		final HorizontalLayout toolbar = new HorizontalLayout(pupilSelector, saveButton, examPointsBadge);
		toolbar.addClassName("tt-results-toolbar");
		toolbar.setAlignItems(Alignment.CENTER);
		toolbar.setPadding(false);
		toolbar.setWidthFull();
		return toolbar;
	}

	private void refreshPupils() {
		final List<Pupil> pupils = courseRepository.findPupils(exam.courseId());
		final Pupil nextSelectedPupil = selectedPupil == null
				? pupils.stream().findFirst().orElse(null)
				: pupils.stream().filter(pupil -> pupil.id().equals(selectedPupil.id())).findFirst()
						.orElseGet(() -> pupils.stream().findFirst().orElse(null));

		refreshing = true;
		pupilSelector.setItems(pupils);
		pupilSelector.setEnabled(!pupils.isEmpty());
		selectedPupil = nextSelectedPupil;
		pupilSelector.setValue(nextSelectedPupil);
		refreshing = false;
	}

	private void renderResultStructure() {
		clearRenderedResults();

		if (selectedPupil == null) {
			results.add(emptyState("Diesem Kurs sind keine Schüler zugeordnet."));
			return;
		}

		if (parts.isEmpty()) {
			results.add(emptyState("Noch kein Erwartungshorizont angelegt."));
			return;
		}

		parts.forEach(part -> results.add(partBlock(part)));
	}

	private void clearRenderedResults() {
		results.removeAll();
		pointBadges.clear();
		requirementPointFields.clear();
		requirementDescriptions.clear();
	}

	private void loadSelectedPupilResults() {
		clearResultState();
		if (exam == null || selectedPupil == null) {
			applyResultState();
			return;
		}
		loadResultState();
		applyResultState();
	}

	private void applyResultState() {
		applyingResultState = true;
		try {
			requirements.forEach(requirement -> {
				final IntegerField points = requirementPointFields.get(requirement.id());
				if (points != null) {
					points.setValue(currentRequirementPoints(requirement));
				}
			});
			requirementDescriptions.forEach((requirementId, description) -> description.setCheckedTagKeys(criteria
					.stream()
					.filter(criterion -> criterion.requirementId().equals(requirementId))
					.filter(this::currentCriterionAchieved)
					.map(EhCriterion::criterionKey)
					.toList()));
		} finally {
			applyingResultState = false;
		}
		refreshPointBadges();
		saveController.update();
	}

	private void loadItems() {
		parts = expectationHorizonRepository.findPartsByExamId(exam.id());
		categories = expectationHorizonRepository.findCategoriesByExamId(exam.id());
		tasks = expectationHorizonRepository.findTasksByExamId(exam.id());
		requirements = expectationHorizonRepository.findRequirementsByExamId(exam.id());
		criteria = expectationHorizonRepository.findActiveCriteriaByExamId(exam.id());
	}

	private void loadResultState() {
		persistedCriterionResults = expectationHorizonRepository
				.findCriterionResultsByExamAndPupil(exam.id(), selectedPupil.id()).stream()
				.collect(Collectors.toMap(EhCriterionResult::criterionId, EhCriterionResult::achieved));
		editedCriterionResults.clear();
		editedCriterionResults.putAll(persistedCriterionResults);

		persistedRequirementResults = expectationHorizonRepository
				.findRequirementResultsByExamAndPupil(exam.id(), selectedPupil.id()).stream()
				.collect(Collectors.toMap(EhRequirementResult::requirementId, EhRequirementResult::points));
		editedRequirementResults.clear();
		editedRequirementResults.putAll(persistedRequirementResults);
	}

	private void clearResultState() {
		persistedCriterionResults = Map.of();
		persistedRequirementResults = Map.of();
		editedCriterionResults.clear();
		editedRequirementResults.clear();
	}

	private Component partBlock(final EhPart part) {
		final VerticalLayout block = aggregationBlock("tt-results-part", part.title(), () -> pointsForPart(part));
		categoriesFor(part).forEach(category -> block.add(categoryBlock(category)));
		return block;
	}

	private Component categoryBlock(final EhCategory category) {
		final VerticalLayout block = aggregationBlock("tt-results-category", category.title(),
				() -> pointsForCategory(category));
		tasksFor(category).forEach(task -> block.add(taskBlock(task)));
		return block;
	}

	private Component taskBlock(final EhTask task) {
		final VerticalLayout block = aggregationBlock("tt-results-task", task.title(), () -> pointsForTask(task));
		requirementsFor(task).forEach(requirement -> block.add(requirementBlock(task, requirement)));
		return block;
	}

	private Component requirementBlock(final EhTask task, final EhRequirement requirement) {
		final VerticalLayout block = new VerticalLayout();
		block.addClassName("tt-results-requirement");
		block.setPadding(false);
		block.setWidthFull();

		block.add(requirementHeader(task, requirement));

		final List<EhCriterion> requirementCriteria = criteriaFor(requirement);
		final Map<String, EhCriterion> criteriaByKey = requirementCriteria.stream()
				.collect(Collectors.toMap(EhCriterion::criterionKey, criterion -> criterion));
		final String descriptionMarkdown = normalized(requirement.descriptionMarkdown());

		final Div descriptionArea = new Div();
		descriptionArea.addClassName("tt-results-requirement-description-area");
		if (!descriptionMarkdown.isBlank()) {
			final MarkdownViewer description = new MarkdownViewer(descriptionMarkdown);
			description.setTag(EhSectionComponents.CRITERION_TAG);
			description.setTagRenderMode(MarkdownTagRenderMode.CHECKBOX);
			description.setCheckedTagKeys(requirementCriteria.stream()
					.filter(this::currentCriterionAchieved).map(EhCriterion::criterionKey)
					.toList());
			description.addTagCheckedChangeListener(change -> {
				if (applyingResultState) {
					return;
				}
				if (selectedPupil == null) {
					return;
				}
				final EhCriterion criterion = criteriaByKey.get(change.key());
				if (criterion != null) {
					editedCriterionResults.put(criterion.id(), change.checked());
					saveController.update();
				}
			});
			description.addClassName("tt-results-requirement-description");
			description.setWidthFull();
			requirementDescriptions.put(requirement.id(), description);
			descriptionArea.add(description);
		}

		final Div pointsColumn = new Div();
		pointsColumn.addClassName("tt-results-points-column-spacer");

		final HorizontalLayout body = new HorizontalLayout(descriptionArea, pointsColumn);
		body.addClassName("tt-results-requirement-body");
		body.setFlexGrow(1, descriptionArea);
		body.setPadding(false);
		body.setSpacing(false);
		body.setWidthFull();
		block.add(body);
		return block;
	}

	private Component requirementHeader(final EhTask task, final EhRequirement requirement) {
		final String requirementNumber = requirementNumber(task, requirement);
		final HorizontalLayout header = header(requirementNumberBadge(requirementNumber));
		final HorizontalLayout controls = new HorizontalLayout();
		controls.addClassName("tt-results-requirement-controls");
		controls.setAlignItems(Alignment.CENTER);
		controls.setPadding(false);
		controls.setSpacing(false);
		if (requirement.bonus()) {
			controls.add(bonusIcon());
		}
		controls.add(pointsControl(requirement));
		header.add(controls);
		return header;
	}

	private static Span requirementNumberBadge(final String requirementNumber) {
		final Span number = new Span(requirementNumber);
		number.addClassName("tt-results-requirement-number");
		number.getElement().setAttribute("aria-label", "Anforderung " + requirementNumber);
		return number;
	}

	private static Icon bonusIcon() {
		final Icon star = VaadinIcon.STAR.create();
		star.addClassName("tt-results-bonus-icon");
		star.getElement().setAttribute("aria-label", "Sternchen-Aufgabe");
		star.setTooltipText("Sternchen-Aufgabe / Bonusaufgabe");
		return star;
	}

	private Component pointsControl(final EhRequirement requirement) {
		final Span label = new Span("Punkte");
		label.addClassName("tt-field-label");

		final IntegerField points = new IntegerField();
		points.addClassName("tt-results-points-field");
		points.getElement().setAttribute("aria-label", "Punkte");
		points.setMin(0);
		points.setMax(requirement.maxPoints());
		points.setStepButtonsVisible(true);
		points.setValue(currentRequirementPoints(requirement));
		points.addValueChangeListener(event -> {
			if (applyingResultState) {
				return;
			}
			editedRequirementResults.put(requirement.id(), valueOrZero(event.getValue()));
			refreshPointBadges();
			saveController.update();
		});
		requirementPointFields.put(requirement.id(), points);

		final Span maximum = new Span("/ " + requirement.maxPoints());
		maximum.addClassName("tt-results-points-maximum");

		final HorizontalLayout control = new HorizontalLayout(label, points, maximum);
		control.addClassName("tt-results-points-control");
		control.setAlignItems(Alignment.CENTER);
		control.setPadding(false);
		control.setSpacing(false);
		return control;
	}

	private VerticalLayout aggregationBlock(final String className, final String title,
			final Supplier<EhPoints> pointsSupplier) {
		final VerticalLayout block = new VerticalLayout(header(title, "Summe", pointsSupplier));
		block.addClassName(className);
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	private Component header(final String titleText, final String badgeLabel, final Supplier<EhPoints> pointsSupplier) {
		final HorizontalLayout header = header(titleText);
		header.add(pointBadge(badgeLabel, pointsSupplier));
		return header;
	}

	private HorizontalLayout header(final String titleText) {
		final Span title = new Span(titleText);
		title.addClassName("tt-results-title");

		return header(title);
	}

	private HorizontalLayout header(final Component title) {
		final HorizontalLayout header = new HorizontalLayout(title);
		header.addClassName("tt-results-header");
		header.setPadding(false);
		header.setWidthFull();
		return header;
	}

	private EhPointBadge pointBadge(final String label, final Supplier<EhPoints> pointsSupplier) {
		final EhPointBadge badge = new EhPointBadge(label, pointsSupplier);
		pointBadges.add(badge);
		return badge;
	}

	private void refreshPointBadges() {
		if (examPointsBadge != null) {
			examPointsBadge.refreshBadges();
		}
		pointBadges.forEach(EhPointBadge::refreshBadges);
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

	private List<EhCriterion> criteriaFor(final EhRequirement requirement) {
		return criteria.stream().filter(criterion -> criterion.requirementId().equals(requirement.id()))
				.sorted(Comparator.comparingInt(EhCriterion::sortOrder).thenComparing(EhCriterion::id)).toList();
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

	private EhPoints pointsForExam() {
		return sum(requirements);
	}

	private EhPoints pointsForRequirement(final EhRequirement requirement) {
		return requirement.bonus() ? new EhPoints(0, currentRequirementPoints(requirement))
				: new EhPoints(currentRequirementPoints(requirement), 0);
	}

	private EhPoints sum(final List<EhRequirement> requirements) {
		return requirements.stream().map(this::pointsForRequirement).reduce(new EhPoints(0, 0), EhPoints::plus);
	}

	private boolean currentCriterionAchieved(final EhCriterion criterion) {
		return editedCriterionResults.getOrDefault(criterion.id(), persistedCriterionAchieved(criterion));
	}

	private boolean persistedCriterionAchieved(final EhCriterion criterion) {
		return persistedCriterionResults.getOrDefault(criterion.id(), false);
	}

	private int currentRequirementPoints(final EhRequirement requirement) {
		return editedRequirementResults.getOrDefault(requirement.id(), persistedRequirementPoints(requirement));
	}

	private int persistedRequirementPoints(final EhRequirement requirement) {
		return persistedRequirementResults.getOrDefault(requirement.id(), 0);
	}

	private boolean isDirty() {
		return criteria.stream()
				.anyMatch(criterion -> currentCriterionAchieved(criterion) != persistedCriterionAchieved(criterion))
				|| requirements.stream()
						.anyMatch(requirement -> currentRequirementPoints(requirement) != persistedRequirementPoints(
								requirement));
	}

	private void saveResults() {
		if (selectedPupil == null) {
			return;
		}
		if (!validateRequirementPoints()) {
			saveController.update();
			return;
		}

		criteria.stream()
				.filter(criterion -> currentCriterionAchieved(criterion) != persistedCriterionAchieved(criterion))
				.map(criterion -> new EhCriterionResult(criterion.id(), selectedPupil.id(),
						currentCriterionAchieved(criterion)))
				.forEach(expectationHorizonRepository::saveCriterionResult);
		requirements.stream()
				.filter(requirement -> currentRequirementPoints(requirement) != persistedRequirementPoints(
						requirement))
				.map(requirement -> new EhRequirementResult(requirement.id(), selectedPupil.id(),
						currentRequirementPoints(requirement)))
				.forEach(expectationHorizonRepository::saveRequirementResult);

		persistedCriterionResults = criteria.stream()
				.collect(Collectors.toMap(EhCriterion::id, this::currentCriterionAchieved));
		persistedRequirementResults = requirements.stream()
				.collect(Collectors.toMap(EhRequirement::id, this::currentRequirementPoints));
	}

	private boolean validateRequirementPoints() {
		for (final EhRequirement requirement : requirements) {
			final int points = currentRequirementPoints(requirement);
			if (points < 0 || points > requirement.maxPoints()) {
				Notification.show("Punkte müssen zwischen 0 und " + requirement.maxPoints() + " liegen.");
				return false;
			}
		}
		return true;
	}

	private static int valueOrZero(final Integer value) {
		return value == null ? 0 : value;
	}

	private String requirementNumber(final EhTask task, final EhRequirement requirement) {
		final List<EhRequirement> siblings = requirementsFor(task);
		for (int index = 0; index < siblings.size(); index++) {
			if (siblings.get(index).id().equals(requirement.id())) {
				return String.valueOf(index + 1);
			}
		}
		throw new IllegalStateException("Missing EH requirement: " + requirement.id());
	}

	private Component emptyState(final String text) {
		final Span emptyState = new Span(text);
		emptyState.addClassName("tt-empty-state");
		return emptyState;
	}

	private String pupilLabel(final Pupil pupil) {
		if (pupil == null) {
			return "";
		}
		return pupil.surname() + ", " + pupil.name();
	}

	private String normalized(final String value) {
		return value == null ? "" : value;
	}
}
