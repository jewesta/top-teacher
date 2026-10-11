package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.flow.server.VaadinSession;

import tools.jackson.databind.node.JsonNodeFactory;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.component.ActionToolbar;
import de.westarps.topteacher.ui.component.FullscreenButton;
import de.westarps.topteacher.ui.component.loe.LevelOfExpectationsEditor.DesignState;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.tray.StatusTray;
import de.westarps.vaadin.tray.TrayState;

class LevelOfExpectationsEditorTests {

	private static final int GRADING_SCALE_ID = 30;
	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1), null, GRADING_SCALE_ID);
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "Beschreibung", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Teilaufgabe 1", "Aufgabentext", 5, 0);
	private static final LoeRequirement REQUIREMENT = new LoeRequirement(4, TASK.id(), "Requirement text", 5, false, 0);
	private static final LoePart SECOND_PART = new LoePart(5, EXAM.id(), "Klausurteil B", 1);
	private static final LoeCategory SECOND_CATEGORY = new LoeCategory(6, SECOND_PART.id(), "Sprache", "", 0);
	private static final LoeTask SECOND_TASK = new LoeTask(7, SECOND_CATEGORY.id(), "Teilaufgabe 2", "", 15, 0);
	private static final LoeRequirement SECOND_REQUIREMENT = new LoeRequirement(8, SECOND_TASK.id(),
			"Second requirement", 15, false, 0);
	private static final LoeRequirement FOLLOWING_REQUIREMENT = new LoeRequirement(9, TASK.id(),
			"Very long user-entered requirement text that should not become a summary title", 2, false, 1);
	private static final LoeRequirement BONUS_REQUIREMENT = new LoeRequirement(10, TASK.id(), "Bonus requirement", 4,
			true, 1);
	private static final LoeRequirement NEW_REQUIREMENT = new LoeRequirement(11, TASK.id(), "", 0, false, 1);

	@Test
	void staleSaveKeepsTheDirtyRequirementMarkdownAndDoesNotInvokePersistence() {
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
			when(repository.designRevision(EXAM.id())).thenReturn(4L);
			final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
			editor.setExam(EXAM);
			final MarkdownEditor source = markdownEditor(editor, REQUIREMENT.descriptionMarkdown());
			source.setValue("Unsaved requirement source");
			clearInvocations(repository);
			RepositoryDesignTestSupport.rejectDesignWrites(repository, 4, 5);

			saveButtons(editor).getFirst().click();

			assertThat(source.getValue()).isEqualTo("Unsaved requirement source");
			assertThat(editor.hasUnsavedChanges()).isTrue();
			assertThat(saveButtons(editor).getFirst().isEnabled()).isTrue();
			verify(repository, never()).saveRequirement(any());
			verify(repository, never()).findRequirementsByExamId(anyInt());
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void placesRequirementActionsBeforeMarkdownInDesignAndCorrectionMode() {
		for (final boolean correctionMode : List.of(false, true)) {
			final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
			when(repository.hasResultsForExam(EXAM.id())).thenReturn(correctionMode);
			final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
			editor.setExam(EXAM);
			final VerticalLayout requirement = components(editor, VerticalLayout.class).stream()
					.filter(layout -> layout.hasClassName("tt-eh-requirement")).findFirst().orElseThrow();
			final List<Component> content = requirement.getChildren().toList();

			assertThat(content).hasSize(3);
			assertThat(content.getFirst().hasClassName("tt-eh-summary")).isTrue();
			assertThat(content.get(1)).isInstanceOf(ActionToolbar.class);
			assertThat(content.get(1).hasClassName("tt-eh-actions")).isTrue();
			assertThat(content.getLast().hasClassName("tt-markdown-block")).isTrue();
			assertThat(components(content.getLast(), MarkdownEditor.class)).hasSize(1);
		}
	}

	@Test
	void showsAllocationWarningsInTheStatusTray() {
		final LoeRequirement underallocatedRequirement = requirementWithCriteria(REQUIREMENT, 5, 1);
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(
				repositoryWithHierarchy(underallocatedRequirement));

		editor.setExam(EXAM);

		assertThat(components(editor, StatusTray.class)).singleElement().satisfies(tray -> {
			assertThat(tray.isVisible()).isTrue();
			assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
			assertThat(tray.getItems()).singleElement()
					.satisfies(result -> assertThat(result.message())
							.isEqualTo("Aufgabe 1 · Anforderung 1: Ordne weitere 4 Kriterienpunkte zu."));
		});
		assertThat(editor.getDesignState()).isEqualTo(DesignState.INCOMPLETE);
	}

	@Test
	void opensAndEmphasizesTheRequirementFromItsStatusEntry() {
		final LoeRequirement underallocatedRequirement = requirementWithCriteria(REQUIREMENT, 5, 1);
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(
				repositoryWithHierarchy(underallocatedRequirement));
		editor.setExam(EXAM);
		final StatusTray tray = components(editor, StatusTray.class).getFirst();
		globalToggleButtons(editor).getFirst().click();
		tray.show();

		final Anchor link = components(tray, Anchor.class).stream()
				.filter(anchor -> anchor.getText().startsWith("Aufgabe 1 · Anforderung 1:"))
				.findFirst().orElseThrow();
		assertThat(link.getHref()).isEqualTo("exams#tt-eh-requirement-" + REQUIREMENT.id());
		link.getElement().getNode().getFeature(ElementListenerMap.class)
				.fireEvent(new DomEvent(link.getElement(), "click", JsonNodeFactory.instance.objectNode()));

		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsOnly(true);
		assertThat(components(editor, VerticalLayout.class).stream()
				.filter(layout -> layout.getClassNames().contains("tt-eh-requirement")).findFirst().orElseThrow()
				.getClassNames()).contains("animate__animated", "animate__pulse", "animate__faster");
	}

	@Test
	void marksAMatchingLevelOfExpectationsAsCompleteAndKeepsTheTrayPeeking() {
		final LoeRequirement completeRequirement = requirementWithCriteria(REQUIREMENT, 2, 2);
		final LevelOfExpectationsEditor editor = editorWithGradingScale(
				repositoryWithHierarchy(completeRequirement), 2);

		editor.setExam(EXAM);

		assertThat(editor.getDesignState()).isEqualTo(DesignState.COMPLETE);
		assertThat(components(editor, StatusTray.class)).singleElement().satisfies(tray -> {
			assertThat(tray.getItems()).isEmpty();
			assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		});
	}

	@Test
	void allowsSavingAnUnderAllocatedIntermediateDesign() {
		final LoeRequirement completeRequirement = requirementWithCriteria(REQUIREMENT, 2, 2);
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy(completeRequirement);
		final LevelOfExpectationsEditor editor = editorWithGradingScale(repository, 4);
		editor.setExam(EXAM);
		final String updatedDescription = completeRequirement.descriptionMarkdown() + "\n\nZusatz";

		markdownEditor(editor, completeRequirement.descriptionMarkdown()).setValue(updatedDescription);

		assertThat(editor.getDesignState()).isEqualTo(DesignState.INCOMPLETE);
		assertThat(saveButtons(editor)).extracting(Button::isEnabled).containsOnly(true);

		saveButtons(editor).getFirst().click();

		verify(repository).saveRequirement(new LoeRequirement(completeRequirement.id(), completeRequirement.taskId(),
				updatedDescription, completeRequirement.maxPoints(), completeRequirement.bonus(),
				completeRequirement.sortOrder()));
	}

	@Test
	void blocksSavingWhenACriterionAllocationExceedsItsRequirement() {
		final LoeRequirement completeRequirement = requirementWithCriteria(REQUIREMENT, 2, 2);
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy(completeRequirement);
		final LevelOfExpectationsEditor editor = editorWithGradingScale(repository, 2);
		editor.setExam(EXAM);

		markdownEditor(editor, completeRequirement.descriptionMarkdown())
				.setValue(completeRequirement.descriptionMarkdown() + " [Kriterium 3](crit:3)");

		assertThat(saveButtons(editor)).extracting(Button::isEnabled).containsOnly(false);
		assertThat(discardButtons(editor)).extracting(Button::isEnabled).containsOnly(true);
		assertThat(components(editor, StatusTray.class).getFirst().getItems()).singleElement()
				.satisfies(result -> assertThat(result.message())
						.isEqualTo("Aufgabe 1 · Anforderung 1: Ordne einen Kriterienpunkt weniger zu."));
		verify(repository, never()).saveRequirement(any());
	}

	@Test
	void blocksSavingWhenRegularRequirementPointsExceedTheGradingScale() {
		final LoeRequirement completeRequirement = requirementWithCriteria(REQUIREMENT, 2, 2);
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy(completeRequirement);
		final LevelOfExpectationsEditor editor = editorWithGradingScale(repository, 2);
		editor.setExam(EXAM);

		setRequirementMaxPoints(editor, 3);

		assertThat(saveButtons(editor)).extracting(Button::isEnabled).containsOnly(false);
		assertThat(discardButtons(editor)).extracting(Button::isEnabled).containsOnly(true);
		assertThat(components(editor, StatusTray.class).getFirst().getItems())
				.anySatisfy(result -> assertThat(result.message())
						.isEqualTo("Aufgabe 1: Ordne Anforderungen einen Punkt weniger zu."));
		verify(repository, never()).saveRequirement(any());
	}

	@Test
	void derivesTheLockedStateWhenResultsExist() {
		final LoeRequirement completeRequirement = requirementWithCriteria(REQUIREMENT, 2, 2);
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy(completeRequirement);
		when(repository.hasResultsForExam(EXAM.id())).thenReturn(true);
		final LevelOfExpectationsEditor editor = editorWithGradingScale(repository, 2);

		editor.setExam(EXAM);

		assertThat(editor.getDesignState()).isEqualTo(DesignState.LOCKED);
	}

	@Test
	void preservesCollapsedDetailsWhenRefreshingSameExam() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		assertThat(details).hasSize(3);
		assertThat(components(editor, FullscreenButton.class)).hasSize(1);

		details.get(1).setOpened(false);
		editor.setExam(EXAM);

		final List<Details> refreshedDetails = components(editor, Details.class);
		assertThat(refreshedDetails).hasSize(3);
		assertThat(refreshedDetails).extracting(Details::isOpened).containsExactly(true, false, true);
	}

	@Test
	void reloadsFromTheDatabaseOnlyWhileTheEditorIsClean() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Button reload = buttonsByAriaLabel(editor, "Neu laden").getFirst();
		clearInvocations(repository);

		assertThat(reload.isEnabled()).isTrue();
		assertThat(toolbar(editor).getChildren().toList().getLast()).isSameAs(reload);
		reload.click();

		verify(repository).findPartsByExamId(EXAM.id());
		verify(repository).findCategoriesByExamId(EXAM.id());
		verify(repository).findTasksByExamId(EXAM.id());
		verify(repository).findRequirementsByExamId(EXAM.id());

		setRequirementMaxPoints(editor, 7);

		assertThat(buttonsByAriaLabel(editor, "Neu laden").getFirst().isEnabled()).isFalse();
	}

	@Test
	void marksTheHierarchyAndExpandsThePathForDeepLinks() {
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repositoryWithHierarchy());
		editor.setExam(EXAM);
		globalToggleButtons(editor).getFirst().click();

		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsOnly(false);
		assertThat(
				editor.focusRequirement(new LoeNavigationTarget(PART.id(), CATEGORY.id(), TASK.id(), REQUIREMENT.id())))
						.isTrue();
		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsOnly(true);
		assertThat(anchorKeys(editor)).contains("part:1", "category:2", "task:3", "requirement:4");
		assertThat(breadcrumbSegments(editor)).containsExactly("Klausurteil A", "Inhalt", "Teilaufgabe 1", "1");
		assertThat(breadcrumb(editor).getElement().getAttribute("data-tt-breadcrumb-root-label")).isEqualTo("EH");
		assertThat(breadcrumb(editor).getElement().getAttribute("data-tt-breadcrumb-root-title"))
				.isEqualTo("Erwartungshorizont");
		assertThat(editor.focusRequirement(
				new LoeNavigationTarget(SECOND_PART.id(), CATEGORY.id(), TASK.id(), REQUIREMENT.id()))).isFalse();
	}

	@Test
	void scrollsToNewRequirementAfterRefreshingTheHierarchy() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		when(repository.nextRequirementSortOrder(TASK.id())).thenReturn(NEW_REQUIREMENT.sortOrder());
		when(repository.saveRequirement(any())).thenReturn(NEW_REQUIREMENT);
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT),
				List.of(REQUIREMENT, NEW_REQUIREMENT));
		final UI ui = new UI();
		ui.getInternals().setSession(mock(VaadinSession.class));
		UI.setCurrent(ui);
		try {
			final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
			ui.add(editor);
			editor.setExam(EXAM);
			ui.getInternals().dumpPendingJavaScriptInvocations();

			button(editor, "Anforderung hinzufügen").click();
			ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
			final List<PendingJavaScriptInvocation> invocations = ui.getInternals().dumpPendingJavaScriptInvocations();

			assertThat(anchorKeys(editor)).contains("requirement:" + NEW_REQUIREMENT.id());
			assertThat(invocations)
					.filteredOn(invocation -> invocation.getInvocation().getExpression().contains("pendingViewers"))
					.anySatisfy(invocation -> assertThat(invocation.getInvocation().getParameters())
							.contains("requirement:" + NEW_REQUIREMENT.id()));
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void bindsTheBreadcrumbWhenTheHierarchyIsAttached() {
		final UI ui = new UI();
		ui.getInternals().setSession(mock(VaadinSession.class));
		UI.setCurrent(ui);
		try {
			final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repositoryWithHierarchy());
			ui.add(editor);
			editor.setExam(EXAM);
			ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

			assertThat(ui.getInternals().dumpPendingJavaScriptInvocations())
					.anySatisfy(invocation -> assertThat(invocation.getInvocation().getExpression())
							.contains("data-tt-breadcrumb-bound"));
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void savesRequirementWithoutRefreshingWholeTree() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		setRequirementMaxPoints(editor, 4);

		assertThat(badgeTexts(editor)).contains("4", "100 %");
		assertThat(components(editor, LoePointBadge.class)).extracting(LoePointBadge::getText).containsOnly("4");
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);

		final List<Button> saveButtons = saveButtons(editor);
		assertThat(saveButtons).hasSize(1);
		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 4, REQUIREMENT.bonus(), REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("4", "100 %");
	}

	@Test
	void savesBonusRequirementWithoutAddingPointsToTotals() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		setRequirementMaxPoints(editor, 7);
		bonusButtons(editor).getFirst().click();

		assertThat(badgeTexts(editor)).contains("0", "100 %");
		assertThat(components(editor, LoeWholePointStepper.class)).extracting(LoeWholePointStepper::getValue)
				.containsExactly(7);
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);

		final List<Button> saveButtons = saveButtons(editor);
		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, true, REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(components(editor, LoePointBadge.class)).extracting(LoePointBadge::getText).containsOnly("0");
	}

	@Test
	void enablesAllSaveButtonsOnlyWhileLevelOfExpectationsIsDirty() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final List<Button> saveButtons = saveButtons(editor);
		final List<Button> discardButtons = discardButtons(editor);

		assertThat(saveButtons).hasSize(1).extracting(Button::isEnabled).containsOnly(false);
		assertThat(discardButtons).hasSize(1).extracting(Button::isEnabled).containsOnly(false);

		setRequirementMaxPoints(editor, 4);

		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(true);
		assertThat(discardButtons).extracting(Button::isEnabled).containsOnly(true);

		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 4, REQUIREMENT.bonus(), REQUIREMENT.sortOrder()));
		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(false);
		assertThat(discardButtons).extracting(Button::isEnabled).containsOnly(false);
	}

	@Test
	void keepsPartCategoryAndTaskReadOnlyBeforeResultsExist() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);

		assertThat(components(editor, TextField.class)).isEmpty();
		assertThat(components(editor, Span.class).stream().map(Span::getText)).contains(PART.title(), CATEGORY.title(),
				TASK.title());
		assertThat(buttonsByAriaLabel(editor, "Löschen...")).hasSize(1);
	}

	@Test
	void correctionModeLocksStructureButKeepsTextCorrectionsEditable() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		when(repository.hasResultsForExam(EXAM.id())).thenReturn(true);
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		assertThat(button(editor, "Anforderung hinzufügen").isEnabled()).isFalse();
		assertThat(buttonsByAriaLabel(editor, "Löschen...")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Nach oben")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Nach unten")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Max. Punkte verringern")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Max. Punkte erhöhen")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(bonusButtons(editor)).extracting(Button::isEnabled).containsOnly(false);

		assertThat(components(editor, TextField.class)).isEmpty();
		assertThat(markdownEditor(editor, REQUIREMENT.descriptionMarkdown()).isEnabled()).isTrue();
	}

	@Test
	void structureAddButtonsUseSecondaryStyle() {
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repositoryWithHierarchy());

		editor.setExam(EXAM);

		assertThat(button(editor, "Anforderung hinzufügen").getThemeNames())
				.doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());
	}

	@Test
	void structureDeleteButtonsAskForConfirmationAndUseNeutralIconStyle() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
			ui.add(editor);
			editor.setExam(EXAM);
			clearInvocations(repository);
			final Button deleteButton = buttonsByAriaLabel(editor, "Löschen...").getFirst();
			assertThat(deleteButton.getTooltip().getText()).isEqualTo("Löschen...");

			assertThat(deleteButton.getThemeNames()).doesNotContain(ButtonVariant.LUMO_ERROR.getVariantName());

			deleteButton.click();

			verify(repository, never()).deleteRequirement(REQUIREMENT.id());
			final ConfirmDialog confirmation = components(ui, ConfirmDialog.class).stream()
					.filter(dialog -> "Anforderung löschen?".equals(dialog.getElement().getProperty("header")))
					.findFirst().orElseThrow();
			assertThat(confirmation.isOpened()).isTrue();
			assertThat(confirmation.getElement().getProperty("header")).isEqualTo("Anforderung löschen?");

			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

			verify(repository).deleteRequirement(REQUIREMENT.id());
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void discardRestoresSavedLevelOfExpectationsAfterBlockedCorrectionModeEdit() {
		final String savedDescription = "Nutzt die [korrekte Zeitform](crit:1) und [präzise Wortwahl](crit:2).";
		final LoeRequirement taggedRequirement = new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				savedDescription, REQUIREMENT.maxPoints(), REQUIREMENT.bonus(), REQUIREMENT.sortOrder());
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy(taggedRequirement);
		when(repository.hasResultsForExam(EXAM.id())).thenReturn(true);
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

			editor.setExam(EXAM);
			final MarkdownEditor requirementDescription = markdownEditor(editor, savedDescription);
			final Button saveButton = saveButtons(editor).getFirst();
			final Button discardButton = discardButtons(editor).getFirst();

			requirementDescription.setValue("Nutzt die korrekte Zeitform und präzise Wortwahl.");

			assertThat(saveButton.isEnabled()).isTrue();
			assertThat(discardButton.isEnabled()).isTrue();

			saveButton.click();

			verify(repository, never()).saveRequirement(any());
			assertThat(saveButton.isEnabled()).isTrue();
			assertThat(discardButton.isEnabled()).isTrue();

			discardButton.click();

			final ConfirmDialog confirmation = components(editor, ConfirmDialog.class).getFirst();
			assertThat(confirmation.isOpened()).isTrue();
			assertThat(requirementDescription.getValue()).isEqualTo("Nutzt die korrekte Zeitform und präzise Wortwahl.");
			assertThat(editor.hasUnsavedChanges()).isTrue();
			confirmation.close();
			assertThat(editor.hasUnsavedChanges()).isTrue();
			discardButton.click();
			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

			assertThat(confirmation.isOpened()).isFalse();
			assertThat(markdownEditor(editor, savedDescription).getValue()).isEqualTo(savedDescription);
			assertThat(saveButtons(editor)).extracting(Button::isEnabled).containsOnly(false);
			assertThat(discardButtons(editor)).extracting(Button::isEnabled).containsOnly(false);
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void showsAssignedRegularPointsWithoutBonusTotals() {
		final LevelOfExpectationsRepository repository = repositoryWithRegularAndBonusRequirement();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("5", "100 %");
		assertThat(components(editor, LoePointBadge.class)).extracting(LoePointBadge::getText).containsOnly("5");
		assertThat(components(editor, LoeAllocationMessage.class).stream()
				.filter(message -> message.getClassNames().contains("tt-eh-task-allocation-message")))
				.allSatisfy(message -> assertThat(message.isVisible()).isFalse());
	}

	@Test
	void showsTaskPointMismatchBesidePlainBadgeAndUpdatesLive() {
		final LoeRequirement openRequirement = new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 3, false, REQUIREMENT.sortOrder());
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(
				repositoryWithFixedTaskBudget(openRequirement, 5));
		editor.setExam(EXAM);
		final LoeAllocationMessage message = components(editor, LoeAllocationMessage.class).stream()
				.filter(component -> component.getClassNames().contains("tt-eh-task-allocation-message"))
				.findFirst().orElseThrow();
		final Details taskDetails = components(editor, Details.class).getLast();
		final LoePointBadge badge = components(taskDetails.getSummary(), LoePointBadge.class).getFirst();
		final Component summary = badge.getParent().orElseThrow();
		final List<Component> summaryChildren = summary.getChildren().toList();

		assertThat(message.getClassNames()).contains("tt-eh-allocation-message");
		assertThat(message.getParent().orElseThrow()).isSameAs(summary);
		assertThat(summaryChildren.indexOf(message)).isEqualTo(summaryChildren.indexOf(badge) - 1);
		assertThat(message.isVisible()).isTrue();
		assertThat(message.getText()).isEqualTo("Ordne Anforderungen weitere 2 Punkte zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("warning");
		assertThat(badge.getText()).isEqualTo("3");
		assertThat(components(editor, StatusTray.class).getFirst().getItems())
				.anySatisfy(result -> assertThat(result.message()).isEqualTo("Aufgabe 1: " + message.getText()));

		setRequirementMaxPoints(editor, 4);

		assertThat(message.getText()).isEqualTo("Ordne Anforderungen einen weiteren Punkt zu.");
		assertThat(components(editor, StatusTray.class).getFirst().getItems())
				.anySatisfy(result -> assertThat(result.message())
						.isEqualTo("Aufgabe 1: Ordne Anforderungen einen weiteren Punkt zu."));
		assertThat(badge.getText()).isEqualTo("4");

		setRequirementMaxPoints(editor, 5);

		assertThat(message.isVisible()).isFalse();
		assertThat(message.getText()).isEmpty();
		assertThat(message.getElement().getAttribute("data-severity")).isNull();
		assertThat(badge.getText()).isEqualTo("5");

		setRequirementMaxPoints(editor, 7);

		assertThat(message.isVisible()).isTrue();
		assertThat(message.getText()).isEqualTo("Ordne Anforderungen 2 Punkte weniger zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("error");
		assertThat(badge.getText()).isEqualTo("7");
		assertThat(saveButtons(editor)).allSatisfy(button -> assertThat(button.isEnabled()).isFalse());
		assertThat(components(editor, StatusTray.class).getFirst().getItems())
				.anySatisfy(result -> assertThat(result.message()).isEqualTo("Aufgabe 1: " + message.getText()));

		bonusButtons(editor).getFirst().click();

		assertThat(message.getText()).isEqualTo("Ordne Anforderungen weitere 5 Punkte zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("warning");
		assertThat(badge.getText()).isEqualTo("0");
		assertThat(saveButtons(editor)).allSatisfy(button -> assertThat(button.isEnabled()).isTrue());
		assertThat(components(editor, Details.class).getLast()).isSameAs(taskDetails);
		assertThat(taskDetails.isOpened()).isTrue();
		assertThat(components(taskDetails.getSummary(), LoePointBadge.class).getFirst()).isSameAs(badge);
	}

	@Test
	void updatesTaskAllocationMessagesOnlyForTheirOwnTask() {
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repositoryWithTwoPartHierarchy());
		editor.setExam(EXAM);
		final List<LoeAllocationMessage> messages = components(editor, LoeAllocationMessage.class).stream()
				.filter(message -> message.getClassNames().contains("tt-eh-task-allocation-message")).toList();

		assertThat(messages).hasSize(2).allSatisfy(message -> assertThat(message.isVisible()).isFalse());

		setRequirementMaxPoints(editor, 4);

		assertThat(messages.getFirst().isVisible()).isTrue();
		assertThat(messages.getFirst().getText()).isEqualTo("Ordne Anforderungen einen weiteren Punkt zu.");
		assertThat(messages.getLast().isVisible()).isFalse();
	}

	@Test
	void pointBadgesUseSharedPlainNumberRenderingWithoutLabelsOrBonusSlots() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		assertThat(components(editor, LoePointBadge.class)).extracting(LoePointBadge::getText).containsOnly("5");
		assertThat(components(editor, LoePointBadge.class)).allSatisfy(badge ->
				assertThat(badge.getClassNames()).contains("tt-loe-points-cell", "tt-loe-aggregate-points"));
		assertThat(components(editor, LoePointBadge.class)).allSatisfy(badge -> {
			assertThat(badge.getContent().getText()).isEqualTo("5");
			assertThat(badge.getContent().getChildren()).isEmpty();
		});
	}

	@Test
	void showsCriterionPointMismatchBesideRequirementMaximumAndUpdatesLive() {
		final String underAllocatedDescription = "[Erster Aspekt](crit:1?points=0,5)";
		final LoeRequirement underAllocatedRequirement = new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				underAllocatedDescription, 2, REQUIREMENT.bonus(), REQUIREMENT.sortOrder());
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(
				repositoryWithHierarchy(underAllocatedRequirement));
		editor.setExam(EXAM);
		final Span message = components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-requirement-allocation-message")).findFirst()
				.orElseThrow();

		assertThat(message.isVisible()).isTrue();
		assertThat(message.getText()).isEqualTo("Ordne weitere 1,5 Kriterienpunkte zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("warning");
		assertThat(components(editor, StatusTray.class).getFirst().getItems())
				.anySatisfy(result -> assertThat(result.message())
						.isEqualTo("Aufgabe 1 · Anforderung 1: " + message.getText()));

		final MarkdownEditor description = markdownEditor(editor, underAllocatedDescription);
		assertThat(description).isInstanceOf(CriterionMarkdownEditor.class);
		assertThat(description.getExtensionIds()).containsExactly(CriterionMarkdownEditor.EXTENSION_ID);
		description.setValue("[Erster Aspekt](crit:1?points=0,5) [Zweiter Aspekt](crit:2?points=1,5)");

		assertThat(message.isVisible()).isFalse();

		setRequirementMaxPoints(editor, 1);

		assertThat(message.isVisible()).isTrue();
		assertThat(message.getText()).isEqualTo("Ordne einen Kriterienpunkt weniger zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("error");
		assertThat(components(editor, StatusTray.class).getFirst().getItems())
				.anySatisfy(result -> assertThat(result.message())
						.isEqualTo("Aufgabe 1 · Anforderung 1: " + message.getText()));

		description.setValue("[Erster Aspekt](crit:1?points=0,5) [Zweiter Aspekt](crit:2?points=1)");

		assertThat(message.getText()).isEqualTo("Ordne 0,5 Kriterienpunkte weniger zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("error");

		setRequirementMaxPoints(editor, 3);

		assertThat(message.isVisible()).isTrue();
		assertThat(message.getText()).isEqualTo("Ordne weitere 1,5 Kriterienpunkte zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("warning");

		description.setValue("[Erster Aspekt](crit:1?points=?)");

		assertThat(message.getText()).isEqualTo("Kriterium „Erster Aspekt“: Ordne eine gültige Punktzahl zu.");
		assertThat(message.getElement().getAttribute("data-severity")).isEqualTo("error");
		assertThat(components(editor, StatusTray.class).getFirst().getItems())
				.anySatisfy(result -> assertThat(result.message())
						.isEqualTo("Aufgabe 1 · Anforderung 1: " + message.getText()));

		description.setValue("Keine Kriterien");
		assertThat(message.isVisible()).isFalse();
	}

	@Test
	void placesSharedWholePointStepperWithoutVisibleLabelInRequirementSummary() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		final LoeWholePointStepper maxPoints = components(editor, LoeWholePointStepper.class).getFirst();
		final Button bonus = bonusButtons(editor).getFirst();
		final Component parent = maxPoints.getParent().orElseThrow();
		final Component bonusParent = bonus.getParent().orElseThrow();
		final Component headerControls = parent.getParent().orElseThrow();

		assertThat(components(maxPoints, Span.class)).singleElement()
				.satisfies(value -> {
					assertThat(value.getElement().getAttribute("aria-label")).isEqualTo("Max. Punkte");
					assertThat(value.getText()).isEqualTo("5");
				});
		assertThat(maxPoints.getClassNames()).contains("tt-loe-point-stepper", "tt-loe-whole-point-stepper");
		assertThat(buttonsByAriaLabel(maxPoints, "Max. Punkte verringern")).hasSize(1);
		assertThat(buttonsByAriaLabel(maxPoints, "Max. Punkte erhöhen")).hasSize(1);
		assertThat(maxPoints.getWidth()).isNull();
		assertThat(parent).isInstanceOf(HorizontalLayout.class);
		assertThat(parent.getClassNames()).contains("tt-eh-requirement-points-control");
		final List<Component> pointControlChildren = parent.getChildren().toList();
		assertThat(pointControlChildren).hasSize(2);
		assertThat(pointControlChildren.get(0)).isInstanceOf(Span.class)
				.satisfies(component -> assertThat(component.getClassNames())
						.contains("tt-eh-requirement-allocation-message"));
		assertThat(pointControlChildren.get(0).isVisible()).isFalse();
		assertThat(pointControlChildren.get(1)).isSameAs(maxPoints);
		assertThat(bonus.getElement().getAttribute("aria-label")).isEqualTo("Sternchen-Aufgabe");
		assertThat(bonus.getElement().getAttribute("aria-pressed")).isEqualTo("false");
		assertThat(bonus.getIcon().getElement().getAttribute("icon")).isEqualTo("vaadin:star");
		assertThat(bonus.getClassNames()).contains("tt-eh-bonus-toggle");
		assertThat(bonus.getClassNames()).doesNotContain("tt-eh-bonus-toggle-active");
		assertThat(bonusParent.getClassNames()).contains("tt-eh-requirement-bonus-control");
		assertThat(bonusParent.getChildren().filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText))
				.isEmpty();
		assertThat(headerControls.getClassNames()).contains("tt-eh-requirement-header-controls");
		final Component summary = headerControls.getParent().orElseThrow();
		assertThat(summary.getClassNames()).contains("tt-eh-summary");
		assertThat(bonusParent.getParent().orElseThrow()).isSameAs(summary);
		final List<Component> summaryChildren = summary.getChildren().toList();
		assertThat(summaryChildren.get(0)).isInstanceOf(Span.class);
		assertThat(((Span) summaryChildren.get(0)).getText()).isEqualTo("Anforderung 1");
		assertThat(summaryChildren.get(1)).isSameAs(bonusParent);
		assertThat(summaryChildren.get(2)).isSameAs(headerControls);

		bonus.click();

		assertThat(bonus.getElement().getAttribute("aria-pressed")).isEqualTo("true");
		assertThat(bonus.getClassNames()).contains("tt-eh-bonus-toggle-active");
	}

	@Test
	void showsPartPercentageRelativeToExamTotal() {
		final LevelOfExpectationsRepository repository = repositoryWithTwoPartHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("25 %", "75 %");
		assertThat(components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-percentage-number")).map(Span::getText))
						.contains("25", "75");
	}

	@Test
	void numbersRequirementsInsteadOfUsingDescriptionInSummary() {
		final LevelOfExpectationsRepository repository = repositoryWithTwoRequirements();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		final List<String> spanTexts = components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-summary-type")).map(Span::getText).toList();
		assertThat(spanTexts).contains("Anforderung 1", "Anforderung 2");
		assertThat(spanTexts).doesNotContain("1", "2");
		assertThat(spanTexts).doesNotContain(REQUIREMENT.descriptionMarkdown(),
				FOLLOWING_REQUIREMENT.descriptionMarkdown());
	}

	@Test
	void toolbarCollapseButtonTogglesWholeTree() {
		final LevelOfExpectationsRepository repository = repositoryWithTwoPartHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Button collapseButton = globalToggleButtons(editor).getFirst();

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-down");

		collapseButton.click();

		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsOnly(false);
		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-right");

		collapseButton.click();

		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsOnly(true);
		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-down");
	}

	@Test
	void showsLevelOfExpectationsLightOnlyWhenLevelOfExpectationsExists() {
		final LevelOfExpectationsEditor editorWithHierarchy = new LevelOfExpectationsEditor(repositoryWithHierarchy());
		editorWithHierarchy.setExam(EXAM);
		assertThat(components(editorWithHierarchy, Span.class).stream().map(Span::getText))
				.anyMatch(text -> text.contains("Licht am Ende des Erwartungshorizonts"));

		final LevelOfExpectationsEditor emptyEditor = new LevelOfExpectationsEditor(emptyRepository());
		emptyEditor.setExam(EXAM);
		assertThat(components(emptyEditor, Span.class).stream().map(Span::getText))
				.doesNotContain("Licht am Ende des Erwartungshorizonts");
	}

	@Test
	void sectionHeadersUseExplicitSelfAndSubtreeToggleButtons() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		final List<Button> sectionToggles = details.stream()
				.map(detail -> buttonsByDataAction(detail.getSummary(), "toggle-section").getFirst()).toList();
		final List<Button> subtreeToggles = details.stream()
				.flatMap(detail -> buttonsByDataAction(detail.getSummary(), "toggle-subtree").stream()).toList();

		assertThat(details).hasSize(3);
		assertThat(sectionToggles).hasSize(3);
		assertThat(subtreeToggles).hasSize(2);
		assertThat(globalToggleButtons(editor)).hasSize(1);
		assertThat(details).allSatisfy(detail -> assertThat(detail.getElement()
				.getAttribute("data-summary-toggle")).isEqualTo("explicit"));
		for (int index = 0; index < details.size(); index++) {
			assertThat(sectionToggles.get(index).getParent().orElseThrow()).isSameAs(details.get(index).getSummary());
			assertThat(details.get(index).getSummary().getChildren().findFirst().orElseThrow())
					.isSameAs(sectionToggles.get(index));
		}
		assertThat(subtreeToggles.get(0).getParent().orElseThrow()).isSameAs(details.get(0).getSummary());
		assertThat(subtreeToggles.get(1).getParent().orElseThrow()).isSameAs(details.get(1).getSummary());
		assertThat(sectionToggles).extracting(button -> button.getElement().getAttribute("aria-label"))
				.containsExactly("Klausurteil „Klausurteil A“ einklappen",
						"Leistungskategorie „Inhalt“ einklappen", "Aufgabe „Teilaufgabe 1“ einklappen");
		assertThat(sectionToggles).allSatisfy(button -> {
			assertThat(button.getElement().getAttribute("aria-expanded")).isEqualTo("true");
			assertThat(collapseIcon(button)).isEqualTo("vaadin:angle-down");
		});

		sectionToggles.getFirst().click();
		assertThat(details).extracting(Details::isOpened).containsExactly(false, true, true);
		assertThat(sectionToggles.getFirst().getElement().getAttribute("aria-label"))
				.isEqualTo("Klausurteil „Klausurteil A“ ausklappen");
		assertThat(sectionToggles.getFirst().getElement().getAttribute("aria-expanded")).isEqualTo("false");
		assertThat(collapseIcon(sectionToggles.getFirst())).isEqualTo("vaadin:angle-right");

		sectionToggles.getFirst().click();
		subtreeToggles.getFirst().click();
		assertThat(details).extracting(Details::isOpened).containsOnly(false);

		subtreeToggles.getFirst().click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);
	}

	@Test
	void preservesExistingSectionCssHooks() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);

		final List<Details> details = components(editor, Details.class);

		assertThat(details).hasSize(3);
		assertThat(details.get(0).getClassNames()).contains("tt-eh-details", "tt-eh-part");
		assertThat(details.get(1).getClassNames()).contains("tt-eh-details", "tt-eh-category");
		assertThat(details.get(2).getClassNames()).contains("tt-eh-details", "tt-eh-task");
		assertThat(components(editor, VerticalLayout.class).stream()
				.filter(layout -> layout.getClassNames().contains("tt-eh-section-content"))).hasSize(3);
		assertThat(components(editor, VerticalLayout.class).stream()
				.filter(layout -> layout.getClassNames().contains("tt-eh-requirement"))).hasSize(1);
		assertThat(components(editor, LoeBadge.class)).hasSize(5);
	}

	@Test
	void groupToggleOffersExpansionUntilWholeTreeIsExpanded() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Button collapseButton = globalToggleButtons(editor).getFirst();
		final List<Details> details = components(editor, Details.class);

		details.forEach(detail -> detail.setOpened(false));

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-right");

		details.getFirst().setOpened(true);

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-right");
		assertThat(collapseButton.getElement().getAttribute("aria-label")).isEqualTo("Alle Abschnitte ausklappen");

		collapseButton.click();

		assertThat(details).extracting(Details::isOpened).containsOnly(true);
		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-down");
		assertThat(collapseButton.getElement().getAttribute("aria-label")).isEqualTo("Alle Abschnitte einklappen");
	}

	@Test
	void subtreeToggleOffersExpansionUntilWholeSubtreeIsExpanded() {
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repositoryWithHierarchy());
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		final Button partCollapseButton = buttonsByDataAction(details.getFirst().getSummary(), "toggle-subtree")
				.getFirst();

		details.get(1).setOpened(false);

		assertThat(collapseIcon(partCollapseButton)).isEqualTo("vaadin:angle-double-right");
		assertThat(partCollapseButton.getElement().getAttribute("aria-label"))
				.isEqualTo("Klausurteil „Klausurteil A“ samt Unterpunkten ausklappen");
		partCollapseButton.click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);
		assertThat(collapseIcon(partCollapseButton)).isEqualTo("vaadin:angle-double-down");
		assertThat(partCollapseButton.getElement().getAttribute("aria-label"))
				.isEqualTo("Klausurteil „Klausurteil A“ samt Unterpunkten einklappen");

		details.get(2).setOpened(false);

		assertThat(collapseIcon(partCollapseButton)).isEqualTo("vaadin:angle-double-right");
	}

	private static LevelOfExpectationsRepository repositoryWithHierarchy() {
		return repositoryWithHierarchy(REQUIREMENT);
	}

	private static LevelOfExpectationsRepository repositoryWithHierarchy(final LoeRequirement requirement) {
		return repositoryWithFixedTaskBudget(requirement,
				requirement.bonus() ? TASK.maxPoints() : requirement.maxPoints());
	}

	private static LevelOfExpectationsRepository repositoryWithFixedTaskBudget(final LoeRequirement requirement,
			final int taskMaxPoints) {
		final LevelOfExpectationsRepository repository = RepositoryDesignTestSupport.mockRepository();
		final LoeTask task = new LoeTask(TASK.id(), TASK.categoryId(), TASK.title(), TASK.descriptionMarkdown(),
				taskMaxPoints, TASK.sortOrder());
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(task));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(requirement));
		return repository;
	}

	private static LevelOfExpectationsRepository repositoryWithTwoPartHierarchy() {
		final LevelOfExpectationsRepository repository = RepositoryDesignTestSupport.mockRepository();
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART, SECOND_PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY, SECOND_CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK, SECOND_TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, SECOND_REQUIREMENT));
		return repository;
	}

	private static LevelOfExpectationsRepository repositoryWithTwoRequirements() {
		final LevelOfExpectationsRepository repository = RepositoryDesignTestSupport.mockRepository();
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, FOLLOWING_REQUIREMENT));
		return repository;
	}

	private static LevelOfExpectationsRepository repositoryWithRegularAndBonusRequirement() {
		final LevelOfExpectationsRepository repository = RepositoryDesignTestSupport.mockRepository();
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, BONUS_REQUIREMENT));
		return repository;
	}

	private static LevelOfExpectationsRepository emptyRepository() {
		final LevelOfExpectationsRepository repository = RepositoryDesignTestSupport.mockRepository();
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of());
		return repository;
	}

	private static LevelOfExpectationsEditor editorWithGradingScale(
			final LevelOfExpectationsRepository repository, final int maxPoints) {
		final GradingScaleRepository gradingScaleRepository = mock(GradingScaleRepository.class);
		when(gradingScaleRepository.findById(GRADING_SCALE_ID))
				.thenReturn(Optional.of(new GradingScale(GRADING_SCALE_ID, "Test", maxPoints, Lifecycle.ACTIVE)));
		return new LevelOfExpectationsEditor(repository, gradingScaleRepository);
	}

	private static LoeRequirement requirementWithCriteria(final LoeRequirement requirement, final int maxPoints,
			final int criterionCount) {
		final String description = IntStream.rangeClosed(1, criterionCount)
				.mapToObj(index -> "[Kriterium " + index + "](crit:" + index + ")").collect(java.util.stream.Collectors.joining(" "));
		return new LoeRequirement(requirement.id(), requirement.taskId(), description, maxPoints, requirement.bonus(),
				requirement.sortOrder());
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private static List<String> badgeTexts(final Component root) {
		return components(root, LoeBadge.class).stream().map(LoeBadge::getText).toList();
	}

	private static HorizontalLayout toolbar(final Component root) {
		return components(root, HorizontalLayout.class).stream()
				.filter(layout -> layout.getClassNames().contains("tt-designer-toolbar")).findFirst().orElseThrow();
	}

	private static List<Button> saveButtons(final Component root) {
		return buttonsByAriaLabel(root, "Speichern");
	}

	private static void setRequirementMaxPoints(final Component root, final int value) {
		final LoeWholePointStepper points = components(root, LoeWholePointStepper.class).getFirst();
		final String action = points.getValue() < value ? "Max. Punkte erhöhen" : "Max. Punkte verringern";
		final Button button = buttonsByAriaLabel(points, action).getFirst();
		final int changes = Math.abs(points.getValue() - value);
		for (int index = 0; index < changes; index++) {
			assertThat(button.isEnabled()).isTrue();
			button.click();
		}
		assertThat(points.getValue()).isEqualTo(value);
	}

	private static List<Button> discardButtons(final Component root) {
		return buttonsByAriaLabel(root, "Verwerfen...");
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static List<Button> buttonsByAriaLabel(final Component root, final String ariaLabel) {
		return components(root, Button.class).stream()
				.filter(button -> ariaLabel.equals(button.getElement().getAttribute("aria-label"))).toList();
	}

	private static List<String> anchorKeys(final Component root) {
		return components(root, Component.class).stream()
				.map(component -> component.getElement().getAttribute("data-tt-anchor")).filter(Objects::nonNull)
				.toList();
	}

	private static List<String> breadcrumbSegments(final Component root) {
		return components(root, Component.class).stream()
				.map(component -> component.getElement().getAttribute("data-tt-breadcrumb-segment"))
				.filter(Objects::nonNull).distinct().toList();
	}

	private static Span breadcrumb(final Component root) {
		return components(root, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-designer-breadcrumb")).findFirst().orElseThrow();
	}

	private static List<Button> globalToggleButtons(final Component root) {
		return buttonsByDataAction(root, "toggle-all");
	}

	private static List<Button> buttonsByDataAction(final Component root, final String action) {
		return components(root, Button.class).stream()
				.filter(button -> action.equals(button.getElement().getAttribute("data-action"))).toList();
	}

	private static List<Button> bonusButtons(final Component root) {
		return components(root, Button.class).stream()
				.filter(button -> "toggle-bonus".equals(button.getElement().getAttribute("data-action"))).toList();
	}

	private static MarkdownEditor markdownEditor(final Component root, final String value) {
		return components(root, MarkdownEditor.class).stream().filter(editor -> value.equals(editor.getValue()))
				.findFirst().orElseThrow();
	}

	private static String collapseIcon(final Button button) {
		return button.getIcon().getElement().getAttribute("icon");
	}
}
