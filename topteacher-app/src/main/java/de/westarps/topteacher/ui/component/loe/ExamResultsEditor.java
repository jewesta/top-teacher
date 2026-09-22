package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoePointRules;
import de.westarps.topteacher.model.loe.LoePointUnits;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.UiUrls;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.DesignerViewport;
import de.westarps.topteacher.ui.component.FullscreenButton;
import de.westarps.topteacher.ui.component.StepperComboBox;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

public class ExamResultsEditor extends AbstractDesigner {

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final LoeSaveController saveController = new LoeSaveController();
	private final StepperComboBox<Pupil> pupilSelector = new StepperComboBox<>();
	private final Button saveButton = Buttons.save();
	private final Button discardButton = Buttons.icon("Änderungen verwerfen", VaadinIcon.ROTATE_LEFT,
			event -> openDiscardConfirmation());
	private final Button deleteButton = Buttons.icon("Ergebnisse löschen", VaadinIcon.TRASH,
			event -> openDeleteConfirmation());
	private final Button reloadButton = Buttons.icon("Neu laden", VaadinIcon.REFRESH, event -> refreshFromDatabase());
	private final MenuBar pdfMenu = new MenuBar();
	private final ConfirmDialog discardConfirmation = new ConfirmDialog();
	private final ConfirmDialog deleteConfirmation = new ConfirmDialog();
	private final FullscreenButton fullscreenButton;
	private final VerticalLayout results;
	private final DesignerViewport viewport = new DesignerViewport(content());
	private final List<LoePointBadge> pointBadges = new ArrayList<>();
	private final Map<Integer, LoePointStepper> directPointControls = new HashMap<>();
	private final Map<Integer, LoePointStepper> adjustmentControls = new HashMap<>();
	private final Map<Integer, LoePointStepper> criterionPointControls = new HashMap<>();
	private final Map<Integer, Span> requirementResultBadges = new HashMap<>();
	private final Map<Integer, Span> requirementPointTexts = new HashMap<>();
	private final Map<Integer, TextArea> requirementCommentFields = new HashMap<>();
	private final Map<Integer, CriterionMarkdownViewer> requirementDescriptions = new HashMap<>();
	private final Map<Integer, Span> requirementCriterionIndicators = new HashMap<>();
	private final Map<Integer, Checkbox> criterionCheckboxes = new HashMap<>();
	private final Map<Integer, HorizontalLayout> criterionRows = new HashMap<>();
	private final Set<Integer> highlightedQuickCriteria = new HashSet<>();
	private final Set<Integer> highlightedInlineCriteria = new HashSet<>();
	private final Map<Integer, Integer> editedCriterionResults = new HashMap<>();
	private final Map<Integer, Integer> editedRequirementResults = new HashMap<>();
	private final Map<Integer, Integer> editedAdjustments = new HashMap<>();
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
	private final Span breadcrumb = new Span();
	private MenuItem pdfMenuItem;
	private MenuItem pupilPdfItem;
	private MenuItem teacherPdfItem;
	private LoePointRules pointRules;
	private Map<Integer, Integer> persistedCriterionResults = Map.of();
	private Map<Integer, Integer> persistedRequirementResults = Map.of();
	private Map<Integer, Integer> persistedAdjustments = Map.of();
	private Map<Integer, String> persistedRequirementComments = Map.of();
	private Runnable changeHandler = () -> {
	};

	public ExamResultsEditor(final CourseRepository courseRepository, final ExamRepository examRepository,
			final LevelOfExpectationsRepository levelOfExpectationsRepository,
			final GradingScaleRepository gradingScaleRepository) {
		super("tt-exam-results-editor");
		enableStatusTray();
		this.courseRepository = courseRepository;
		this.examRepository = examRepository;
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		fullscreenButton = new FullscreenButton(this);
		results = content();

		configurePupilSelector();
		configureSaveButton();
		configureDiscardButton();
		configureReloadButton();
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

	public boolean selectPupil(final int pupilId) {
		if (exam == null) {
			return false;
		}
		final Pupil pupil = examRepository.findPupils(exam.id()).stream()
				.filter(candidate -> candidate.id().equals(pupilId)).findFirst().orElse(null);
		if (pupil == null) {
			return false;
		}
		pupilSelector.setValue(pupil);
		return true;
	}

	public boolean focusRequirement(final LoeNavigationTarget target) {
		if (!contains(target)) {
			return false;
		}
		viewport.scrollTo(target.requirementAnchor());
		return true;
	}

	public void setChangeHandler(final Runnable changeHandler) {
		this.changeHandler = changeHandler == null ? () -> {
		} : changeHandler;
	}

	private void configurePupilSelector() {
		pupilSelector.setItemLabelGenerator(this::pupilLabel);
		pupilSelector.setWidth("16rem");
		pupilSelector.setMaxWidth("100%");
		pupilSelector.setAriaLabel("Schüler:in");
		pupilSelector.addValueChangeListener(event -> {
			if (refreshing) {
				selectedPupil = event.getValue();
				return;
			}
			if (isDirty()) {
				Notification.show("Bitte speichere die Ergebnisse zuerst.");
				refreshing = true;
				pupilSelector.setValue(event.getOldValue());
				refreshing = false;
				return;
			}
			selectedPupil = event.getValue();
			bindBreadcrumb();
			loadSelectedPupilResults();
		});
	}

	private void configureSaveButton() {
		saveButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
		saveButton.addClickListener(event -> saveController.save());
		saveController.setDirtySupplier(this::isDirty);
		saveController.setSaveAction(this::saveResults);
		saveController.register(saveButton);
	}

	private void configureDiscardButton() {
		discardButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		saveController.setDiscardAction(this::discardChanges);
		saveController.register(discardButton);
		TopTeacherDialogs.configureDiscardConfirmation(discardConfirmation, "Änderungen verwerfen?",
				saveController::discard);
	}

	private void configureReloadButton() {
		reloadButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		saveController.registerClean(reloadButton);
	}

	private void configureDeleteButton() {
		deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		TopTeacherDialogs.configureDeleteConfirmation(deleteConfirmation, "Ergebnisse löschen?",
				this::deleteSelectedPupilResults);
		updateDeleteButton();
	}

	private void configurePdfDownload() {
		pdfMenu.addClassName("tt-pdf-menu");
		pdfMenu.addThemeVariants(MenuBarVariant.LUMO_SMALL);
		pdfMenuItem = pdfMenu.addItem("Laden");
		pdfMenuItem.addComponentAsFirst(VaadinIcon.DOWNLOAD.create());
		pdfMenuItem.setAriaLabel("Ergebnisbogen herunterladen");
		pdfMenu.setTooltipText(pdfMenuItem, "Ergebnisbogen herunterladen");
		pupilPdfItem = pdfMenuItem.getSubMenu().addItem("Ergebnisbogen (Schüler:innen-Version)",
				event -> downloadPdf(false));
		teacherPdfItem = pdfMenuItem.getSubMenu().addItem("Ergebnisbogen (Lehrer:innen-Version)",
				event -> downloadPdf(true));
		updatePdfDownload();
	}

	private void refresh() {
		resetDesigner();
		clearRenderedResults();
		examPointsBadge = null;

		if (exam == null) {
			clearResultState();
			pointRules = null;
			updateActionButtons();
			showDesignerMessage(emptyState("Bitte wähle eine Klausur aus."));
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
		breadcrumb.addClassName("tt-designer-breadcrumb");
		toolbar().add(pupilSelector, saveButton, discardButton, deleteButton, pdfMenu, fullscreenButton,
				discardConfirmation, deleteConfirmation, reloadButton);
		toolbarSummary().add(breadcrumb, examPointsBadge);
		toolbarSummary().expand(breadcrumb);
		bindBreadcrumb();
	}

	private void bindBreadcrumb() {
		if (selectedPupil == null) {
			viewport.bindBreadcrumb(breadcrumb, "Ergebnisse", "Ergebnisse");
			return;
		}
		viewport.bindBreadcrumb(breadcrumb, pupilInitials(selectedPupil), pupilLabel(selectedPupil));
	}

	private void refreshPupils() {
		final List<Pupil> pupils = examRepository.findPupils(exam.id());
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
			results.add(emptyState("Dieser Klausur sind keine Schüler:innen zugeordnet."));
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
		directPointControls.clear();
		adjustmentControls.clear();
		criterionPointControls.clear();
		requirementResultBadges.clear();
		requirementPointTexts.clear();
		requirementCommentFields.clear();
		requirementDescriptions.clear();
		requirementCriterionIndicators.clear();
		criterionCheckboxes.clear();
		criterionRows.clear();
		highlightedQuickCriteria.clear();
		highlightedInlineCriteria.clear();
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
				final TextArea comment = requirementCommentFields.get(requirement.id());
				if (comment != null) {
					comment.setValue(currentRequirementComment(requirement));
				}
			});
			requirements.forEach(this::refreshRequirementControls);
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
		pointRules = exam.gradingScaleId() == null ? null
				: gradingScaleRepository.findById(exam.gradingScaleId()).map(LoePointRules::new).orElse(null);
	}

	private void loadResultState() {
		persistedCriterionResults = levelOfExpectationsRepository
				.findCriterionResultsByExamAndPupil(exam.id(), selectedPupil.id()).stream()
				.collect(Collectors.toMap(LoeCriterionResult::criterionId, LoeCriterionResult::pointUnits));
		editedCriterionResults.clear();
		editedCriterionResults.putAll(persistedCriterionResults);

		final List<LoeRequirementResult> requirementResults = levelOfExpectationsRepository
				.findRequirementResultsByExamAndPupil(exam.id(), selectedPupil.id());
		persistedRequirementResults = requirementResults.stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, LoeRequirementResult::pointUnits));
		editedRequirementResults.clear();
		editedRequirementResults.putAll(persistedRequirementResults);
		persistedAdjustments = requirementResults.stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, LoeRequirementResult::adjustmentUnits));
		editedAdjustments.clear();
		editedAdjustments.putAll(persistedAdjustments);

		persistedRequirementComments = requirementResults.stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, LoeRequirementResult::comment));
		editedRequirementComments.clear();
		editedRequirementComments.putAll(persistedRequirementComments);
	}

	private void clearResultState() {
		persistedCriterionResults = Map.of();
		persistedRequirementResults = Map.of();
		persistedAdjustments = Map.of();
		persistedRequirementComments = Map.of();
		editedCriterionResults.clear();
		editedRequirementResults.clear();
		editedAdjustments.clear();
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
		notifyChanged();
	}

	private Component partBlock(final LoePart part) {
		final VerticalLayout block = aggregationBlock("tt-results-part", part.title(), () -> pointsForPart(part));
		DesignerViewport.mark(block, LoeNavigationTarget.anchor("part", part.id()), part.title());
		categoriesFor(part).forEach(category -> block.add(categoryBlock(category)));
		return block;
	}

	private Component categoryBlock(final LoeCategory category) {
		final VerticalLayout block = aggregationBlock("tt-results-category", category.title(),
				() -> pointsForCategory(category));
		DesignerViewport.mark(block, LoeNavigationTarget.anchor("category", category.id()), category.title());
		tasksFor(category).forEach(task -> block.add(taskBlock(task)));
		return block;
	}

	private Component taskBlock(final LoeTask task) {
		final VerticalLayout block = aggregationBlock("tt-results-task", task.title(), () -> pointsForTask(task));
		DesignerViewport.mark(block, LoeNavigationTarget.anchor("task", task.id()), task.title());
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
		DesignerViewport.mark(block, LoeNavigationTarget.anchor("requirement", requirement.id()),
				requirementNumber(task, requirement));

		final List<LoeCriterion> requirementCriteria = criteriaFor(requirement);
		final Map<String, LoeCriterion> criteriaByKey = requirementCriteria.stream()
				.collect(Collectors.toMap(LoeCriterion::criterionKey, criterion -> criterion));
		final String descriptionMarkdown = normalized(requirement.descriptionMarkdown());

		final VerticalLayout descriptionArea = new VerticalLayout();
		descriptionArea.addClassName("tt-results-requirement-content");
		descriptionArea.setPadding(false);
		descriptionArea.setSpacing(false);
		descriptionArea.setWidthFull();

		final VerticalLayout descriptionMain = new VerticalLayout();
		descriptionMain.addClassName("tt-results-requirement-main");
		descriptionMain.setPadding(false);
		descriptionMain.setSpacing(false);
		descriptionMain.setWidthFull();
		if (!descriptionMarkdown.isBlank()) {
			final CriterionMarkdownViewer description = new CriterionMarkdownViewer(descriptionMarkdown);
			description.addCriterionAwardChangeListener(change -> {
				if (applyingResultState || (selectedPupil == null)) {
					return;
				}
				final LoeCriterion criterion = criteriaByKey.get(change.key());
				if (criterion != null) {
					setCriterionPointUnits(requirement, criterion, change.pointUnits());
				}
			});
			description.addCriterionHighlightChangeListener(change -> {
				final LoeCriterion criterion = criteriaByKey.get(change.key());
				if (criterion != null) {
					setCriterionHighlight(criterion, highlightedInlineCriteria, change.active());
				}
			});
			description.addClassName("tt-results-requirement-description");
			description.setWidthFull();
			requirementDescriptions.put(requirement.id(), description);
			descriptionMain.add(description);
		}
		if (!requirementCriteria.isEmpty()) {
			descriptionMain.add(criterionIndicator(requirement));
		}
		descriptionArea.add(descriptionMain, commentField(requirement));
		descriptionArea.setFlexGrow(1, descriptionMain);

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
		final long achieved = requirementCriteria.stream()
				.filter(criterion -> currentCriterionPointUnits(criterion) == criterion.pointUnits()).count();
		indicator.setText(achieved + " von " + requirementCriteria.size() + " Kriterien erfüllt");
	}

	private void setCriterionPointUnits(final LoeRequirement requirement, final LoeCriterion criterion,
			final int pointUnits) {
		if (selectedPupil == null || pointUnits < 0 || pointUnits > maximumCriterionPointUnits(requirement, criterion)) {
			return;
		}
		editedCriterionResults.put(criterion.id(), pointUnits);
		refreshRequirementControls(requirement);
	}

	private void setAdjustmentPointUnits(final LoeRequirement requirement, final int pointUnits) {
		if (selectedPupil == null || pointUnits < 0 || pointUnits > maximumAdjustmentPointUnits(requirement)) {
			return;
		}
		editedAdjustments.put(requirement.id(), pointUnits);
		refreshRequirementControls(requirement);
	}

	private void setDirectPointUnits(final LoeRequirement requirement, final int pointUnits) {
		if (selectedPupil == null || pointUnits < 0 || pointUnits > LoePointUnits.fromWholePoints(requirement.maxPoints())) {
			return;
		}
		editedRequirementResults.put(requirement.id(), pointUnits);
		refreshRequirementControls(requirement);
	}

	private void refreshRequirementControls(final LoeRequirement requirement) {
		updatingCriterionControls = true;
		try {
			final List<LoeCriterion> requirementCriteria = criteriaFor(requirement);
			final CriterionMarkdownViewer description = requirementDescriptions.get(requirement.id());
			if (description != null) {
				description.setCriterionAwards(requirementCriteria.stream()
						.map(criterion -> new CriterionMarkdownViewer.AwardState(criterion.criterionKey(),
								criterion.label(), criterion.pointUnits(), currentCriterionPointUnits(criterion),
								maximumCriterionPointUnits(requirement, criterion)))
						.toList());
			}
			requirementCriteria.forEach(criterion -> {
				final int awardedUnits = currentCriterionPointUnits(criterion);
				final Checkbox checkbox = criterionCheckboxes.get(criterion.id());
				if (checkbox != null) {
					checkbox.setValue(awardedUnits == criterion.pointUnits());
					checkbox.setIndeterminate(awardedUnits > 0 && awardedUnits < criterion.pointUnits());
					checkbox.setEnabled(awardedUnits == criterion.pointUnits()
							|| maximumCriterionPointUnits(requirement, criterion) >= criterion.pointUnits());
				}
				final LoePointStepper control = criterionPointControls.get(criterion.id());
				if (control != null) {
					control.setMaximumPointUnits(maximumCriterionPointUnits(requirement, criterion));
					control.setPointUnits(awardedUnits);
				}
			});
			final LoePointStepper direct = directPointControls.get(requirement.id());
			if (direct != null) {
				direct.setMaximumPointUnits(LoePointUnits.fromWholePoints(requirement.maxPoints()));
				direct.setPointUnits(currentRequirementRawUnits(requirement));
			}
			final LoePointStepper adjustment = adjustmentControls.get(requirement.id());
			if (adjustment != null) {
				adjustment.setMaximumPointUnits(maximumAdjustmentPointUnits(requirement));
				adjustment.setPointUnits(currentAdjustmentPointUnits(requirement));
			}
		} finally {
			updatingCriterionControls = false;
		}
		refreshCriterionIndicator(requirement);
		refreshRequirementPointText(requirement);
		refreshPointBadges();
		updateActionButtons();
	}

	private void setCriterionHighlight(final LoeCriterion criterion, final Set<Integer> source,
			final boolean active) {
		final boolean changed = active ? source.add(criterion.id()) : source.remove(criterion.id());
		if (!changed) {
			return;
		}
		final boolean highlighted = highlightedQuickCriteria.contains(criterion.id())
				|| highlightedInlineCriteria.contains(criterion.id());
		final HorizontalLayout row = criterionRows.get(criterion.id());
		if (row != null) {
			row.getElement().getClassList().set("tt-results-criterion-highlighted", highlighted);
		}
		final CriterionMarkdownViewer description = requirementDescriptions.get(criterion.requirementId());
		if (description != null) {
			description.setHighlightedCriterionKeys(criteria.stream()
					.filter(candidate -> candidate.requirementId().equals(criterion.requirementId()))
					.filter(candidate -> highlightedQuickCriteria.contains(candidate.id())
							|| highlightedInlineCriteria.contains(candidate.id()))
					.map(LoeCriterion::criterionKey).toList());
		}
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
		final Span resultBadge = new Span();
		resultBadge.addClassNames("tt-eh-points", "tt-results-requirement-total");
		requirementResultBadges.put(requirement.id(), resultBadge);

		final Span pointsText = new Span();
		pointsText.addClassName("tt-results-points-text");
		requirementPointTexts.put(requirement.id(), pointsText);
		refreshRequirementPointText(requirement);

		final VerticalLayout control = new VerticalLayout(resultBadge, pointsText);
		control.addClassName("tt-results-points-control");
		control.setPadding(false);
		control.setSpacing(false);
		control.setWidthFull();
		if (requirementCriteria.isEmpty()) {
			final LoePointStepper direct = new LoePointStepper("Erreichte Punkte");
			direct.setChangeHandler(units -> setDirectPointUnits(requirement, units));
			directPointControls.put(requirement.id(), direct);
			final Popover popover = new Popover(direct);
			popover.setTarget(resultBadge);
			popover.setPosition(PopoverPosition.BOTTOM);
			popover.setOpenOnClick(true);
			popover.setCloseOnOutsideClick(true);
			resultBadge.getElement().setAttribute("role", "button");
			resultBadge.getElement().setAttribute("tabindex", "0");
			resultBadge.getElement().setAttribute("aria-label", "Erreichte Punkte bearbeiten");
			resultBadge.getElement().addEventListener("keydown", event -> popover.open())
					.setFilter("event.key === 'Enter' || event.key === ' '");
			control.add(popover);
		} else {
			control.add(criteriaChecklist(requirement, requirementCriteria));
			final LoePointStepper adjustment = new LoePointStepper("Zusatzpunkte");
			adjustment.setChangeHandler(units -> setAdjustmentPointUnits(requirement, units));
			adjustmentControls.put(requirement.id(), adjustment);
			final VerticalLayout adjustmentArea = new VerticalLayout(new Span("Zusatzpunkte"), adjustment);
			adjustmentArea.addClassName("tt-results-adjustment");
			adjustmentArea.setPadding(false);
			adjustmentArea.setSpacing(false);
			control.add(adjustmentArea);
		}
		return control;
	}

	private Component criteriaChecklist(final LoeRequirement requirement,
			final List<LoeCriterion> requirementCriteria) {
		final VerticalLayout checklist = new VerticalLayout();
		checklist.addClassName("tt-results-criteria-checklist");
		checklist.setPadding(false);
		checklist.setSpacing(false);
		checklist.setWidthFull();
		requirementCriteria.forEach(criterion -> checklist.add(criterionCheckboxRow(requirement, criterion)));
		return checklist;
	}

	private Component criterionCheckboxRow(final LoeRequirement requirement, final LoeCriterion criterion) {
		final Checkbox checkbox = new Checkbox();
		checkbox.addClassName("tt-results-criterion-checkbox");
		checkbox.setAriaLabel(criterion.label() + ": volle Punktzahl");
		checkbox.addValueChangeListener(event -> {
			if (applyingResultState || updatingCriterionControls) {
				return;
			}
			setCriterionPointUnits(requirement, criterion,
					currentCriterionPointUnits(criterion) == criterion.pointUnits() ? 0 : criterion.pointUnits());
		});
		criterionCheckboxes.put(criterion.id(), checkbox);

		final LoePointStepper points = new LoePointStepper(criterion.label() + " Punkte");
		points.setChangeHandler(units -> setCriterionPointUnits(requirement, criterion, units));
		criterionPointControls.put(criterion.id(), points);

		final HorizontalLayout row = new HorizontalLayout(checkbox, points);
		row.addClassName("tt-results-criterion-checkbox-row");
		row.setAlignItems(Alignment.CENTER);
		row.setPadding(false);
		row.setSpacing(false);
		row.setWidthFull();
		row.getElement().setAttribute("aria-label", criterion.label() + ": "
				+ LoePointUnits.formatGerman(criterion.pointUnits())
				+ (criterion.pointUnits() == LoePointUnits.UNITS_PER_POINT ? " Punkt" : " Punkte") + " möglich");
		row.getElement().addEventListener("mouseenter",
				event -> setCriterionHighlight(criterion, highlightedQuickCriteria, true));
		row.getElement().addEventListener("mouseleave",
				event -> setCriterionHighlight(criterion, highlightedQuickCriteria, false));
		row.getElement().addEventListener("focusin",
				event -> setCriterionHighlight(criterion, highlightedQuickCriteria, true));
		row.getElement().addEventListener("focusout",
				event -> setCriterionHighlight(criterion, highlightedQuickCriteria, false))
				.setFilter("!this.contains(event.relatedTarget)");
		criterionRows.put(criterion.id(), row);
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

	private Component header(final String titleText, final String badgeLabel,
			final Supplier<LoePoints> pointsSupplier) {
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
		if (pointsText != null) {
			pointsText.setText(currentRequirementPoints(requirement) + " von " + requirement.maxPoints() + " Punkten");
		}
		final Span badge = requirementResultBadges.get(requirement.id());
		if (badge != null) {
			badge.setText(String.valueOf(currentRequirementPoints(requirement)));
			final int rawUnits = currentRequirementRawUnits(requirement);
			badge.getElement().setAttribute("title", rawUnits % LoePointUnits.UNITS_PER_POINT == 0
					? "Erreichte Punkte: " + currentRequirementPoints(requirement)
					: "Gerundet: " + LoePointUnits.formatGerman(rawUnits) + " → "
							+ currentRequirementPoints(requirement) + " Punkte");
		}
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

	private int currentCriterionPointUnits(final LoeCriterion criterion) {
		return editedCriterionResults.getOrDefault(criterion.id(), persistedCriterionPointUnits(criterion));
	}

	private int persistedCriterionPointUnits(final LoeCriterion criterion) {
		return persistedCriterionResults.getOrDefault(criterion.id(), 0);
	}

	private int currentRequirementPoints(final LoeRequirement requirement) {
		return LoePointUnits.roundedWholePoints(currentRequirementRawUnits(requirement));
	}

	private int currentRequirementRawUnits(final LoeRequirement requirement) {
		return criteriaFor(requirement).isEmpty()
				? editedRequirementResults.getOrDefault(requirement.id(), persistedRequirementPointUnits(requirement))
				: criterionSubtotalUnits(requirement) + currentAdjustmentPointUnits(requirement);
	}

	private int criterionSubtotalUnits(final LoeRequirement requirement) {
		return criteriaFor(requirement).stream().mapToInt(this::currentCriterionPointUnits).sum();
	}

	private int maximumCriterionPointUnits(final LoeRequirement requirement, final LoeCriterion criterion) {
		final int otherAwards = criterionSubtotalUnits(requirement) - currentCriterionPointUnits(criterion);
		return Math.min(criterion.pointUnits(),
				Math.max(0, LoePointUnits.fromWholePoints(requirement.maxPoints())
						- currentAdjustmentPointUnits(requirement) - otherAwards));
	}

	private int maximumAdjustmentPointUnits(final LoeRequirement requirement) {
		return Math.max(0, LoePointUnits.fromWholePoints(requirement.maxPoints())
				- criterionSubtotalUnits(requirement));
	}

	private int currentAdjustmentPointUnits(final LoeRequirement requirement) {
		return editedAdjustments.getOrDefault(requirement.id(), persistedAdjustmentPointUnits(requirement));
	}

	private int persistedAdjustmentPointUnits(final LoeRequirement requirement) {
		return persistedAdjustments.getOrDefault(requirement.id(), 0);
	}

	private int persistedRequirementPointUnits(final LoeRequirement requirement) {
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
				.anyMatch(criterion -> currentCriterionPointUnits(criterion) != persistedCriterionPointUnits(criterion))
				|| requirements.stream().anyMatch(
						requirement -> (criteriaFor(requirement).isEmpty()
								&& currentRequirementRawUnits(requirement) != persistedRequirementPointUnits(requirement))
								|| currentAdjustmentPointUnits(requirement) != persistedAdjustmentPointUnits(requirement)
								|| !currentRequirementComment(requirement)
										.equals(persistedRequirementComment(requirement)));
	}

	private void discardChanges() {
		editedCriterionResults.clear();
		editedCriterionResults.putAll(persistedCriterionResults);
		editedRequirementResults.clear();
		editedRequirementResults.putAll(persistedRequirementResults);
		editedAdjustments.clear();
		editedAdjustments.putAll(persistedAdjustments);
		editedRequirementComments.clear();
		editedRequirementComments.putAll(persistedRequirementComments);
		applyResultState();
		discardConfirmation.close();
	}

	private void openDiscardConfirmation() {
		if (!isDirty()) {
			return;
		}
		final String pupilName = selectedPupil == null ? "" : pupilLabel(selectedPupil);
		discardConfirmation.setText("Die nicht gespeicherten Änderungen für " + pupilName
				+ " werden verworfen. Diese Aktion kann nicht rückgängig gemacht werden.");
		discardConfirmation.open();
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
				.filter(criterion -> currentCriterionPointUnits(criterion) != persistedCriterionPointUnits(criterion))
				.map(criterion -> new LoeCriterionResult(criterion.id(), selectedPupil.id(),
						currentCriterionPointUnits(criterion)))
				.forEach(levelOfExpectationsRepository::saveCriterionResult);
		requirements.stream()
				.filter(requirement -> currentRequirementRawUnits(requirement) != persistedRequirementPointUnits(requirement)
						|| currentAdjustmentPointUnits(requirement) != persistedAdjustmentPointUnits(requirement)
						|| !currentRequirementComment(requirement).equals(persistedRequirementComment(requirement)))
				.map(requirement -> new LoeRequirementResult(requirement.id(), selectedPupil.id(),
						currentRequirementRawUnits(requirement), currentAdjustmentPointUnits(requirement),
						currentRequirementComment(requirement)))
				.forEach(levelOfExpectationsRepository::saveRequirementResult);

		persistedCriterionResults = criteria.stream()
				.collect(Collectors.toMap(LoeCriterion::id, this::currentCriterionPointUnits));
		persistedRequirementResults = requirements.stream()
				.collect(Collectors.toMap(LoeRequirement::id, this::currentRequirementRawUnits));
		persistedAdjustments = requirements.stream()
				.collect(Collectors.toMap(LoeRequirement::id, this::currentAdjustmentPointUnits));
		persistedRequirementComments = requirements.stream()
				.collect(Collectors.toMap(LoeRequirement::id, this::currentRequirementComment));
		updateActionButtons();
		notifyChanged();
	}

	private void updateActionButtons() {
		saveController.update();
		updateDeleteButton();
		updatePdfDownload();
	}

	private void refreshFromDatabase() {
		if (!isDirty()) {
			viewport.refreshPreservingPosition(this::refresh);
		}
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
		final String fileName = teacherVersion ? "level-of-expectations-teacher.pdf" : "level-of-expectations.pdf";
		return UiUrls.contextRelative("/export/exams/" + exam.id() + "/pupils/" + selectedPupil.id() + "/" + fileName);
	}

	private boolean hasPersistedResults() {
		return !persistedCriterionResults.isEmpty() || !persistedRequirementResults.isEmpty();
	}

	private boolean validateRequirementPoints() {
		for (final LoeRequirement requirement : requirements) {
			final int rawUnits = currentRequirementRawUnits(requirement);
			if (rawUnits < 0 || rawUnits > LoePointUnits.fromWholePoints(requirement.maxPoints())) {
				Notification.show("Punkte müssen zwischen 0 und " + requirement.maxPoints() + " liegen.");
				return false;
			}
			if (criteriaFor(requirement).stream().anyMatch(criterion -> currentCriterionPointUnits(criterion) < 0
					|| currentCriterionPointUnits(criterion) > criterion.pointUnits())) {
				Notification.show("Kriterienpunkte liegen außerhalb des erlaubten Bereichs.");
				return false;
			}
		}
		if (pointRules == null) {
			Notification.show("Für die Klausur wurde kein Notenschlüssel gefunden.");
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

	private void notifyChanged() {
		changeHandler.run();
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

	private static String pupilInitials(final Pupil pupil) {
		return (pupil.surname().substring(0, 1) + pupil.name().substring(0, 1)).toUpperCase(Locale.GERMAN);
	}

	private String pdfFileName(final boolean teacherVersion) {
		final String prefix = teacherVersion ? "lehrerversion-erwartungshorizont" : "erwartungshorizont";
		return prefix + "-" + fileNamePart(exam.title()) + "-" + fileNamePart(selectedPupil.surname()) + "-"
				+ fileNamePart(selectedPupil.name()) + ".pdf";
	}

	private static String fileNamePart(final String value) {
		return normalizedStatic(value).replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("(^-+|-+$)", "").toLowerCase();
	}

	private String normalized(final String value) {
		return normalizedStatic(value);
	}

	private static String normalizedStatic(final String value) {
		return value == null ? "" : value;
	}
}
