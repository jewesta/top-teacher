package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.component.FullscreenButton;
import de.westarps.topteacher.ui.component.StepperComboBox;
import de.westarps.vaadin.markdown.MarkdownViewer;

class ExamResultsEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final Subject SUBJECT = new Subject(1, "Englisch", Lifecycle.ACTIVE);
	private static final Course COURSE = new Course(EXAM.courseId(), SchoolClass.CLS_5A, SUBJECT, new SchoolYear(2026),
			CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 30);
	private static final GradingScale GRADING_SCALE = new GradingScale(COURSE.gradingScaleId(), "Standard", 5,
			Lifecycle.ACTIVE);
	private static final Pupil PUPIL = new Pupil(20, "Anna", "Ergebnis", Lifecycle.ACTIVE);
	private static final Pupil SECOND_PUPIL = new Pupil(21, "Berta", "Ergebnis", Lifecycle.ACTIVE);
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final LoeRequirement REQUIREMENT = new LoeRequirement(4, TASK.id(),
			"Nutzt die [korrekte Zeitform](eh:1).", 5, false, 0);
	private static final LoeCriterion CRITERION = new LoeCriterion(5, REQUIREMENT.id(), "1", "korrekte Zeitform", 0,
			true);
	private static final LoeRequirement BONUS_REQUIREMENT = new LoeRequirement(6, TASK.id(), "Bonusaufgabe", 2, true,
			1);

	@Test
	void savesRequirementPointsOnlyWhenToolbarSaveIsClicked() {
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository();
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
				gradingScaleRepository());
		final AtomicInteger changes = new AtomicInteger();
		editor.setChangeHandler(changes::incrementAndGet);

		editor.setExam(EXAM);

		final Button saveButton = saveButton(editor);
		final MenuBar pdfMenu = pdfMenu(editor);
		final IntegerField points = components(editor, IntegerField.class).getFirst();
		assertThat(saveButton.isEnabled()).isFalse();
		assertThat(pdfMenu.isEnabled()).isTrue();
		assertThat(points.getLabel()).isNull();
		assertThat(pointsText(editor)).containsExactly("1 von 5 Punkten");
		assertThat(points.getValue()).isEqualTo(1);
		assertThat(criterionCheckboxes(editor)).hasSize(1);
		assertThat(criterionCheckboxes(editor).getFirst().getElement().getAttribute("aria-label"))
				.isEqualTo("Kriterium 1 erfüllt");
		assertThat(badgeTexts(editor)).contains("Gesamt: 1 (+0)", "Summe: 1 (+0)");
		assertThat(requirementNumberTexts(editor)).containsExactly("1");
		assertThat(components(editor, FullscreenButton.class)).hasSize(1);

		points.setValue(3);

		assertThat(saveButton.isEnabled()).isTrue();
		assertThat(pdfMenu.isEnabled()).isFalse();
		assertThat(pointsText(editor)).containsExactly("3 von 5 Punkten");
		assertThat(badgeTexts(editor)).contains("Gesamt: 3 (+0)", "Summe: 3 (+0)");
		verify(levelOfExpectationsRepository, never()).saveRequirementResult(any());
		verify(levelOfExpectationsRepository, never()).saveCriterionResult(any());

		saveButton.click();

		verify(levelOfExpectationsRepository)
				.saveRequirementResult(new LoeRequirementResult(REQUIREMENT.id(), PUPIL.id(), 3));
		verify(levelOfExpectationsRepository, never()).saveCriterionResult(any(LoeCriterionResult.class));
		assertThat(saveButton.isEnabled()).isFalse();
		assertThat(pdfMenu.isEnabled()).isTrue();
		assertThat(changes).hasValue(1);
	}

	@Test
	void syncsCriterionCheckboxListWithMarkdownViewerAndSaveState() {
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository();
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
				gradingScaleRepository());

		editor.setExam(EXAM);

		final Button saveButton = saveButton(editor);
		final Checkbox criterionCheckbox = criterionCheckboxes(editor).getFirst();
		final MarkdownViewer description = components(editor, MarkdownViewer.class).getFirst();
		assertThat(criterionCheckbox.getValue()).isFalse();
		assertThat(description.getCheckedTagKeys()).isEmpty();
		assertThat(criterionIndicatorTexts(editor)).containsExactly("0 von 1 Kriterien erfüllt");

		criterionCheckbox.setValue(true);

		assertThat(saveButton.isEnabled()).isTrue();
		assertThat(description.getCheckedTagKeys()).containsExactly("1");
		assertThat(criterionIndicatorTexts(editor)).containsExactly("1 von 1 Kriterien erfüllt");
		verify(levelOfExpectationsRepository, never()).saveCriterionResult(any(LoeCriterionResult.class));

		saveButton.click();

		verify(levelOfExpectationsRepository)
				.saveCriterionResult(new LoeCriterionResult(CRITERION.id(), PUPIL.id(), true));
		assertThat(saveButton.isEnabled()).isFalse();
	}

	@Test
	void showsBonusStarOnlyForBonusRequirements() {
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository(
				List.of(REQUIREMENT, BONUS_REQUIREMENT), List.of(CRITERION),
				List.of(new LoeRequirementResult(REQUIREMENT.id(), PUPIL.id(), 1)));
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
				gradingScaleRepository());

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
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository();
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
				gradingScaleRepository());

		editor.setExam(EXAM);

		final Button saveButton = saveButton(editor);
		final TextArea comment = components(editor, TextArea.class).getFirst();
		assertThat(saveButton.isEnabled()).isFalse();
		assertThat(comment.getMinRows()).isEqualTo(2);
		assertThat(comment.getMaxRows()).isEqualTo(2);

		comment.setValue("Zeitform noch einmal besprechen.");

		assertThat(saveButton.isEnabled()).isTrue();

		saveButton.click();

		verify(levelOfExpectationsRepository).saveRequirementResult(
				new LoeRequirementResult(REQUIREMENT.id(), PUPIL.id(), 1, "Zeitform noch einmal besprechen."));
		assertThat(saveButton.isEnabled()).isFalse();
	}

	@Test
	void switchingPupilsReusesRenderedResultStructure() {
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository();
		when(levelOfExpectationsRepository.findCriterionResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id()))
				.thenReturn(List.of(new LoeCriterionResult(CRITERION.id(), SECOND_PUPIL.id(), true)));
		when(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id()))
				.thenReturn(List
						.of(new LoeRequirementResult(REQUIREMENT.id(), SECOND_PUPIL.id(), 4, "Guter Fortschritt.")));
		final CourseRepository courseRepository = courseRepository(List.of(PUPIL, SECOND_PUPIL));
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
				gradingScaleRepository());

		editor.setExam(EXAM);

		final IntegerField points = components(editor, IntegerField.class).getFirst();
		final TextArea comment = components(editor, TextArea.class).getFirst();
		final Checkbox criterionCheckbox = criterionCheckboxes(editor).getFirst();
		assertThat(points.getValue()).isEqualTo(1);
		assertThat(comment.getValue()).isEmpty();
		assertThat(criterionCheckbox.getValue()).isFalse();
		assertThat(criterionIndicatorTexts(editor)).containsExactly("0 von 1 Kriterien erfüllt");

		pupilSelector(editor).setValue(SECOND_PUPIL);

		assertThat(points.getValue()).isEqualTo(4);
		assertThat(comment.getValue()).isEqualTo("Guter Fortschritt.");
		assertThat(criterionCheckbox.getValue()).isTrue();
		assertThat(pointsText(editor)).containsExactly("4 von 5 Punkten");
		assertThat(criterionIndicatorTexts(editor)).containsExactly("1 von 1 Kriterien erfüllt");
		assertThat(components(editor, IntegerField.class).getFirst()).isSameAs(points);
		assertThat(components(editor, TextArea.class).getFirst()).isSameAs(comment);
		assertThat(criterionCheckboxes(editor).getFirst()).isSameAs(criterionCheckbox);
		assertThat(badgeTexts(editor)).contains("Gesamt: 4 (+0)", "Summe: 4 (+0)");
		verify(levelOfExpectationsRepository, times(1)).syncCriteriaForExam(EXAM.id());
		verify(levelOfExpectationsRepository, times(1)).findPartsByExamId(EXAM.id());
		verify(levelOfExpectationsRepository, times(1)).findCategoriesByExamId(EXAM.id());
		verify(levelOfExpectationsRepository, times(1)).findTasksByExamId(EXAM.id());
		verify(levelOfExpectationsRepository, times(1)).findRequirementsByExamId(EXAM.id());
		verify(levelOfExpectationsRepository, times(1)).findActiveCriteriaByExamId(EXAM.id());
	}

	@Test
	void deletesSelectedPupilResultsOnlyAfterConfirmation() {
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository();
		final CourseRepository courseRepository = courseRepository();
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
					gradingScaleRepository());
			editor.setExam(EXAM);

			final Button deleteButton = deleteButton(editor);
			final ConfirmDialog confirmation = components(editor, ConfirmDialog.class).getFirst();
			final IntegerField points = components(editor, IntegerField.class).getFirst();
			final TextArea comment = components(editor, TextArea.class).getFirst();
			assertThat(deleteButton.isEnabled()).isTrue();
			assertThat(points.getValue()).isEqualTo(1);

			deleteButton.click();

			assertThat(confirmation.isOpened()).isTrue();
			verify(levelOfExpectationsRepository, never()).deleteResultsByExamAndPupil(EXAM.id(), PUPIL.id());

			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

			verify(levelOfExpectationsRepository).deleteResultsByExamAndPupil(EXAM.id(), PUPIL.id());
			assertThat(points.getValue()).isZero();
			assertThat(comment.getValue()).isEmpty();
			assertThat(deleteButton.isEnabled()).isFalse();
			assertThat(pointsText(editor)).containsExactly("0 von 5 Punkten");
			assertThat(criterionIndicatorTexts(editor)).containsExactly("0 von 1 Kriterien erfüllt");
			assertThat(badgeTexts(editor)).contains("Gesamt: 0 (+0)", "Summe: 0 (+0)");
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void rejectsSavingResultsWhenRegularLoePointsDoNotMatchTheGradingScale() {
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository();
		final CourseRepository courseRepository = courseRepository();
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
					gradingScaleRepository(6));
			editor.setExam(EXAM);

			components(editor, IntegerField.class).getFirst().setValue(2);
			saveButton(editor).click();

			verify(levelOfExpectationsRepository, never()).saveRequirementResult(any(LoeRequirementResult.class));
			verify(levelOfExpectationsRepository, never()).saveCriterionResult(any(LoeCriterionResult.class));
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void allowsBonusResultsThatWouldOnlyApplyUpToTheGradingScaleMaximum() {
		final LevelOfExpectationsRepository levelOfExpectationsRepository = levelOfExpectationsRepository(
				List.of(REQUIREMENT, BONUS_REQUIREMENT), List.of(CRITERION),
				List.of(new LoeRequirementResult(REQUIREMENT.id(), PUPIL.id(), 5),
						new LoeRequirementResult(BONUS_REQUIREMENT.id(), PUPIL.id(), 0)));
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, levelOfExpectationsRepository,
				gradingScaleRepository());
		editor.setExam(EXAM);

		components(editor, IntegerField.class).get(1).setValue(2);
		saveButton(editor).click();

		verify(levelOfExpectationsRepository)
				.saveRequirementResult(new LoeRequirementResult(BONUS_REQUIREMENT.id(), PUPIL.id(), 2));
	}

	private static LevelOfExpectationsRepository levelOfExpectationsRepository() {
		return levelOfExpectationsRepository(List.of(REQUIREMENT), List.of(CRITERION),
				List.of(new LoeRequirementResult(REQUIREMENT.id(), PUPIL.id(), 1)));
	}

	private static LevelOfExpectationsRepository levelOfExpectationsRepository(final List<LoeRequirement> requirements,
			final List<LoeCriterion> criteria, final List<LoeRequirementResult> requirementResults) {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(requirements);
		when(repository.findActiveCriteriaByExamId(EXAM.id())).thenReturn(criteria);
		when(repository.findCriterionResultsByExamAndPupil(EXAM.id(), PUPIL.id())).thenReturn(
				criteria.stream().map(criterion -> new LoeCriterionResult(criterion.id(), PUPIL.id(), false)).toList());
		when(repository.findRequirementResultsByExamAndPupil(EXAM.id(), PUPIL.id())).thenReturn(requirementResults);
		return repository;
	}

	private static CourseRepository courseRepository() {
		return courseRepository(List.of(PUPIL));
	}

	private static CourseRepository courseRepository(final List<Pupil> pupils) {
		final CourseRepository repository = mock(CourseRepository.class);
		when(repository.findPupils(EXAM.courseId())).thenReturn(pupils);
		when(repository.findById(EXAM.courseId())).thenReturn(Optional.of(COURSE));
		return repository;
	}

	private static GradingScaleRepository gradingScaleRepository() {
		return gradingScaleRepository(GRADING_SCALE.maxPoints());
	}

	private static GradingScaleRepository gradingScaleRepository(final int maxPoints) {
		final GradingScaleRepository repository = mock(GradingScaleRepository.class);
		when(repository.findById(COURSE.gradingScaleId())).thenReturn(
				Optional.of(new GradingScale(COURSE.gradingScaleId(), "Standard", maxPoints, Lifecycle.ACTIVE)));
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

	private static MenuBar pdfMenu(final Component root) {
		return components(root, MenuBar.class).stream().filter(menu -> menu.getClassNames().contains("tt-pdf-menu"))
				.findFirst().orElseThrow();
	}

	private static List<String> badgeTexts(final Component root) {
		return components(root, LoeBadge.class).stream().map(LoeBadge::getText).toList();
	}

	private static List<Icon> bonusIcons(final Component root) {
		return components(root, Icon.class).stream()
				.filter(icon -> icon.getClassNames().contains("tt-results-bonus-icon")).toList();
	}

	private static List<String> requirementNumberTexts(final Component root) {
		return components(root, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-results-requirement-number")).map(Span::getText)
				.toList();
	}

	private static List<String> criterionIndicatorTexts(final Component root) {
		return components(root, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-results-criteria-indicator")).map(Span::getText)
				.toList();
	}

	private static List<String> pointsText(final Component root) {
		return components(root, Span.class).stream()
				.filter(span -> span.getClassNames().contains("tt-results-points-text")).map(Span::getText).toList();
	}

	private static List<Checkbox> criterionCheckboxes(final Component root) {
		return components(root, Checkbox.class).stream()
				.filter(checkbox -> checkbox.getClassNames().contains("tt-results-criterion-checkbox")).toList();
	}

	@SuppressWarnings("unchecked")
	private static StepperComboBox<Pupil> pupilSelector(final Component root) {
		return components(root, StepperComboBox.class).getFirst();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
