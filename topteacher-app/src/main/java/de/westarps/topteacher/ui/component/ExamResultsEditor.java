package de.westarps.topteacher.ui.component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExpectationHorizonRepository;
import de.westarps.topteacher.model.EhCategory;
import de.westarps.topteacher.model.EhCriterion;
import de.westarps.topteacher.model.EhCriterionResult;
import de.westarps.topteacher.model.EhPart;
import de.westarps.topteacher.model.EhRequirement;
import de.westarps.topteacher.model.EhTask;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.vaadin.markdown.MarkdownViewer;
import de.westarps.vaadin.markdown.MarkdownTagRenderMode;

public class ExamResultsEditor extends VerticalLayout {

	private final CourseRepository courseRepository;
	private final ExpectationHorizonRepository expectationHorizonRepository;
	private final ComboBox<Pupil> pupilSelector = new ComboBox<>("Schüler");
	private final VerticalLayout results = new VerticalLayout();

	private Exam exam;
	private Pupil selectedPupil;
	private boolean refreshing;
	private List<EhPart> parts = List.of();
	private List<EhCategory> categories = List.of();
	private List<EhTask> tasks = List.of();
	private List<EhRequirement> requirements = List.of();
	private List<EhCriterion> criteria = List.of();

	public ExamResultsEditor(final CourseRepository courseRepository,
			final ExpectationHorizonRepository expectationHorizonRepository) {
		this.courseRepository = courseRepository;
		this.expectationHorizonRepository = expectationHorizonRepository;

		addClassName("tt-exam-results-editor");
		setPadding(false);
		setSpacing(false);
		setSizeFull();

		configurePupilSelector();
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
		pupilSelector.addValueChangeListener(event -> {
			selectedPupil = event.getValue();
			if (!refreshing) {
				renderResults();
			}
		});
	}

	private void configureResults() {
		results.addClassName("tt-results-list");
		results.setPadding(false);
		results.setSizeFull();
	}

	private void refresh() {
		removeAll();
		results.removeAll();

		if (exam == null) {
			add(emptyState("Bitte wählen Sie eine Klausur aus."));
			return;
		}

		final HorizontalLayout toolbar = new HorizontalLayout(pupilSelector);
		toolbar.addClassName("tt-results-toolbar");
		toolbar.setPadding(false);
		toolbar.setWidthFull();

		refreshPupils();
		add(toolbar, results);
		expand(results);
		renderResults();
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

	private void renderResults() {
		results.removeAll();

		if (selectedPupil == null) {
			results.add(emptyState("Diesem Kurs sind keine Schüler zugeordnet."));
			return;
		}

		expectationHorizonRepository.syncCriteriaForExam(exam.id());
		loadItems();

		if (parts.isEmpty()) {
			results.add(emptyState("Noch kein Erwartungshorizont angelegt."));
			return;
		}
		if (criteria.isEmpty()) {
			results.add(emptyState("Noch keine Kriterien markiert."));
			return;
		}

		final Set<Integer> achievedCriterionIds = expectationHorizonRepository
				.findCriterionResultsByExamAndPupil(exam.id(), selectedPupil.id()).stream()
				.filter(EhCriterionResult::achieved).map(EhCriterionResult::criterionId).collect(Collectors.toSet());
		parts.forEach(part -> results.add(partBlock(part, achievedCriterionIds)));
	}

	private void loadItems() {
		parts = expectationHorizonRepository.findPartsByExamId(exam.id());
		categories = expectationHorizonRepository.findCategoriesByExamId(exam.id());
		tasks = expectationHorizonRepository.findTasksByExamId(exam.id());
		requirements = expectationHorizonRepository.findRequirementsByExamId(exam.id());
		criteria = expectationHorizonRepository.findActiveCriteriaByExamId(exam.id());
	}

	private Component partBlock(final EhPart part, final Set<Integer> achievedCriterionIds) {
		final VerticalLayout block = aggregationBlock("tt-results-part", part.title(), pointsForPart(part));
		categoriesFor(part).forEach(category -> block.add(categoryBlock(category, achievedCriterionIds)));
		return block;
	}

	private Component categoryBlock(final EhCategory category, final Set<Integer> achievedCriterionIds) {
		final VerticalLayout block = aggregationBlock("tt-results-category", category.title(),
				pointsForCategory(category));
		tasksFor(category).forEach(task -> block.add(taskBlock(task, achievedCriterionIds)));
		return block;
	}

	private Component taskBlock(final EhTask task, final Set<Integer> achievedCriterionIds) {
		final VerticalLayout block = aggregationBlock("tt-results-task", task.title(), pointsForTask(task));
		requirementsFor(task)
				.forEach(requirement -> block.add(requirementBlock(task, requirement, achievedCriterionIds)));
		return block;
	}

	private Component requirementBlock(final EhTask task, final EhRequirement requirement,
			final Set<Integer> achievedCriterionIds) {
		final VerticalLayout block = new VerticalLayout();
		block.addClassName("tt-results-requirement");
		block.setPadding(false);
		block.setWidthFull();

		block.add(header("Anforderung " + requirementNumber(task, requirement), "Punkte",
				pointsForRequirement(requirement)));

		final List<EhCriterion> requirementCriteria = criteriaFor(requirement);
		final Map<String, EhCriterion> criteriaByKey = requirementCriteria.stream()
				.collect(Collectors.toMap(EhCriterion::criterionKey, criterion -> criterion));
		final String descriptionMarkdown = normalized(requirement.descriptionMarkdown());
		if (!descriptionMarkdown.isBlank()) {
			final MarkdownViewer description = new MarkdownViewer(descriptionMarkdown);
			description.setTag(EhSectionComponents.CRITERION_TAG);
			description.setTagRenderMode(MarkdownTagRenderMode.CHECKBOX);
			description.setCheckedTagKeys(requirementCriteria.stream()
					.filter(criterion -> achievedCriterionIds.contains(criterion.id())).map(EhCriterion::criterionKey)
					.toList());
			description.addTagCheckedChangeListener(change -> {
				if (selectedPupil == null) {
					return;
				}
				final EhCriterion criterion = criteriaByKey.get(change.key());
				if (criterion != null) {
					expectationHorizonRepository.saveCriterionResult(
							new EhCriterionResult(criterion.id(), selectedPupil.id(), change.checked()));
				}
			});
			description.addClassName("tt-results-requirement-description");
			description.setWidthFull();
			block.add(description);
		}
		return block;
	}

	private VerticalLayout aggregationBlock(final String className, final String title, final EhPoints points) {
		final VerticalLayout block = new VerticalLayout(header(title, "Summe", points));
		block.addClassName(className);
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	private Component header(final String titleText, final String badgeLabel, final EhPoints points) {
		final Span title = new Span(titleText);
		title.addClassName("tt-results-title");

		final HorizontalLayout header = new HorizontalLayout(title, new EhPointBadge(badgeLabel, () -> points));
		header.addClassName("tt-results-header");
		header.setPadding(false);
		header.setWidthFull();
		return header;
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

	private EhPoints pointsForRequirement(final EhRequirement requirement) {
		return requirement.bonus() ? new EhPoints(0, requirement.maxPoints())
				: new EhPoints(requirement.maxPoints(), 0);
	}

	private EhPoints sum(final List<EhRequirement> requirements) {
		return requirements.stream().map(this::pointsForRequirement).reduce(new EhPoints(0, 0), EhPoints::plus);
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
