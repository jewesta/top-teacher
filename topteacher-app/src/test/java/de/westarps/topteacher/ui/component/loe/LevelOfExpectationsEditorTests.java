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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.Exam;

class LevelOfExpectationsEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "Beschreibung", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final LoeRequirement REQUIREMENT = new LoeRequirement(4, TASK.id(), "Requirement text", 5, false, 0);
	private static final LoePart SECOND_PART = new LoePart(5, EXAM.id(), "Klausurteil B", 1);
	private static final LoeCategory SECOND_CATEGORY = new LoeCategory(6, SECOND_PART.id(), "Sprache", "", 0);
	private static final LoeTask SECOND_TASK = new LoeTask(7, SECOND_CATEGORY.id(), "Teilaufgabe 2", 0);
	private static final LoeRequirement SECOND_REQUIREMENT = new LoeRequirement(8, SECOND_TASK.id(), "Second requirement",
			15, false, 0);
	private static final LoeRequirement FOLLOWING_REQUIREMENT = new LoeRequirement(9, TASK.id(),
			"Very long user-entered requirement text that should not become a summary title", 2, false, 1);
	private static final LoeRequirement BONUS_REQUIREMENT = new LoeRequirement(10, TASK.id(), "Bonus requirement", 4,
			true, 1);

	@Test
	void preservesCollapsedDetailsWhenRefreshingSameExam() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
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
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		components(editor, IntegerField.class).getFirst().setValue(7);
		final List<Button> saveButtons = saveButtons(editor);
		assertThat(saveButtons).hasSize(1);
		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, REQUIREMENT.bonus(), REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("Gesamt: 7 (+0)", "100 %");
	}

	@Test
	void savesBonusRequirementWithoutAddingPointsToTotals() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		components(editor, IntegerField.class).getFirst().setValue(7);
		bonusButtons(editor).getFirst().click();
		final List<Button> saveButtons = saveButtons(editor);
		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, true, REQUIREMENT.sortOrder()));
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findCategoriesByExamId(anyInt());
		verify(repository, never()).findTasksByExamId(anyInt());
		verify(repository, never()).findRequirementsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(badgeTexts(editor)).contains("Gesamt: 0 (+7)");
	}

	@Test
	void enablesAllSaveButtonsOnlyWhileLevelOfExpectationsIsDirty() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final List<Button> saveButtons = saveButtons(editor);

		assertThat(saveButtons).hasSize(1).extracting(Button::isEnabled).containsOnly(false);

		components(editor, IntegerField.class).getFirst().setValue(7);

		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(true);

		saveButtons.getFirst().click();

		verify(repository).saveRequirement(new LoeRequirement(REQUIREMENT.id(), REQUIREMENT.taskId(),
				REQUIREMENT.descriptionMarkdown(), 7, REQUIREMENT.bonus(), REQUIREMENT.sortOrder()));
		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(false);
	}

	@Test
	void titleFieldsAlsoControlSaveButtonState() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		clearInvocations(repository);
		final List<Button> saveButtons = saveButtons(editor);
		final TextField partTitle = titleField(editor, PART.title());

		assertThat(partTitle.getValueChangeMode()).isEqualTo(ValueChangeMode.EAGER);
		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(false);

		partTitle.setValue("Klausurteil X");

		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(true);

		saveButtons.getFirst().click();

		verify(repository).savePart(new LoePart(PART.id(), PART.examId(), "Klausurteil X", PART.sortOrder()));
		assertThat(saveButtons).extracting(Button::isEnabled).containsOnly(false);
	}

	@Test
	void aggregatesBonusPointsWithoutAddingThemToRegularTotal() {
		final LevelOfExpectationsRepository repository = repositoryWithRegularAndBonusRequirement();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("Summe: 5 (+4)", "Gesamt: 5 (+4)", "100 %");
	}

	@Test
	void pointBadgesReserveSpaceForRegularAndBonusPoints() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

		editor.setExam(EXAM);

		assertThat(badgeTexts(editor)).contains("Summe: 5 (+0)", "Gesamt: 5 (+0)");
		assertThat(components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-point-regular")).map(Span::getText)).contains("5");
		assertThat(components(editor, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-eh-point-bonus")).map(Span::getText)).contains("0");
		assertThat(components(editor, Span.class).stream().map(Span::getText)).contains("\u00a0");
	}

	@Test
	void placesMaxPointsFieldInRequirementSummary() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);

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

		final List<String> spanTexts = components(editor, Span.class).stream().map(Span::getText).toList();
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
	void partCollapseButtonCollapsesDescendantsOnly() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		assertThat(collapseButtons(editor)).hasSize(4);

		collapseButtons(editor).get(1).click();

		assertThat(components(editor, Details.class)).extracting(Details::isOpened).containsExactly(true, false, false);
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
	void collapseButtonReflectsIndividualDetailsStateChanges() {
		final LevelOfExpectationsRepository repository = repositoryWithHierarchy();
		final LevelOfExpectationsEditor editor = new LevelOfExpectationsEditor(repository);
		editor.setExam(EXAM);
		final Button collapseButton = collapseButtons(editor).getFirst();
		final List<Details> details = components(editor, Details.class);

		details.forEach(detail -> detail.setOpened(false));

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-right");

		details.getFirst().setOpened(true);

		assertThat(collapseIcon(collapseButton)).isEqualTo("vaadin:angle-double-down");
	}

	private static LevelOfExpectationsRepository repositoryWithHierarchy() {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT));
		return repository;
	}

	private static LevelOfExpectationsRepository repositoryWithTwoPartHierarchy() {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART, SECOND_PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY, SECOND_CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK, SECOND_TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, SECOND_REQUIREMENT));
		return repository;
	}

	private static LevelOfExpectationsRepository repositoryWithTwoRequirements() {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, FOLLOWING_REQUIREMENT));
		return repository;
	}

	private static LevelOfExpectationsRepository repositoryWithRegularAndBonusRequirement() {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT, BONUS_REQUIREMENT));
		return repository;
	}

	private static LevelOfExpectationsRepository emptyRepository() {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
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
		return components(root, LoeBadge.class).stream().map(LoeBadge::getText).toList();
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
