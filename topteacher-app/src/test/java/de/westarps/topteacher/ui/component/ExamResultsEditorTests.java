package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;

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
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

class ExamResultsEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final Pupil PUPIL = new Pupil(20, "Anna", "Ergebnis", Lifecycle.ACTIVE);
	private static final Pupil SECOND_PUPIL = new Pupil(21, "Berta", "Ergebnis", Lifecycle.ACTIVE);
	private static final EhPart PART = new EhPart(1, EXAM.id(), "Klausurteil A", 0);
	private static final EhCategory CATEGORY = new EhCategory(2, PART.id(), "Inhalt", "", 0);
	private static final EhTask TASK = new EhTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final EhRequirement REQUIREMENT = new EhRequirement(4, TASK.id(),
			"Nutzt die [korrekte Zeitform](eh:1).", 5, false, 0);
	private static final EhCriterion CRITERION = new EhCriterion(5, REQUIREMENT.id(), "1", "korrekte Zeitform", 0,
			true);
	private static final EhRequirement BONUS_REQUIREMENT = new EhRequirement(6, TASK.id(), "Bonusaufgabe", 2, true, 1);

	@Test
	void savesRequirementPointsOnlyWhenToolbarSaveIsClicked() {
		final ExpectationHorizonRepository expectationHorizonRepository = expectationHorizonRepository();
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, expectationHorizonRepository);

		editor.setExam(EXAM);

		final Button saveButton = saveButton(editor);
		final IntegerField points = components(editor, IntegerField.class).getFirst();
		assertThat(saveButton.isEnabled()).isFalse();
		assertThat(points.getLabel()).isEqualTo("Punkte von 5");
		assertThat(points.getValue()).isEqualTo(1);
		assertThat(badgeTexts(editor)).contains("Gesamtpunkte: 1 (+0)", "Summe: 1 (+0)");
		assertThat(requirementNumberTexts(editor)).containsExactly("1");

		points.setValue(3);

		assertThat(saveButton.isEnabled()).isTrue();
		assertThat(badgeTexts(editor)).contains("Gesamtpunkte: 3 (+0)", "Summe: 3 (+0)");
		verify(expectationHorizonRepository, never()).saveRequirementResult(any());
		verify(expectationHorizonRepository, never()).saveCriterionResult(any());

		saveButton.click();

		verify(expectationHorizonRepository).saveRequirementResult(new EhRequirementResult(REQUIREMENT.id(),
				PUPIL.id(), 3));
		verify(expectationHorizonRepository, never()).saveCriterionResult(any(EhCriterionResult.class));
		assertThat(saveButton.isEnabled()).isFalse();
	}

	@Test
	void showsBonusStarOnlyForBonusRequirements() {
		final ExpectationHorizonRepository expectationHorizonRepository = expectationHorizonRepository(
				List.of(REQUIREMENT, BONUS_REQUIREMENT), List.of(CRITERION),
				List.of(new EhRequirementResult(REQUIREMENT.id(), PUPIL.id(), 1)));
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, expectationHorizonRepository);

		editor.setExam(EXAM);

		final Icon bonusIcon = bonusIcons(editor).getFirst();
		final Component marker = bonusIcon.getParent().orElseThrow();
		final Component requirement = marker.getParent().orElseThrow();
		final List<Component> markerChildren = marker.getChildren().toList();
		final List<Component> requirementChildren = requirement.getChildren().toList();
		assertThat(bonusIcons(editor)).hasSize(1);
		assertThat(requirement.getClassNames()).contains("tt-results-requirement");
		assertThat(marker.getClassNames()).contains("tt-results-requirement-marker");
		assertThat(markerChildren.get(0).getClassNames()).contains("tt-results-requirement-number");
		assertThat(markerChildren.get(1)).isSameAs(bonusIcon);
		assertThat(requirementChildren.get(0)).isSameAs(marker);
		assertThat(requirementChildren.get(1).getClassNames()).contains("tt-results-requirement-content");
		assertThat(requirementChildren.get(2).getClassNames()).contains("tt-results-requirement-points-area");
		assertThat(components(editor, IntegerField.class)).hasSize(2);
	}

	@Test
	void savesRequirementCommentWithoutRerendering() {
		final ExpectationHorizonRepository expectationHorizonRepository = expectationHorizonRepository();
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, expectationHorizonRepository);

		editor.setExam(EXAM);

		final Button saveButton = saveButton(editor);
		final TextArea comment = components(editor, TextArea.class).getFirst();
		assertThat(saveButton.isEnabled()).isFalse();
		assertThat(comment.getMinRows()).isEqualTo(2);
		assertThat(comment.getMaxRows()).isEqualTo(2);

		comment.setValue("Zeitform noch einmal besprechen.");

		assertThat(saveButton.isEnabled()).isTrue();

		saveButton.click();

		verify(expectationHorizonRepository).saveRequirementResult(new EhRequirementResult(REQUIREMENT.id(),
				PUPIL.id(), 1, "Zeitform noch einmal besprechen."));
		assertThat(saveButton.isEnabled()).isFalse();
	}

	@Test
	void switchingPupilsReusesRenderedResultStructure() {
		final ExpectationHorizonRepository expectationHorizonRepository = expectationHorizonRepository();
		when(expectationHorizonRepository.findCriterionResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id()))
				.thenReturn(List.of(new EhCriterionResult(CRITERION.id(), SECOND_PUPIL.id(), true)));
		when(expectationHorizonRepository.findRequirementResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id()))
				.thenReturn(List.of(new EhRequirementResult(REQUIREMENT.id(), SECOND_PUPIL.id(), 4,
						"Guter Fortschritt.")));
		final CourseRepository courseRepository = courseRepository(List.of(PUPIL, SECOND_PUPIL));
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, expectationHorizonRepository);

		editor.setExam(EXAM);

		final IntegerField points = components(editor, IntegerField.class).getFirst();
		final TextArea comment = components(editor, TextArea.class).getFirst();
		assertThat(points.getValue()).isEqualTo(1);
		assertThat(comment.getValue()).isEmpty();
		assertThat(criterionIndicatorTexts(editor)).containsExactly("0/1 Kriterium");

		pupilSelector(editor).setValue(SECOND_PUPIL);

		assertThat(points.getValue()).isEqualTo(4);
		assertThat(comment.getValue()).isEqualTo("Guter Fortschritt.");
		assertThat(criterionIndicatorTexts(editor)).containsExactly("1/1 Kriterium");
		assertThat(components(editor, IntegerField.class).getFirst()).isSameAs(points);
		assertThat(components(editor, TextArea.class).getFirst()).isSameAs(comment);
		assertThat(badgeTexts(editor)).contains("Gesamtpunkte: 4 (+0)", "Summe: 4 (+0)");
		verify(expectationHorizonRepository, times(1)).syncCriteriaForExam(EXAM.id());
		verify(expectationHorizonRepository, times(1)).findPartsByExamId(EXAM.id());
		verify(expectationHorizonRepository, times(1)).findCategoriesByExamId(EXAM.id());
		verify(expectationHorizonRepository, times(1)).findTasksByExamId(EXAM.id());
		verify(expectationHorizonRepository, times(1)).findRequirementsByExamId(EXAM.id());
		verify(expectationHorizonRepository, times(1)).findActiveCriteriaByExamId(EXAM.id());
	}

	@Test
	void deletesSelectedPupilResultsOnlyAfterConfirmation() {
		final ExpectationHorizonRepository expectationHorizonRepository = expectationHorizonRepository();
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, expectationHorizonRepository);

		UI.setCurrent(new UI());
		try {
			editor.setExam(EXAM);

			final Button deleteButton = deleteButton(editor);
			final ConfirmDialog confirmation = components(editor, ConfirmDialog.class).getFirst();
			final IntegerField points = components(editor, IntegerField.class).getFirst();
			final TextArea comment = components(editor, TextArea.class).getFirst();
			assertThat(deleteButton.isEnabled()).isTrue();
			assertThat(points.getValue()).isEqualTo(1);

			deleteButton.click();

			assertThat(confirmation.isOpened()).isTrue();
			verify(expectationHorizonRepository, never()).deleteResultsByExamAndPupil(EXAM.id(), PUPIL.id());

			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

			verify(expectationHorizonRepository).deleteResultsByExamAndPupil(EXAM.id(), PUPIL.id());
			assertThat(points.getValue()).isZero();
			assertThat(comment.getValue()).isEmpty();
			assertThat(deleteButton.isEnabled()).isFalse();
			assertThat(criterionIndicatorTexts(editor)).containsExactly("0/1 Kriterium");
			assertThat(badgeTexts(editor)).contains("Gesamtpunkte: 0 (+0)", "Summe: 0 (+0)");
		} finally {
			UI.setCurrent(null);
		}
	}

	private static ExpectationHorizonRepository expectationHorizonRepository() {
		return expectationHorizonRepository(List.of(REQUIREMENT), List.of(CRITERION),
				List.of(new EhRequirementResult(REQUIREMENT.id(), PUPIL.id(), 1)));
	}

	private static ExpectationHorizonRepository expectationHorizonRepository(final List<EhRequirement> requirements,
			final List<EhCriterion> criteria, final List<EhRequirementResult> requirementResults) {
		final ExpectationHorizonRepository repository = mock(ExpectationHorizonRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(requirements);
		when(repository.findActiveCriteriaByExamId(EXAM.id())).thenReturn(criteria);
		when(repository.findCriterionResultsByExamAndPupil(EXAM.id(), PUPIL.id()))
				.thenReturn(criteria.stream()
						.map(criterion -> new EhCriterionResult(criterion.id(), PUPIL.id(), false))
						.toList());
		when(repository.findRequirementResultsByExamAndPupil(EXAM.id(), PUPIL.id())).thenReturn(requirementResults);
		return repository;
	}

	private static CourseRepository courseRepository() {
		return courseRepository(List.of(PUPIL));
	}

	private static CourseRepository courseRepository(final List<Pupil> pupils) {
		final CourseRepository repository = mock(CourseRepository.class);
		when(repository.findPupils(EXAM.courseId())).thenReturn(pupils);
		return repository;
	}

	private static Button saveButton(final Component root) {
		return components(root, Button.class).stream().filter(button -> "Speichern".equals(button.getText()))
				.findFirst().orElseThrow();
	}

	private static Button deleteButton(final Component root) {
		return components(root, Button.class).stream()
				.filter(button -> "Ergebnisse löschen".equals(button.getElement().getAttribute("aria-label")))
				.findFirst().orElseThrow();
	}

	private static List<String> badgeTexts(final Component root) {
		return components(root, EhBadge.class).stream().map(EhBadge::getText).toList();
	}

	private static List<Icon> bonusIcons(final Component root) {
		return components(root, Icon.class).stream()
				.filter(icon -> icon.getClassNames().contains("tt-results-bonus-icon"))
				.toList();
	}

	private static List<String> requirementNumberTexts(final Component root) {
		return components(root, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-results-requirement-number"))
				.map(Span::getText)
				.toList();
	}

	private static List<String> criterionIndicatorTexts(final Component root) {
		return components(root, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-results-criteria-indicator"))
				.map(Span::getText)
				.toList();
	}

	@SuppressWarnings("unchecked")
	private static StepperComboBox<Pupil> pupilSelector(final Component root) {
		return (StepperComboBox<Pupil>) components(root, StepperComboBox.class).getFirst();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
