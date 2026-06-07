package de.westarps.topteacher.ui.component.eh;

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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.backend.repo.ExpectationHorizonRepository;
import de.westarps.topteacher.model.eh.EhCategory;
import de.westarps.topteacher.model.eh.EhPart;
import de.westarps.topteacher.model.eh.EhRequirement;
import de.westarps.topteacher.model.eh.EhTask;
import de.westarps.topteacher.model.Exam;

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
	private static final EhRequirement BONUS_REQUIREMENT = new EhRequirement(10, TASK.id(), "Bonus requirement", 4,
			true, 1);

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
		final List<Button> saveButtons = saveButtons(editor);
		assertThat(saveButtons).hasSize(1);
		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new EhRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, REQUIREMENT.bonus(), REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("Gesamtpunktzahl: 7 (+0)", "100 %");
	}

	@Test
	void savesBonusRequirementWithoutAddingPointsToTotals() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		components(editor, IntegerField.class).getFirst().setValue(7);
		bonusButtons(editor).getFirst().click();
		final List<Button> saveButtons = saveButtons(editor);
		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new EhRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, true, REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("Gesamtpunktzahl: 0 (+7)");
	}

	@Test
	void enablesAllSaveButtonsOnlyWhileExpectationHorizonIsDirty() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		final List<Button> saveButtons = saveButtons(editor);

		assertThat(saveButtons).hasSize(1).extracting(Button::isEnabled).containsOnly(false);

		components(editor, IntegerField.class).getFirst().setValue(7);

		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(true);

		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new EhRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, REQUIREMENT.bonus(), REQUIREMENT.sortOrder()));
		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(false);
	}

	@Test
	void titleFieldsAlsoControlSaveButtonState() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);
		editor.setExam(EXAM);
		clearInvocations(repository);
		final List<Button> saveButtons = saveButtons(editor);
		final TextField partTitle = titleField(editor, PART.title());

		assertThat(partTitle.getValueChangeMode()).isEqualTo(ValueChangeMode.EAGER);
		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(false);

		partTitle.setValue("Klausurteil X");

		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(true);

		saveButtons.getFirst().click();

		verify(repository).savePart(new EhPart(PART.id(), PART.examId(), "Klausurteil X", PART.sortOrder()));
		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(false);
	}

	@Test
	void aggregatesBonusPointsWithoutAddingThemToRegularTotal() {
		final ExpectationHorizonRepository repository = repositoryWithRegularAndBonusRequirement();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("Summe: 5 (+4)", "Gesamtpunktzahl: 5 (+4)", "100 %");
	}

	@Test
	void pointBadgesReserveSpaceForRegularAndBonusPoints() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("Summe: 5 (+0)", "Gesamtpunktzahl: 5 (+0)");
		assertThat(components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-point-regular")).map(Span::getText)).contains("5");
		assertThat(components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-point-bonus")).map(Span::getText)).contains("0");
		assertThat(components(editor, Span.class).stream().map(Span::getText)).contains("\u00a0");
	}

	@Test
	void placesMaxPointsFieldInRequirementSummary() {
		final ExpectationHorizonRepository repository = repositoryWithHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		final IntegerField maxPoints = components(editor, IntegerField.class).getFirst();
		final Button bonus = bonusButtons(editor).getFirst();
		final Component parent = maxPoints.getParent().orElseThrow();
		final Component bonusParent = bonus.getParent().orElseThrow();
		final Component headerControls = parent.getParent().orElseThrow();

		assertThat(maxPoints.getElement().getAttribute("aria-label")).isEqualTo("Max. Punkte");
		assertThat(maxPoints.getPrefixComponent()).isNull();
		assertThat(maxPoints.getClassNames()).contains("tt-eh-requirement-points-field");
		assertThat(maxPoints.getWidth()).isNull();
		assertThat(parent).isInstanceOf(HorizontalLayout.class);
		assertThat(parent.getClassNames()).contains("tt-eh-requirement-points-control");
		assertThat(parent.getChildren().filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText))
				.containsExactly("Max. Punkte");
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
		final ExpectationHorizonRepository repository = repositoryWithTwoPartHierarchy();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("25 %", "75 %");
		assertThat(components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-percentage-number")).map(Span::getText))
				.contains("25", "75");
	}

	@Test
	void numbersRequirementsInsteadOfUsingDescriptionInSummary() {
		final ExpectationHorizonRepository repository = repositoryWithTwoRequirements();
		final ExpectationHorizonEditor editor = new ExpectationHorizonEditor(repository);

		editor.setExam(EXAM);

		final List<String> spanTexts = components(editor, Span.class).stream().map(Span::getText).toList();
		assertThat(spanTexts).contains("Anforderung 1", "Anforderung 2");
		assertThat(spanTexts).doesNotContain("1", "2");
		assertThat(spanTexts).doesNotContain(REQUIREMENT.descriptionMarkdown(),
				FOLLOWING_REQUIREMENT.descriptionMarkdown());
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
		assertThat(components(editor, EhBadge.class)).hasSize(5);
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

	private static List<Button> saveButtons(final Component root) {
		return components(root, Button.class).stream().filter(button -> "Speichern".equals(button.getText())).toList();
	}

	private static TextField titleField(final Component root, final String value) {
		return components(root, TextField.class).stream().filter(field -> value.equals(field.getValue())).findFirst()
				.orElseThrow();
	}

	private static List<Button> collapseButtons(final Component root) {
		return components(root, Button.class).stream()
				.filter(button -> "collapse-below".equals(button.getElement().getAttribute("data-action"))).toList();
	}

	private static List<Button> bonusButtons(final Component root) {
		return components(root, Button.class).stream()
				.filter(button -> "toggle-bonus".equals(button.getElement().getAttribute("data-action"))).toList();
	}

	private static String collapseIcon(final Button button) {
		return button.getIcon().getElement().getAttribute("icon");
	}
}
