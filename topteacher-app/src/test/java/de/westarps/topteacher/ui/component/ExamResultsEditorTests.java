package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.vaadin.flow.component.textfield.IntegerField;

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
	private static final EhPart PART = new EhPart(1, EXAM.id(), "Klausurteil A", 0);
	private static final EhCategory CATEGORY = new EhCategory(2, PART.id(), "Inhalt", "", 0);
	private static final EhTask TASK = new EhTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final EhRequirement REQUIREMENT = new EhRequirement(4, TASK.id(),
			"Nutzt die [korrekte Zeitform](eh:1).", 5, false, 0);
	private static final EhCriterion CRITERION = new EhCriterion(5, REQUIREMENT.id(), "1", "korrekte Zeitform", 0,
			true);

	@Test
	void savesRequirementPointsOnlyWhenToolbarSaveIsClicked() {
		final ExpectationHorizonRepository expectationHorizonRepository = expectationHorizonRepository();
		final CourseRepository courseRepository = courseRepository();
		final ExamResultsEditor editor = new ExamResultsEditor(courseRepository, expectationHorizonRepository);

		editor.setExam(EXAM);

		final Button saveButton = saveButton(editor);
		final IntegerField points = components(editor, IntegerField.class).getFirst();
		assertThat(saveButton.isEnabled()).isFalse();
		assertThat(points.getValue()).isEqualTo(1);
		assertThat(badgeTexts(editor)).contains("Summe: 1 (+0)");

		points.setValue(3);

		assertThat(saveButton.isEnabled()).isTrue();
		assertThat(badgeTexts(editor)).contains("Summe: 3 (+0)");
		verify(expectationHorizonRepository, never()).saveRequirementResult(any());
		verify(expectationHorizonRepository, never()).saveCriterionResult(any());

		saveButton.click();

		verify(expectationHorizonRepository).saveRequirementResult(new EhRequirementResult(REQUIREMENT.id(),
				PUPIL.id(), 3));
		verify(expectationHorizonRepository, never()).saveCriterionResult(any(EhCriterionResult.class));
		assertThat(saveButton.isEnabled()).isFalse();
	}

	private static ExpectationHorizonRepository expectationHorizonRepository() {
		final ExpectationHorizonRepository repository = mock(ExpectationHorizonRepository.class);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(repository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(repository.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT));
		when(repository.findActiveCriteriaByExamId(EXAM.id())).thenReturn(List.of(CRITERION));
		when(repository.findCriterionResultsByExamAndPupil(EXAM.id(), PUPIL.id()))
				.thenReturn(List.of(new EhCriterionResult(CRITERION.id(), PUPIL.id(), false)));
		when(repository.findRequirementResultsByExamAndPupil(EXAM.id(), PUPIL.id()))
				.thenReturn(List.of(new EhRequirementResult(REQUIREMENT.id(), PUPIL.id(), 1)));
		return repository;
	}

	private static CourseRepository courseRepository() {
		final CourseRepository repository = mock(CourseRepository.class);
		when(repository.findPupils(EXAM.courseId())).thenReturn(List.of(PUPIL));
		return repository;
	}

	private static Button saveButton(final Component root) {
		return components(root, Button.class).stream().filter(button -> "Speichern".equals(button.getText()))
				.findFirst().orElseThrow();
	}

	private static List<String> badgeTexts(final Component root) {
		return components(root, EhBadge.class).stream().map(EhBadge::getText).toList();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
