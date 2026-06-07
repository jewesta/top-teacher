package de.westarps.topteacher.ui.component.loe;

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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoePointRules;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.UiUrls;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.FullscreenButton;
import de.westarps.topteacher.ui.component.StepperComboBox;
import de.westarps.vaadin.markdown.MarkdownViewer;
import de.westarps.vaadin.markdown.MarkdownTagRenderMode;

public class ExamResultsEditor extends AbstractDesigner {

	private final CourseRepository courseRepository;
	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final StepperComboBox<Pupil> pupilSelector = new StepperComboBox<>();
	private final Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
	private final Button deleteButton = new Button(VaadinIcon.TRASH.create());
	private final MenuBar pdfMenu = new MenuBar();
	private final ConfirmDialog deleteConfirmation = new ConfirmDialog();
	private final FullscreenButton fullscreenButton;
	private final VerticalLayout results;
	private final LoeSaveController saveController = new LoeSaveController();
	private final List<LoePointBadge> pointBadges = new ArrayList<>();
	private final Map<Integer, IntegerField> requirementPointFields = new HashMap<>();
	private final Map<Integer, Span> requirementPointTexts = new HashMap<>();
	private final Map<Integer, TextArea> requirementCommentFields = new HashMap<>();
	private final Map<Integer, MarkdownViewer> requirementDescriptions = new HashMap<>();
	private final Map<Integer, Span> requirementCriterionIndicators = new HashMap<>();
	private final Map<Integer, Checkbox> criterionCheckboxes = new HashMap<>();
	private final Map<Integer, Boolean> editedCriterionResults = new HashMap<>();
	private final Map<Integer, Integer> editedRequirementResults = new HashMap<>();
	private final Map<Integer, String> editedRequirementComments = new HashMap<>();

	private Exam exam;
	private Pupil selectedPupil;
	private boolean refreshing;
	private boolean applyingResultState;
	private boolean updatingCriterionControls;
	private List<LoePart> parts = List.of();
	private List<LoeCategory> categories = List.of();
	private List<LoeTask> tasks = List.of();
	private List<LoeRequirement> requirements = List.of();
	private List<LoeCriterion> criteria = List.of();
	private LoePointBadge examPointsBadge;
	private MenuItem pdfMenuItem;
	private MenuItem pupilPdfItem;
	private MenuItem teacherPdfItem;
	private LoePointRules pointRules;
	private Map<Integer, Boolean> persistedCriterionResults = Map.of();
	private Map<Integer, Integer> persistedRequirementResults = Map.of();
	private Map<Integer, String> persistedRequirementComments = Map.of();

	public ExamResultsEditor(final CourseRepository courseRepository,
			final LevelOfExpectationsRepository levelOfExpectationsRepository,
			final GradingScaleRepository gradingScaleRepository) {
		super("tt-exam-results-editor");
		this.courseRepository = courseRepository;
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		fullscreenButton = new FullscreenButton(this);
		results = content();

		configurePupilSelector();
		configureSaveButton();
		configureDeleteButton();
		configurePdfDownload();
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
		pupilSelector.setWidth("16rem");
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

	private void configureDeleteButton() {
		deleteButton.setAriaLabel("Ergebnisse löschen");
		deleteButton.setTooltipText("Ergebnisse löschen");
		deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE,
				ButtonVariant.LUMO_ERROR);
		deleteButton.addClickListener(event -> openDeleteConfirmation());

		deleteConfirmation.setHeader("Ergebnisse löschen?");
		deleteConfirmation.setCancelable(true);
		deleteConfirmation.setCancelText("Abbrechen");
		deleteConfirmation.setConfirmText("Löschen");
		deleteConfirmation.setConfirmButtonTheme("error primary");
		deleteConfirmation.addConfirmListener(event -> deleteSelectedPupilResults());
		updateDeleteButton();
	}

	private void configurePdfDownload() {
		pdfMenu.addClassName("tt-pdf-menu");
		pdfMenu.addThemeVariants(MenuBarVariant.LUMO_SMALL);
		pdfMenuItem = pdfMenu.addItem(pdfMenuLabel());
		pdfMenuItem.setAriaLabel("PDF herunterladen");
		pdfMenu.setTooltipText(pdfMenuItem, "PDF herunterladen");
		pupilPdfItem = pdfMenuItem.getSubMenu().addItem("Schülerversion", event -> downloadPdf(false));
		teacherPdfItem = pdfMenuItem.getSubMenu().addItem("Lehrerversion", event -> downloadPdf(true));
		updatePdfDownload();
	}

	private static HorizontalLayout pdfMenuLabel() {
		final HorizontalLayout label = new HorizontalLayout(VaadinIcon.DOWNLOAD.create(), new Span("PDF"));
		label.addClassName("tt-pdf-menu-label");
		label.setAlignItems(Alignment.CENTER);
		label.setPadding(false);
		label.setSpacing(false);
		return label;
	}

	private void refresh() {
		resetDesigner();
		clearRenderedResults();
		examPointsBadge = null;

		if (exam == null) {
			clearResultState();
			pointRules = null;
			updateActionButtons();
			showDesignerMessage(emptyState("Bitte wählen Sie eine Klausur aus."));
			return;
		}

		levelOfExpectationsRepository.syncCriteriaForExam(exam.id());
		loadPointRules();
		loadItems();
		refreshPupils();
		configureToolbar();
		showDesigner();
		renderResultStructure();
		loadSelectedPupilResults();
	}

	private void configureToolbar() {
		examPointsBadge = new LoePointBadge("Gesamt", this::pointsForExam);
		toolbar().add(pupilSelector, saveButton, deleteButton, pdfMenu, fullscreenButton, deleteConfirmation);
		toolbarSummary().add(examPointsBadge);
	}

	private void refreshPupils() {
		final List<Pupil> pupils = courseRepository.findPupils(exam.courseId());
		final Pupil nextSelectedPupil = selectedPupil == null ? pupils.stream().findFirst().orElse(null)
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
		requirementPointTexts.clear();
		requirementCommentFields.clear();
		requirementDescriptions.clear();
		requirementCriterionIndicators.clear();
		criterionCheckboxes.clear();
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
				final TextArea comment = requirementCommentFields.get(requirement.id());
				if (comment != null) {
					comment.setValue(currentRequirementComment(requirement));
				}
			});
			requirements.forEach(this::refreshCriterionControls);
		} finally {
			applyingResultState = false;
		}
		refreshRequirementPointTexts();
		refreshPointBadges();
		updateActionButtons();
	}

	private void loadItems() {
		parts = levelOfExpectationsRepository.findPartsByExamId(exam.id());
		categories = levelOfExpectationsRepository.findCategoriesByExamId(exam.id());
		tasks = levelOfExpectationsRepository.findTasksByExamId(exam.id());
		requirements = levelOfExpectationsRepository.findRequirementsByExamId(exam.id());
		criteria = levelOfExpectationsRepository.findActiveCriteriaByExamId(exam.id());
	}

	private void loadPointRules() {
		pointRules = courseRepository.findById(exam.courseId())
				.flatMap(course -> gradingScaleRepository.findById(course.gradingScaleId()))
				.map(LoePointRules::new).orElse(null);
	}

	private void loadResultState() {
		persistedCriterionResults = levelOfExpectationsRepository
				.findCriterionResultsByExamAndPupil(exam.id(), selectedPupil.id()).stream()
				.collect(Collectors.toMap(LoeCriterionResult::criterionId, LoeCriterionResult::achieved));
		editedCriterionResults.clear();
		editedCriterionResults.putAll(persistedCriterionResults);

		final List<LoeRequirementResult> requirementResults = levelOfExpectationsRepository
				.findRequirementResultsByExamAndPupil(exam.id(), selectedPupil.id());
		persistedRequirementResults = requirementResults.stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, LoeRequirementResult::points));
		editedRequirementResults.clear();
		editedRequirementResults.putAll(persistedRequirementResults);

		persistedRequirementComments = requirementResults.stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, LoeRequirementResult::comment));
		editedRequirementComments.clear();
		editedRequirementComments.putAll(persistedRequirementComments);
	}

	private void clearResultState() {
		persistedCriterionResults = Map.of();
		persistedRequirementResults = Map.of();
		persistedRequirementComments = Map.of();
		editedCriterionResults.clear();
		editedRequirementResults.clear();
		editedRequirementComments.clear();
	}

	private void openDeleteConfirmation() {
		if (!hasPersistedResults()) {
			return;
		}

		final String pupilName = selectedPupil == null ? "" : pupilLabel(selectedPupil);
		deleteConfirmation.setText("Die erfassten Ergebnisse für " + pupilName
				+ " werden gelöscht. Diese Aktion kann nicht rückgängig gemacht werden.");
		deleteConfirmation.open();
	}

	private void deleteSelectedPupilResults() {
		if (exam == null || selectedPupil == null) {
			return;
		}

		levelOfExpectationsRepository.deleteResultsByExamAndPupil(exam.id(), selectedPupil.id());
		clearResultState();
		applyResultState();
		deleteConfirmation.close();
		Notification.show("Ergebnisse gelöscht.");
	}

	private Component partBlock(final LoePart part) {
		final VerticalLayout block = aggregationBlock("tt-results-part", part.title(), () -> pointsForPart(part));
		categoriesFor(part).forEach(category -> block.add(categoryBlock(category)));
		return block;
	}

	private Component categoryBlock(final LoeCategory category) {
		final VerticalLayout block = aggregationBlock("tt-results-category", category.title(),
				() -> pointsForCategory(category));
		tasksFor(category).forEach(task -> block.add(taskBlock(task)));
		return block;
	}

	private Component taskBlock(final LoeTask task) {
		final VerticalLayout block = aggregationBlock("tt-results-task", task.title(), () -> pointsForTask(task));
		requirementsFor(task).forEach(requirement -> block.add(requirementBlock(task, requirement)));
		return block;
	}

	private Component requirementBlock(final LoeTask task, final LoeRequirement requirement) {
		final HorizontalLayout block = new HorizontalLayout();
		block.addClassName("tt-results-requirement");
		block.setPadding(false);
		block.setSpacing(false);
		block.setWidthFull();
		block.setAlignItems(Alignment.STRETCH);

		final List<LoeCriterion> requirementCriteria = criteriaFor(requirement);
		final Map<String, LoeCriterion> criteriaByKey = requirementCriteria.stream()
				.collect(Collectors.toMap(LoeCriterion::criterionKey, criterion -> criterion));
		final String descriptionMarkdown = normalized(requirement.descriptionMarkdown());

		final VerticalLayout descriptionArea = new VerticalLayout();
		descriptionArea.addClassName("tt-results-requirement-content");
		descriptionArea.setPadding(false);
		descriptionArea.setSpacing(false);
		descriptionArea.setWidthFull();
		if (!descriptionMarkdown.isBlank()) {
			final MarkdownViewer description = new MarkdownViewer(descriptionMarkdown);
			description.setTag(LoeSectionComponents.CRITERION_TAG);
			description.setTagRenderMode(MarkdownTagRenderMode.CHECKBOX);
			description.setCheckedTagKeys(requirementCriteria.stream().filter(this::currentCriterionAchieved)
					.map(LoeCriterion::criterionKey).toList());
			description.addTagCheckedChangeListener(change -> {
				if (applyingResultState) {
					return;
				}
				if (selectedPupil == null) {
					return;
				}
				final LoeCriterion criterion = criteriaByKey.get(change.key());
				if (criterion != null) {
					setCriterionAchieved(requirement, criterion, change.checked());
				}
			});
			description.addClassName("tt-results-requirement-description");
			description.setWidthFull();
			requirementDescriptions.put(requirement.id(), description);
			descriptionArea.add(description);
		}
		if (!requirementCriteria.isEmpty()) {
			descriptionArea.add(criterionIndicator(requirement));
		}
		descriptionArea.add(commentField(requirement));

		final Div pointsArea = new Div(pointsControl(requirement, requirementCriteria));
		pointsArea.addClassName("tt-results-requirement-points-area");

		block.add(requirementMarker(task, requirement), descriptionArea, pointsArea);
		block.setFlexGrow(1, descriptionArea);
		return block;
	}

	private Span criterionIndicator(final LoeRequirement requirement) {
		final Span indicator = new Span();
		indicator.addClassName("tt-results-criteria-indicator");
		indicator.getElement().setAttribute("aria-label", "Markierte Kriterien");
		requirementCriterionIndicators.put(requirement.id(), indicator);
		refreshCriterionIndicator(requirement);
		return indicator;
	}

	private void refreshCriterionIndicators() {
		requirements.forEach(this::refreshCriterionIndicator);
	}

	private void refreshCriterionIndicator(final LoeRequirement requirement) {
		final Span indicator = requirementCriterionIndicators.get(requirement.id());
		if (indicator == null) {
			return;
		}

		final List<LoeCriterion> requirementCriteria = criteriaFor(requirement);
		final long achieved = requirementCriteria.stream().filter(this::currentCriterionAchieved).count();
		indicator.setText(achieved + " von " + requirementCriteria.size() + " Kriterien erfüllt");
	}

	private void setCriterionAchieved(final LoeRequirement requirement, final LoeCriterion criterion,
			final boolean achieved) {
		if (selectedPupil == null) {
			return;
		}
		editedCriterionResults.put(criterion.id(), achieved);
		refreshCriterionControls(requirement);
		updateActionButtons();
	}

	private void refreshCriterionControls(final LoeRequirement requirement) {
		updatingCriterionControls = true;
		try {
			final List<LoeCriterion> requirementCriteria = criteriaFor(requirement);
			final List<String> checkedKeys = requirementCriteria.stream().filter(this::currentCriterionAchieved)
					.map(LoeCriterion::criterionKey).toList();
			final MarkdownViewer description = requirementDescriptions.get(requirement.id());
			if (description != null) {
				description.setCheckedTagKeys(checkedKeys);
			}
			requirementCriteria.forEach(criterion -> {
				final Checkbox checkbox = criterionCheckboxes.get(criterion.id());
				if (checkbox != null) {
					checkbox.setValue(currentCriterionAchieved(criterion));
				}
			});
		} finally {
			updatingCriterionControls = false;
		}
		refreshCriterionIndicator(requirement);
	}

	private TextArea commentField(final LoeRequirement requirement) {
		final TextArea comment = new TextArea();
		comment.addClassName("tt-results-comment-field");
		comment.getElement().setAttribute("aria-label", "Notiz");
		comment.setPlaceholder("Notiz");
		comment.setMaxLength(2000);
		comment.setMinRows(2);
		comment.setMaxRows(2);
		comment.setValue(currentRequirementComment(requirement));
		comment.setValueChangeMode(ValueChangeMode.EAGER);
		comment.setWidthFull();
		comment.addValueChangeListener(event -> {
			if (applyingResultState) {
				return;
			}
			editedRequirementComments.put(requirement.id(), normalized(event.getValue()));
			updateActionButtons();
		});
		requirementCommentFields.put(requirement.id(), comment);
		return comment;
	}

	private Component requirementMarker(final LoeTask task, final LoeRequirement requirement) {
		final String requirementNumber = requirementNumber(task, requirement);
		final VerticalLayout marker = new VerticalLayout(requirementNumberBadge(requirementNumber));
		marker.addClassName("tt-results-requirement-marker");
		marker.setAlignItems(Alignment.CENTER);
		marker.setPadding(false);
		marker.setSpacing(false);
		if (requirement.bonus()) {
			marker.add(bonusIcon());
		}
		return marker;
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

	private Component pointsControl(final LoeRequirement requirement, final List<LoeCriterion> requirementCriteria) {
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
			refreshRequirementPointText(requirement);
			refreshPointBadges();
			updateActionButtons();
		});
		requirementPointFields.put(requirement.id(), points);

		final Span pointsText = new Span();
		pointsText.addClassName("tt-results-points-text");
		requirementPointTexts.put(requirement.id(), pointsText);
		refreshRequirementPointText(requirement);

		final VerticalLayout control = new VerticalLayout(points, pointsText);
		control.addClassName("tt-results-points-control");
		control.setPadding(false);
		control.setSpacing(false);
		control.setWidthFull();
		if (!requirementCriteria.isEmpty()) {
			control.add(criteriaChecklist(requirement, requirementCriteria));
		}
		return control;
	}

	private Component criteriaChecklist(final LoeRequirement requirement, final List<LoeCriterion> requirementCriteria) {
		final VerticalLayout checklist = new VerticalLayout();
		checklist.addClassName("tt-results-criteria-checklist");
		checklist.setPadding(false);
		checklist.setSpacing(false);
		checklist.setWidthFull();
		requirementCriteria.forEach(criterion -> checklist.add(criterionCheckboxRow(requirement, criterion)));
		return checklist;
	}

	private Component criterionCheckboxRow(final LoeRequirement requirement, final LoeCriterion criterion) {
		final Checkbox checkbox = new Checkbox(currentCriterionAchieved(criterion));
		checkbox.addClassName("tt-results-criterion-checkbox");
		checkbox.setAriaLabel("Kriterium " + criterion.criterionKey() + " erfüllt");
		checkbox.getElement().setAttribute("aria-label", "Kriterium " + criterion.criterionKey() + " erfüllt");
		checkbox.addValueChangeListener(event -> {
			if (applyingResultState || updatingCriterionControls) {
				return;
			}
			setCriterionAchieved(requirement, criterion, event.getValue());
		});
		criterionCheckboxes.put(criterion.id(), checkbox);

		final Span badge = new Span(criterion.criterionKey());
		badge.addClassNames("ws-markdown-tag-badge", "tt-results-criterion-badge");
		badge.getElement().setAttribute("aria-hidden", "true");

		final HorizontalLayout row = new HorizontalLayout(badge, checkbox);
		row.addClassName("tt-results-criterion-checkbox-row");
		row.setAlignItems(Alignment.CENTER);
		row.setPadding(false);
		row.setSpacing(false);
		return row;
	}

	private VerticalLayout aggregationBlock(final String className, final String title,
			final Supplier<LoePoints> pointsSupplier) {
		final VerticalLayout block = new VerticalLayout(header(title, "Summe", pointsSupplier));
		block.addClassName(className);
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	private Component header(final String titleText, final String badgeLabel, final Supplier<LoePoints> pointsSupplier) {
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

	private LoePointBadge pointBadge(final String label, final Supplier<LoePoints> pointsSupplier) {
		final LoePointBadge badge = new LoePointBadge(label, pointsSupplier);
		pointBadges.add(badge);
		return badge;
	}

	private void refreshPointBadges() {
		if (examPointsBadge != null) {
			examPointsBadge.refreshBadges();
		}
		pointBadges.forEach(LoePointBadge::refreshBadges);
	}

	private void refreshRequirementPointTexts() {
		requirements.forEach(this::refreshRequirementPointText);
	}

	private void refreshRequirementPointText(final LoeRequirement requirement) {
		final Span pointsText = requirementPointTexts.get(requirement.id());
		if (pointsText == null) {
			return;
		}
		pointsText.setText(currentRequirementPoints(requirement) + " von " + requirement.maxPoints() + " Punkten");
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

	private List<LoeCriterion> criteriaFor(final LoeRequirement requirement) {
		return criteria.stream().filter(criterion -> criterion.requirementId().equals(requirement.id()))
				.sorted(Comparator.comparingInt(LoeCriterion::sortOrder).thenComparing(LoeCriterion::id)).toList();
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

	private LoePoints pointsForExam() {
		return sum(requirements);
	}

	private LoePoints pointsForRequirement(final LoeRequirement requirement) {
		return requirement.bonus() ? new LoePoints(0, currentRequirementPoints(requirement))
				: new LoePoints(currentRequirementPoints(requirement), 0);
	}

	private LoePoints sum(final List<LoeRequirement> requirements) {
		return requirements.stream().map(this::pointsForRequirement).reduce(new LoePoints(0, 0), LoePoints::plus);
	}

	private boolean currentCriterionAchieved(final LoeCriterion criterion) {
		return editedCriterionResults.getOrDefault(criterion.id(), persistedCriterionAchieved(criterion));
	}

	private boolean persistedCriterionAchieved(final LoeCriterion criterion) {
		return persistedCriterionResults.getOrDefault(criterion.id(), false);
	}

	private int currentRequirementPoints(final LoeRequirement requirement) {
		return editedRequirementResults.getOrDefault(requirement.id(), persistedRequirementPoints(requirement));
	}

	private int persistedRequirementPoints(final LoeRequirement requirement) {
		return persistedRequirementResults.getOrDefault(requirement.id(), 0);
	}

	private String currentRequirementComment(final LoeRequirement requirement) {
		return editedRequirementComments.getOrDefault(requirement.id(), persistedRequirementComment(requirement));
	}

	private String persistedRequirementComment(final LoeRequirement requirement) {
		return persistedRequirementComments.getOrDefault(requirement.id(), "");
	}

	private boolean isDirty() {
		return criteria.stream()
				.anyMatch(criterion -> currentCriterionAchieved(criterion) != persistedCriterionAchieved(criterion))
				|| requirements.stream().anyMatch(
						requirement -> currentRequirementPoints(requirement) != persistedRequirementPoints(requirement)
								|| !currentRequirementComment(requirement)
										.equals(persistedRequirementComment(requirement)));
	}

	private void saveResults() {
		if (selectedPupil == null) {
			return;
		}
		if (!validateRequirementPoints()) {
			updateActionButtons();
			return;
		}

		criteria.stream()
				.filter(criterion -> currentCriterionAchieved(criterion) != persistedCriterionAchieved(criterion))
				.map(criterion -> new LoeCriterionResult(criterion.id(), selectedPupil.id(),
						currentCriterionAchieved(criterion)))
				.forEach(levelOfExpectationsRepository::saveCriterionResult);
		requirements.stream()
				.filter(requirement -> currentRequirementPoints(requirement) != persistedRequirementPoints(requirement)
						|| !currentRequirementComment(requirement).equals(persistedRequirementComment(requirement)))
				.map(requirement -> new LoeRequirementResult(requirement.id(), selectedPupil.id(),
						currentRequirementPoints(requirement), currentRequirementComment(requirement)))
				.forEach(levelOfExpectationsRepository::saveRequirementResult);

		persistedCriterionResults = criteria.stream()
				.collect(Collectors.toMap(LoeCriterion::id, this::currentCriterionAchieved));
		persistedRequirementResults = requirements.stream()
				.collect(Collectors.toMap(LoeRequirement::id, this::currentRequirementPoints));
		persistedRequirementComments = requirements.stream()
				.collect(Collectors.toMap(LoeRequirement::id, this::currentRequirementComment));
		updateActionButtons();
	}

	private void updateActionButtons() {
		saveController.update();
		updateDeleteButton();
		updatePdfDownload();
	}

	private void updateDeleteButton() {
		deleteButton.setEnabled(selectedPupil != null && hasPersistedResults());
	}

	private void updatePdfDownload() {
		final boolean enabled = exam != null && selectedPupil != null && !isDirty();
		pdfMenu.setEnabled(enabled);
		pdfMenuItem.setEnabled(enabled);
		pupilPdfItem.setEnabled(enabled);
		teacherPdfItem.setEnabled(enabled);
	}

	private void downloadPdf(final boolean teacherVersion) {
		if (exam == null || selectedPupil == null || isDirty()) {
			return;
		}
		getUI().ifPresent(ui -> ui.getPage().executeJs("""
				const anchor = document.createElement('a');
				anchor.href = $0;
				anchor.download = $1;
				anchor.style.display = 'none';
				document.body.appendChild(anchor);
				anchor.click();
				anchor.remove();
				""", pdfUrl(teacherVersion), pdfFileName(teacherVersion)));
	}

	private String pdfUrl(final boolean teacherVersion) {
		final String fileName =
				teacherVersion ? "level-of-expectations-teacher.pdf" : "level-of-expectations.pdf";
		return UiUrls.contextRelative("/export/exams/" + exam.id() + "/pupils/" + selectedPupil.id() + "/"
				+ fileName);
	}

	private boolean hasPersistedResults() {
		return !persistedCriterionResults.isEmpty() || !persistedRequirementResults.isEmpty();
	}

	private boolean validateRequirementPoints() {
		for (final LoeRequirement requirement : requirements) {
			final int points = currentRequirementPoints(requirement);
			if (points < 0 || points > requirement.maxPoints()) {
				Notification.show("Punkte müssen zwischen 0 und " + requirement.maxPoints() + " liegen.");
				return false;
			}
		}
		if (pointRules == null) {
			Notification.show("Für den Kurs wurde kein Notenschlüssel gefunden.");
			return false;
		}
		final int regularMaxPoints = pointRules.regularMaxPoints(requirements);
		if (!pointRules.regularMaxPointsMatch(requirements)) {
			Notification.show("Der Erwartungshorizont hat " + regularMaxPoints
					+ " reguläre Punkte. Der Notenschlüssel erwartet " + pointRules.maxPoints() + " Punkte.");
			return false;
		}
		pointRules.cappedAchievedTotal(requirements, this::currentRequirementPoints);
		return true;
	}

	private static int valueOrZero(final Integer value) {
		return value == null ? 0 : value;
	}

	private String requirementNumber(final LoeTask task, final LoeRequirement requirement) {
		final List<LoeRequirement> siblings = requirementsFor(task);
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

	private String pdfFileName(final boolean teacherVersion) {
		final String prefix = teacherVersion ? "lehrerversion-erwartungshorizont" : "erwartungshorizont";
		return prefix + "-" + fileNamePart(exam.title()) + "-" + fileNamePart(selectedPupil.surname())
				+ "-" + fileNamePart(selectedPupil.name()) + ".pdf";
	}

	private static String fileNamePart(final String value) {
		return normalizedStatic(value).replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("(^-+|-+$)", "")
				.toLowerCase();
	}

	private String normalized(final String value) {
		return normalizedStatic(value);
	}

	private static String normalizedStatic(final String value) {
		return value == null ? "" : value;
	}
}
