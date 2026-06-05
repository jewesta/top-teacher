package de.topteacher.ui.component;

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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;

import de.topteacher.backend.repo.ExpectationHorizonRepository;
import de.topteacher.model.EhCategory;
import de.topteacher.model.EhPart;
import de.topteacher.model.EhRequirement;
import de.topteacher.model.EhTask;
import de.topteacher.model.Exam;

class ExpectationHorizonEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final EhPart PART = new EhPart(1, EXAM.id(), "Klausurteil A", 0);
	private static final EhCategory CATEGORY = new EhCategory(2, PART.id(), "Inhalt", "Beschreibung", 0);
	private static final EhTask TASK = new EhTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final EhRequirement REQUIREMENT = new EhRequirement(4, TASK.id(), "Requirement text", 5, false, 0);
	private static final EhPart SECOND_PART = new EhPart(5, EXAM.id(), "Klausurteil B", 1);
	private static final EhCategory SECOND_CATEGORY = new EhCategory(6, SECOND_PART.id(), "Sprache", "", 0);
	private static final EhTask SECOND_TASK = new EhTask(7, SECOND_CATEGORY.id(), "Teilaufgabe 2", 0);
	private static final EhRequirement SECOND_REQUIREMENT = new EhRequirement(8, SECOND_TASK.id(), "Second requirement",
			15, false, 0);
	private static final EhRequirement FOLLOWING_REQUIREMENT = new EhRequirement(9, TASK.id(),
			"Very long user-entered requirement text that should not become a summary title", 2, false, 1);
	private static final EhRequirement BONUS_REQUIREMENT = new EhRequirement(10, TASK.id(), "Bonus requirement", 4, true,
			1);

	@Test
	void preservesCollapsedDetailsWhenRefreshingSameExam() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		assertThat(details).hasSize(3);

		details.get(1).setOpened(false);
		editor.setExam(EXAM);

		final List<Details> refreshedDetails = components(editor, Details.class);
		assertThat(refreshedDetails).hasSize(3);
		assertThat(refreshedDetails).extracting(Details::isOpened).containsExactly(true, false, true);
	}

	@Test
	void savesRequirementWithoutRefreshingWholeTree() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		components(editor, IntegerField.class).getFirst().setValue(7);
		final List<Button> saveButtons = components(editor, Button.class).stream()
				.filter(button -> "Speichern".equals(button.getText())).toList();
		assertThat(saveButtons).hasSize(4);
		saveButtons.getLast().click();

		verify(repository).saveRequirement(new EhRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, REQUIREMENT.bonus(), REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("Gesamtpunktzahl: 7", "100 %");
	}

	@Test
	void savesBonusRequirementWithoutAddingPointsToTotals() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		components(editor, IntegerField.class).getFirst().setValue(7);
		components(editor, Checkbox.class).getFirst().setValue(true);
		final List<Button> saveButtons = components(editor, Button.class).stream()
				.filter(button -> "Speichern".equals(button.getText())).toList();
		saveButtons.getLast().click();

		verify(repository).saveRequirement(new EhRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, true, REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("(+ 7)", "Gesamtpunktzahl: 0 (+ 7)");
	}

	@Test
	void aggregatesBonusPointsWithoutAddingThemToRegularTotal() {
		final ExpectationHorizonRepository repository = repositoryWithRegularAndBonusRequirement();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("5", "(+ 4)", "Summe: 5 (+ 4)",
				"Gesamtpunktzahl: 5 (+ 4)", "100 %");
	}

	@Test
	void showsPartPercentageRelativeToExamTotal() {
		final ExpectationHorizonRepository repository = repositoryWithTwoPartHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("25 %", "75 %");
	}

	@Test
	void numbersRequirementsInsteadOfUsingDescriptionInSummary() {
		final ExpectationHorizonRepository repository = repositoryWithTwoRequirements();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		final List<String> spanTexts = components(editor, Span.class).stream().map(Span::getText).toList();
		assertThat(spanTexts).contains("1", "2");
		assertThat(spanTexts).doesNotContain(REQUIREMENT.descriptionMarkdown(), FOLLOWING_REQUIREMENT.descriptionMarkdown());
	}

	@Test
	void toolbarCollapseButtonTogglesWholeTree() {
		final ExpectationHorizonRepository repository = repositoryWithTwoPartHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		final Button collapseButton = collapseButtons(editor).getFirst();

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-down");

		collapseButton.click();

		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsOnly(false);
		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-right");

		collapseButton.click();

		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsOnly(true);
		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-down");
	}

	@Test
	void showsExpectationHorizonLightOnlyWhenExpectationHorizonExists() {
		final ExpectationHorizonEditor editorWithHierarchy = new ExpectationHorizonEditor(repositoryWithHierarchy());
		editorWithHierarchy.setExam(EXAM);
		assertThat(components(editorWithHierarchy, Span.class).stream().map(Span::getText))
				.anyMatch(text -> text.contains("Licht am Ende des Erwartungshorizonts"));

		final ExpectationHorizonEditor emptyEditor = new ExpectationHorizonEditor(emptyRepository());
		emptyEditor.setExam(EXAM);
		assertThat(components(emptyEditor, Span.class).stream().map(Span::getText))
				.doesNotContain("Licht am Ende des Erwartungshorizonts");
	}

	@Test
	void partCollapseButtonCollapsesDescendantsOnly() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		assertThat(collapseButtons(editor)).hasSize(4);

		collapseButtons(editor).get(1).click();

		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsExactly(true, false, false);
	}

	@Test
	void preservesEhSectionCssHooks() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
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
		assertThat(components(editor, EhBadge.class)).hasSize(6);
	}

	@Test
	void collapseButtonReflectsIndividualDetailsStateChanges() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		final Button collapseButton = collapseButtons(editor).getFirst();
		final List<Details> details = components(editor, Details.class);

		details.forEach(detail -> detail.setOpened(false));

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-right");

		details.getFirst().setOpened(true);

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-down");
	}

	private static ExpectationHorizonRepository repositoryWithHierarchy() {
		final ExpectationHorizonRepository repository = mock(ExpectationHorizonRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT));
		return repository;
	}

	private static ExpectationHorizonRepository repositoryWithTwoPartHierarchy() {
		final ExpectationHorizonRepository repository = mock(ExpectationHorizonRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART, SECOND_PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY, SECOND_CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK, SECOND_TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, SECOND_REQUIREMENT));
		return repository;
	}

	private static ExpectationHorizonRepository repositoryWithTwoRequirements() {
		final ExpectationHorizonRepository repository = mock(ExpectationHorizonRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, FOLLOWING_REQUIREMENT));
		return repository;
	}

	private static ExpectationHorizonRepository repositoryWithRegularAndBonusRequirement() {
		final ExpectationHorizonRepository repository = mock(ExpectationHorizonRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, BONUS_REQUIREMENT));
		return repository;
	}

	private static ExpectationHorizonRepository emptyRepository() {
		final ExpectationHorizonRepository repository = mock(ExpectationHorizonRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of());
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of());
		return repository;
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private static List<String> badgeTexts(final Component root) {
		return components(root, EhBadge.class).stream().map(EhBadge::getText).toList();
	}

	private static List<Button> collapseButtons(final Component root) {
		return components(root, Button.class).stream()
				.filter(button -> "collapse-below".equals(button.getElement().getAttribute("data-action"))).toList();
	}

	private static String collapseIcon(final Button button) {
		return button.getIcon().getElement().getAttribute("icon");
	}
}
