package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownViewer;

class ExamTasksEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "Einordnung", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Aufgabe 1", "Analysiere den Text.", 5, 0);

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
		assertThat(badgeTexts(editor)).contains("Gesamt: 8 (+0)", "Summe: 8 (+0)")
				.noneMatch(text -> text.startsWith("Max.:"));
		saveButton(editor).click();

		verify(repository).saveTask(new LoeTask(TASK.id(), TASK.categoryId(), "Aufgabe 1a",
				"Analysiere **Material 1**.", 8, TASK.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("Gesamt: 8 (+0)", "Summe: 8 (+0)")
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
	void distributionBadgeKeepsThreeDigitAxesAndBonusAxis() {
		final LoePointDistributionBadge badge = new LoePointDistributionBadge(100, () -> new LoePoints(100, 12));

		assertThat(badge.getText()).isEqualTo("Verteilt: 100 / 100 (+12)");
		assertThat(components(badge, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-loe-distribution-regular")))
				.extracting(Span::getText).containsExactly("100", "100");
		assertThat(components(badge, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-loe-distribution-bonus")))
				.extracting(Span::getText).containsExactly("12");
	}

	@Test
	void locksEveryTaskFieldAndStructuralActionAfterFirstResult() {
		final ExamTasksEditor editor = new ExamTasksEditor(repository(true));

		editor.setExam(EXAM);

		assertThat(components(editor, TextField.class)).allMatch(TextField::isReadOnly);
		assertThat(components(editor, MarkdownEditor.class)).isEmpty();
		assertThat(components(editor, MarkdownViewer.class)).extracting(MarkdownViewer::getContent)
				.containsExactly(CATEGORY.descriptionMarkdown(), TASK.descriptionMarkdown());
		final LoeWholePointStepper points = components(editor, LoeWholePointStepper.class).getFirst();
		assertThat(buttonByAriaLabel(points, "Max. Punkte verringern").isEnabled()).isFalse();
		assertThat(buttonByAriaLabel(points, "Max. Punkte erhöhen").isEnabled()).isFalse();
		assertThat(button(editor, "Klausurteil hinzufügen").isEnabled()).isFalse();
		assertThat(button(editor, "Leistungskategorie hinzufügen").isEnabled()).isFalse();
		assertThat(button(editor, "Teilaufgabe hinzufügen").isEnabled()).isFalse();
		assertThat(buttonsByAriaLabel(editor, "Löschen")).extracting(Button::isEnabled).containsOnly(false);
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

	private static LevelOfExpectationsRepository repository(final boolean locked) {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.hasResultsForExam(EXAM.id())).thenReturn(locked);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
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
		return button(root, "Speichern");
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

