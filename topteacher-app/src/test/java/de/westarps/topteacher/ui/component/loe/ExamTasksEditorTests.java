package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.component.ActionToolbar;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownViewer;

class ExamTasksEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "Einordnung", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Aufgabe 1", "Analysiere den Text.", 5, 0);
	private UI ui;

	@BeforeEach
	void createUi() {
		ui = new UI();
		UI.setCurrent(ui);
	}

	@AfterEach
	void clearUi() {
		UI.setCurrent(null);
		ui = null;
	}

	@Test
	void taskSheetMenuOffersAnonymousAndPersonalizedAndChecksUnsavedWork() {
		final var editor = new ExamTasksEditor(repository(false));
		editor.configureTaskSheetExport(id -> List.of(new de.westarps.topteacher.model.Pupil(42, "Mia", "Weber", de.westarps.topteacher.model.Lifecycle.ACTIVE)), () -> true);
		editor.setExam(EXAM);
		final var menu = components(editor, MenuBar.class).getFirst();
		final var options = menu.getItems().getFirst().getSubMenu().getItems();
		assertThat(options).extracting(MenuItem::getText).containsExactly("Anonym", "Personalisiert...");
		ComponentUtil.fireEvent(options.get(1), new ClickEvent<>(options.get(1)));
		final var dialog = components(ui, Dialog.class).stream().filter(Dialog::isOpened).findFirst().orElseThrow();
		final var all = components(dialog, Checkbox.class).getFirst();
		final var pupil = components(dialog, ComboBox.class).getFirst();
		assertThat(all.getValue()).isTrue();
		assertThat(pupil.isEnabled()).isFalse();
		all.setValue(false);
		assertThat(pupil.isEnabled()).isTrue();
		dialog.close();
		components(editor, MarkdownEditor.class).getFirst().setValue("Unsaved tasks");
		ComponentUtil.fireEvent(options.get(1), new ClickEvent<>(options.get(1)));
		assertThat(components(ui, Dialog.class)).noneMatch(Dialog::isOpened);
	}

	@Test
	void categoryActionsPrecedeDescriptionAndEyeToggleSavesWithoutRebuildingOrHidingEditors() {
		final LevelOfExpectationsRepository repository = repository(false);
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final LoeCategorySection category = components(editor, LoeCategorySection.class).getFirst();
		final MarkdownEditor description = markdownEditor(category, CATEGORY.descriptionMarkdown());
		final MarkdownEditor taskSource = markdownEditor(category, TASK.descriptionMarkdown());
		final Component body = description.getParent().orElseThrow().getParent().orElseThrow();
		final List<Component> blocks = body.getChildren().toList();
		final Button eye = buttonsByDataAction(category, "toggle-print-tasks").getFirst();
		assertThat(components(blocks.getFirst(), Button.class)).contains(eye);
		assertThat(components(blocks.get(1), MarkdownEditor.class)).contains(description);
		assertThat(eye.getIcon().getElement().getAttribute("icon")).isEqualTo("vaadin:eye");
		assertThat(eye.getThemeNames()).contains("icon");
		clearInvocations(repository);

		eye.click();

		assertThat(eye.getIcon().getElement().getAttribute("icon")).isEqualTo("vaadin:eye-slash");
		assertThat(eye.getThemeNames()).contains("icon");
		assertThat(eye.getAriaLabel()).contains("Aufgaben im Aufgabenblatt anzeigen");
		assertThat(eye.getElement().getAttribute("aria-pressed")).isEqualTo("false");
		assertThat(editor.hasUnsavedChanges()).isTrue();
		assertThat(saveButton(editor).isEnabled()).isTrue();
		assertThat(taskSource.isVisible()).isTrue();
		assertThat(badgeTexts(editor)).containsOnly("5", "100 %");
		verify(repository, never()).saveCategory(org.mockito.ArgumentMatchers.any());
		saveButton(editor).click();

		verify(repository).saveCategory(new LoeCategory(CATEGORY.id(), CATEGORY.partId(), CATEGORY.title(),
				CATEGORY.descriptionMarkdown(), CATEGORY.sortOrder(), false));
		verify(repository, never()).findPartsByExamId(anyInt());
		assertThat(components(editor, LoeCategorySection.class).getFirst()).isSameAs(category);
		assertThat(markdownEditor(editor, TASK.descriptionMarkdown())).isSameAs(taskSource);
		assertThat(editor.hasUnsavedChanges()).isFalse();
		assertThat(saveButton(editor).isEnabled()).isFalse();
	}

	@Test
	void togglingTwiceRestoresCleanStateAndDiscardRestoresTheStoredEyeSetting() {
		final ExamTasksEditor editor = new ExamTasksEditor(repository(false));
		editor.setExam(EXAM);
		final Button eye = buttonsByDataAction(editor, "toggle-print-tasks").getFirst();
		eye.click();
		eye.click();
		assertThat(editor.hasUnsavedChanges()).isFalse();
		assertThat(eye.getAriaLabel()).contains("Aufgaben im Aufgabenblatt ausblenden");
		assertThat(eye.getThemeNames()).contains("icon");
		eye.click();
		buttonByAriaLabel(editor, "Verwerfen...").click();
		final ConfirmDialog confirmation = components(editor, ConfirmDialog.class).getFirst();
		ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));
		assertThat(editor.hasUnsavedChanges()).isFalse();
		assertThat(buttonsByDataAction(editor, "toggle-print-tasks").getFirst().getIcon()
				.getElement().getAttribute("icon")).isEqualTo("vaadin:eye");
	}

	@Test
	void editingCategoryTextPreservesAStoredHiddenTasksSetting() {
		final LevelOfExpectationsRepository repository = repository(false);
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(new LoeCategory(CATEGORY.id(),
				CATEGORY.partId(), CATEGORY.title(), CATEGORY.descriptionMarkdown(), CATEGORY.sortOrder(), false)));
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		assertThat(buttonsByDataAction(editor, "toggle-print-tasks").getFirst().getIcon()
				.getElement().getAttribute("icon")).isEqualTo("vaadin:eye-slash");
		titleField(editor, CATEGORY.title()).setValue("Sprache");
		saveButton(editor).click();
		verify(repository).saveCategory(new LoeCategory(CATEGORY.id(), CATEGORY.partId(), "Sprache",
				CATEGORY.descriptionMarkdown(), CATEGORY.sortOrder(), false));
	}

	@Test
	void failedCategorySaveRetainsThePendingEyeSetting() {
		final LevelOfExpectationsRepository repository = repository(false);
		doThrow(new IllegalStateException("Klausur ist gesperrt.")).when(repository)
				.saveCategory(org.mockito.ArgumentMatchers.any());
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final Button eye = buttonsByDataAction(editor, "toggle-print-tasks").getFirst();
		eye.click();
		saveButton(editor).click();
		assertThat(editor.hasUnsavedChanges()).isTrue();
		assertThat(eye.getElement().getAttribute("aria-pressed")).isEqualTo("false");
		assertThat(saveButton(editor).isEnabled()).isTrue();
	}

	@Test
	void staleSavePreservesDirtyTaskSourceAndFooterWithoutInvokingAnyPersistence() {
		final LevelOfExpectationsRepository repository = repository(false);
		when(repository.designRevision(EXAM.id())).thenReturn(4L);
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final MarkdownEditor source = markdownEditor(editor, TASK.descriptionMarkdown());
		final MarkdownEditor footer = components(footerBlock(editor), MarkdownEditor.class).getFirst();
		source.setValue("Unsaved task source");
		footer.setValue("Unsaved footer");
		final Button eye = buttonsByDataAction(editor, "toggle-print-tasks").getFirst();
		eye.click();
		clearInvocations(repository);
		RepositoryDesignTestSupport.rejectDesignWrites(repository, 4, 5);

		saveButton(editor).click();

		assertThat(source.getValue()).isEqualTo("Unsaved task source");
		assertThat(footer.getValue()).isEqualTo("Unsaved footer");
		assertThat(eye.getElement().getAttribute("aria-pressed")).isEqualTo("false");
		assertThat(editor.hasUnsavedChanges()).isTrue();
		assertThat(saveButton(editor).isEnabled()).isTrue();
		verify(repository, never()).saveTask(org.mockito.ArgumentMatchers.any());
		verify(repository, never()).saveCategory(org.mockito.ArgumentMatchers.any());
		verify(repository, never()).saveExamFooterMarkdown(anyInt(), org.mockito.ArgumentMatchers.anyString());
		verify(repository, never()).findPartsByExamId(anyInt());
	}

	@Test
	void staleStructureActionsDoNotAddMoveOrDeleteTasks() {
		final LevelOfExpectationsRepository repository = repository(false);
		when(repository.designRevision(EXAM.id())).thenReturn(4L);
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK,
				new LoeTask(4, CATEGORY.id(), "Second", "Source", 5, 1)));
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		clearInvocations(repository);
		RepositoryDesignTestSupport.rejectDesignWrites(repository, 4, 5);

		button(editor, "Klausurteil hinzufügen").click();
		final Details firstTask = components(editor, Details.class).get(2);
		buttonByAriaLabel(firstTask, "Nach unten").click();
		buttonByAriaLabel(firstTask, "Löschen...").click();
		final ConfirmDialog confirmation = components(ui, ConfirmDialog.class).stream()
				.filter(ConfirmDialog::isOpened).findFirst().orElseThrow();
		ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

		verify(repository, never()).savePart(org.mockito.ArgumentMatchers.any());
		verify(repository, never()).moveTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(), anyInt());
		verify(repository, never()).deleteTask(anyInt());
		verify(repository, never()).findPartsByExamId(anyInt());
		assertThat(components(editor, Details.class)).hasSize(4);
	}

	@Test
	void placesLabeledAddActionFirstAndConfirmsDiscardingTaskChanges() {
		final LevelOfExpectationsRepository repository = repository(false);
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final HorizontalLayout toolbar = components(editor, HorizontalLayout.class).stream()
				.filter(layout -> layout.getClassNames().contains("tt-designer-toolbar")).findFirst().orElseThrow();
		assertThat(toolbar.getChildren().toList().getFirst()).isSameAs(button(editor, "Klausurteil hinzufügen"));
		assertThat(saveButton(editor).getText()).isEqualTo("Speichern");
		final Button discard = buttonByAriaLabel(editor, "Verwerfen...");
		assertThat(discard.getText()).isEqualTo("Verwerfen...");
		assertThat(discard.getTooltip().getText()).isEqualTo("Verwerfen...");
		final TextField title = titleField(editor, TASK.title());
		title.setValue("Geänderte Aufgabe");

		discard.click();

		final ConfirmDialog confirmation = components(editor, ConfirmDialog.class).getFirst();
		assertThat(confirmation.isOpened()).isTrue();
		assertThat(title.getValue()).isEqualTo("Geänderte Aufgabe");
		confirmation.close();
		assertThat(editor.hasUnsavedChanges()).isTrue();
		discard.click();
		ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

		assertThat(confirmation.isOpened()).isFalse();
		assertThat(editor.hasUnsavedChanges()).isFalse();
		assertThat(titleField(editor, TASK.title()).getValue()).isEqualTo(TASK.title());
		verify(repository, never()).saveTask(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void placesTaskActionsBeforeMarkdownInEditableAndLockedMode() {
		for (final boolean locked : List.of(false, true)) {
			final ExamTasksEditor editor = new ExamTasksEditor(repository(locked));
			editor.setExam(EXAM);
			final Details task = components(editor, Details.class).getLast();
			final VerticalLayout body = components(task, VerticalLayout.class).stream()
					.filter(layout -> layout.hasClassName("tt-eh-section-content")).findFirst().orElseThrow();
			final List<Component> content = body.getChildren().toList();

			assertThat(content).hasSize(2);
			assertThat(content.getFirst().hasClassName("tt-eh-editor-block")).isTrue();
			assertThat(content.getFirst().getChildren()).singleElement().isInstanceOf(ActionToolbar.class);
			assertThat(content.getLast().hasClassName("tt-markdown-block")).isTrue();
			if (locked) {
				assertThat(components(content.getLast(), MarkdownViewer.class)).hasSize(1);
			} else {
				assertThat(components(content.getLast(), MarkdownEditor.class)).hasSize(1);
			}
		}
	}

	@Test
	void savesTaskContentAndPointsWithoutRebuildingTheTree() {
		final LevelOfExpectationsRepository repository = repository(false);
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		titleField(editor, TASK.title()).setValue("Aufgabe 1a");
		markdownEditor(editor, TASK.descriptionMarkdown()).setValue("Analysiere **Material 1**.");
		final Details taskDetails = components(editor, Details.class).getLast();
		final LoeWholePointStepper points = components(editor, LoeWholePointStepper.class).getFirst();
		final Button increase = buttonByAriaLabel(points, "Max. Punkte erhöhen");
		increase.click();
		increase.click();
		increase.click();
		assertThat(taskDetails.isOpened()).isTrue();
		assertThat(points.getValue()).isEqualTo(8);
		assertThat(badgeTexts(editor)).contains("8")
				.noneMatch(text -> text.startsWith("Max.:"));
		saveButton(editor).click();

		verify(repository).saveTask(new LoeTask(TASK.id(), TASK.categoryId(), "Aufgabe 1a",
				"Analysiere **Material 1**.", 8, TASK.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("8")
				.noneMatch(text -> text.startsWith("Max.:"));
		assertThat(saveButton(editor).isEnabled()).isFalse();
	}

	@Test
	void placesWholePointStepperInTheSharedTaskSummaryPointsColumn() {
		final ExamTasksEditor editor = new ExamTasksEditor(repository(false));
		editor.setExam(EXAM);

		final Details taskDetails = components(editor, Details.class).getLast();
		final LoeWholePointStepper points = components(editor, LoeWholePointStepper.class).getFirst();

		assertThat(points.getParent()).contains(taskDetails.getSummary());
		assertThat(points.getClassNames()).contains("tt-loe-points-cell", "tt-results-points-cell",
				"tt-loe-point-stepper", "tt-loe-whole-point-stepper");
		assertThat(buttonByAriaLabel(points, "Max. Punkte verringern").isEnabled()).isTrue();
		assertThat(buttonByAriaLabel(points, "Max. Punkte erhöhen").isEnabled()).isTrue();
	}

	@Test
	void groupTogglesExpandMixedStateBeforeTheyCollapseEverything() {
		final ExamTasksEditor editor = new ExamTasksEditor(repository(false));
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		final Button taskToggle = buttonsByDataAction(details.getLast().getSummary(), "toggle-section").getFirst();
		final Button partSubtreeToggle = buttonsByDataAction(details.getFirst().getSummary(), "toggle-subtree")
				.getFirst();
		final Button allToggle = buttonsByDataAction(editor, "toggle-all").getFirst();

		taskToggle.click();
		assertThat(allToggle.getElement().getAttribute("aria-label")).isEqualTo("Alle Abschnitte ausklappen");
		assertThat(partSubtreeToggle.getElement().getAttribute("aria-label"))
				.isEqualTo("Klausurteil „Klausurteil A“ samt Unterpunkten ausklappen");

		partSubtreeToggle.click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);
		taskToggle.click();
		allToggle.click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);
	}

	@Test
	void pointBadgeShowsOnlyItsNumberAndRetainsAnAccessibleDescription() {
		final LoePointBadge badge = new LoePointBadge("Zugeordnete Punkte", () -> new LoePoints(100, 12));

		assertThat(badge.getText()).isEqualTo("100");
		assertThat(badge.getContent().getText()).isEqualTo("100");
		assertThat(badge.getElement().getAttribute("aria-label")).isEqualTo("Zugeordnete Punkte: 100 Punkte");
		assertThat(badge.getElement().getAttribute("title")).isEqualTo("Zugeordnete Punkte: 100 Punkte");
		assertThat(badge.getContent().getChildren()).isEmpty();
	}

	@Test
	void locksEveryTaskFieldAndStructuralActionAfterFirstResult() {
		final ExamTasksEditor editor = new ExamTasksEditor(repository(true));

		editor.setExam(EXAM);

		assertThat(components(editor, TextField.class)).allMatch(TextField::isReadOnly);
		assertThat(components(editor, MarkdownEditor.class)).isEmpty();
		assertThat(components(editor, MarkdownViewer.class)).extracting(MarkdownViewer::getContent)
				.containsExactly(CATEGORY.descriptionMarkdown(), TASK.descriptionMarkdown(), "");
		final LoeWholePointStepper points = components(editor, LoeWholePointStepper.class).getFirst();
		assertThat(buttonByAriaLabel(points, "Max. Punkte verringern").isEnabled()).isFalse();
		assertThat(buttonByAriaLabel(points, "Max. Punkte erhöhen").isEnabled()).isFalse();
		assertThat(button(editor, "Klausurteil hinzufügen").isEnabled()).isFalse();
		assertThat(button(editor, "Leistungskategorie hinzufügen").isEnabled()).isFalse();
		assertThat(button(editor, "Teilaufgabe hinzufügen").isEnabled()).isFalse();
		assertThat(buttonsByDataAction(editor, "toggle-print-tasks").getFirst().isEnabled()).isFalse();
		assertThat(buttonsByAriaLabel(editor, "Löschen...")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Nach oben")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Nach unten")).extracting(Button::isEnabled).containsOnly(false);
	}

	@Test
	void placesSharedSelfAndSubtreeTogglesInSectionHeaders() {
		final ExamTasksEditor editor = new ExamTasksEditor(repository(false));
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		final List<Button> sectionToggles = details.stream()
				.map(detail -> buttonsByDataAction(detail.getSummary(), "toggle-section").getFirst()).toList();
		final List<Button> subtreeToggles = details.stream()
				.flatMap(detail -> buttonsByDataAction(detail.getSummary(), "toggle-subtree").stream()).toList();

		assertThat(details).hasSize(3);
		assertThat(sectionToggles).hasSize(3);
		assertThat(subtreeToggles).hasSize(2);
		assertThat(buttonsByDataAction(editor, "toggle-all")).hasSize(1);
		for (int index = 0; index < details.size(); index++) {
			assertThat(sectionToggles.get(index).getParent().orElseThrow()).isSameAs(details.get(index).getSummary());
		}
		assertThat(subtreeToggles.get(0).getParent().orElseThrow()).isSameAs(details.get(0).getSummary());
		assertThat(subtreeToggles.get(1).getParent().orElseThrow()).isSameAs(details.get(1).getSummary());

		subtreeToggles.getFirst().click();
		assertThat(details).extracting(Details::isOpened).containsOnly(false);
		subtreeToggles.getFirst().click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);

		sectionToggles.getLast().click();
		assertThat(details).extracting(Details::isOpened).containsExactly(true, true, false);
	}

	@Test
	void footerIsAFixedBlockBelowTheTreeAndUnaffectedByCollapseAll() {
		final ExamTasksEditor editor = new ExamTasksEditor(repository(false));
		editor.setExam(EXAM);
		final Component footer = footerBlock(editor);
		final Component content = footer.getParent().orElseThrow();

		assertThat(content.hasClassName("tt-designer-content")).isTrue();
		assertThat(content.getChildren().toList().getLast()).isSameAs(footer);
		assertThat(components(footer, Details.class)).isEmpty();
		buttonsByDataAction(editor, "toggle-all").getFirst().click();

		assertThat(components(editor, Details.class)).allMatch(detail -> !detail.isOpened());
		assertThat(footer.isVisible()).isTrue();
	}

	@Test
	void savesOnlyTheChangedFooterWithoutRebuildingTheTreeOrEditor() {
		final LevelOfExpectationsRepository repository = repository(false);
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final Component footer = footerBlock(editor);
		final Details part = components(editor, Details.class).getFirst();
		final MarkdownEditor markdown = components(footer, MarkdownEditor.class).getFirst();
		clearInvocations(repository);

		markdown.setValue("**Viel Erfolg!**");
		assertThat(editor.hasUnsavedChanges()).isTrue();
		assertThat(saveButton(editor).isEnabled()).isTrue();
		assertThat(buttonByAriaLabel(editor, "Verwerfen...").isEnabled()).isTrue();
		assertThat(buttonByAriaLabel(editor, "Neu laden").isEnabled()).isFalse();
		saveButton(editor).click();

		verify(repository).saveExamFooterMarkdown(EXAM.id(), "**Viel Erfolg!**");
		verify(repository, never()).saveTask(org.mockito.ArgumentMatchers.any());
		verify(repository, never()).findExamFooterMarkdown(anyInt());
		assertThat(footerBlock(editor)).isSameAs(footer);
		assertThat(components(editor, Details.class).getFirst()).isSameAs(part);
		assertThat(markdown.getValue()).isEqualTo("**Viel Erfolg!**");
		assertThat(editor.hasUnsavedChanges()).isFalse();
		assertThat(saveButton(editor).isEnabled()).isFalse();
		assertThat(buttonByAriaLabel(editor, "Neu laden").isEnabled()).isTrue();
	}

	@Test
	void restoringTheSavedFooterValueClearsDirtyState() {
		final LevelOfExpectationsRepository repository = repository(false);
		when(repository.findExamFooterMarkdown(EXAM.id())).thenReturn("Viel Erfolg!");
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final MarkdownEditor markdown = components(footerBlock(editor), MarkdownEditor.class).getFirst();

		markdown.setValue("Anderer Text");
		markdown.setValue("Viel Erfolg!");

		assertThat(editor.hasUnsavedChanges()).isFalse();
		assertThat(saveButton(editor).isEnabled()).isFalse();
	}

	@Test
	void discardingFooterChangesRequiresConfirmationAndReloadsStoredContent() {
		final LevelOfExpectationsRepository repository = repository(false);
		when(repository.findExamFooterMarkdown(EXAM.id())).thenReturn("Viel Erfolg!");
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final MarkdownEditor markdown = components(footerBlock(editor), MarkdownEditor.class).getFirst();
		markdown.setValue("Geänderter Abschlusstext");

		buttonByAriaLabel(editor, "Verwerfen...").click();
		final ConfirmDialog confirmation = components(editor, ConfirmDialog.class).getFirst();
		confirmation.close();
		assertThat(markdown.getValue()).isEqualTo("Geänderter Abschlusstext");
		assertThat(editor.hasUnsavedChanges()).isTrue();
		buttonByAriaLabel(editor, "Verwerfen...").click();
		ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

		assertThat(components(footerBlock(editor), MarkdownEditor.class).getFirst().getValue()).isEqualTo("Viel Erfolg!");
		assertThat(editor.hasUnsavedChanges()).isFalse();
		verify(repository, never()).saveExamFooterMarkdown(anyInt(), org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void keepsFooterChangesWhenSwitchingExamsOrChangingTheTree() {
		final LevelOfExpectationsRepository repository = repository(false);
		final LoeTask secondTask = new LoeTask(4, CATEGORY.id(), "Aufgabe 2", "Zweiter Text", 5, 1);
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK, secondTask));
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final Component footer = footerBlock(editor);
		components(footer, MarkdownEditor.class).getFirst().setValue("Noch nicht gespeichert");
		clearInvocations(repository);

		button(editor, "Klausurteil hinzufügen").click();
		button(editor, "Leistungskategorie hinzufügen").click();
		button(editor, "Teilaufgabe hinzufügen").click();
		final Details firstTask = components(editor, Details.class).get(2);
		buttonByAriaLabel(firstTask, "Nach unten").click();
		buttonByAriaLabel(firstTask, "Löschen...").click();
		final ConfirmDialog deleteConfirmation = components(ui, ConfirmDialog.class).stream()
				.filter(ConfirmDialog::isOpened).findFirst().orElseThrow();
		ComponentUtil.fireEvent(deleteConfirmation, new ConfirmDialog.ConfirmEvent(deleteConfirmation, false));
		editor.setExam(new Exam(2, EXAM.courseId(), "Andere Klausur", EXAM.date()));

		verify(repository, never()).savePart(org.mockito.ArgumentMatchers.any());
		verify(repository, never()).saveCategory(org.mockito.ArgumentMatchers.any());
		verify(repository, never()).saveTask(org.mockito.ArgumentMatchers.any());
		verify(repository, never()).moveTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(), anyInt());
		verify(repository, never()).deleteTask(anyInt());
		verify(repository, never()).findPartsByExamId(anyInt());
		assertThat(footerBlock(editor)).isSameAs(footer);
		assertThat(components(footer, MarkdownEditor.class).getFirst().getValue()).isEqualTo("Noch nicht gespeichert");
		assertThat(editor.hasUnsavedChanges()).isTrue();
	}

	@Test
	void failedFooterSaveRetainsPendingChanges() {
		final LevelOfExpectationsRepository repository = repository(false);
		doThrow(new IllegalStateException("Klausur ist gesperrt.")).when(repository)
				.saveExamFooterMarkdown(EXAM.id(), "Noch nicht gespeichert");
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);
		final MarkdownEditor markdown = components(footerBlock(editor), MarkdownEditor.class).getFirst();
		markdown.setValue("Noch nicht gespeichert");

		saveButton(editor).click();

		assertThat(markdown.getValue()).isEqualTo("Noch nicht gespeichert");
		assertThat(editor.hasUnsavedChanges()).isTrue();
		assertThat(saveButton(editor).isEnabled()).isTrue();
	}

	@Test
	void examWithoutPartsStillHasAnEditableFooter() {
		final LevelOfExpectationsRepository repository = repository(false);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of());
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);

		assertThat(components(editor, Details.class)).isEmpty();
		components(footerBlock(editor), MarkdownEditor.class).getFirst().setValue("Viel Erfolg!");
		saveButton(editor).click();

		verify(repository).saveExamFooterMarkdown(EXAM.id(), "Viel Erfolg!");
		editor.setExam(null);
		assertThat(components(editor, MarkdownEditor.class)).isEmpty();
		assertThat(editor.hasUnsavedChanges()).isFalse();
	}

	@Test
	void lockedFooterIsRenderedWithoutEditingControls() {
		final LevelOfExpectationsRepository repository = repository(true);
		when(repository.findExamFooterMarkdown(EXAM.id())).thenReturn("**Viel Erfolg!**");
		final ExamTasksEditor editor = new ExamTasksEditor(repository);
		editor.setExam(EXAM);

		assertThat(components(footerBlock(editor), MarkdownEditor.class)).isEmpty();
		assertThat(components(footerBlock(editor), MarkdownViewer.class)).extracting(MarkdownViewer::getContent)
				.containsExactly("**Viel Erfolg!**");
		assertThat(saveButton(editor).isEnabled()).isFalse();
	}

	private static Component footerBlock(final Component root) {
		return components(root, VerticalLayout.class).stream().filter(component -> component.hasClassName("tt-exam-footer"))
				.findFirst().orElseThrow();
	}

	private static LevelOfExpectationsRepository repository(final boolean locked) {
		final LevelOfExpectationsRepository repository = RepositoryDesignTestSupport.mockRepository();
		when(repository.hasResultsForExam(EXAM.id())).thenReturn(locked);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findExamFooterMarkdown(EXAM.id())).thenReturn("");
		return repository;
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private static TextField titleField(final Component root, final String value) {
		return components(root, TextField.class).stream().filter(field -> value.equals(field.getValue())).findFirst()
				.orElseThrow();
	}

	private static MarkdownEditor markdownEditor(final Component root, final String value) {
		return components(root, MarkdownEditor.class).stream().filter(field -> value.equals(field.getValue())).findFirst()
				.orElseThrow();
	}

	private static Button saveButton(final Component root) {
		return buttonByAriaLabel(root, "Speichern");
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static Button buttonByAriaLabel(final Component root, final String label) {
		return components(root, Button.class).stream()
				.filter(button -> label.equals(button.getElement().getAttribute("aria-label"))).findFirst().orElseThrow();
	}

	private static List<Button> buttonsByAriaLabel(final Component root, final String label) {
		return components(root, Button.class).stream()
				.filter(button -> label.equals(button.getElement().getAttribute("aria-label"))).toList();
	}

	private static List<Button> buttonsByDataAction(final Component root, final String action) {
		return components(root, Button.class).stream()
				.filter(button -> action.equals(button.getElement().getAttribute("data-action"))).toList();
	}

	private static List<String> badgeTexts(final Component root) {
		return components(root, LoeBadge.class).stream().map(LoeBadge::getText).toList();
	}
}

