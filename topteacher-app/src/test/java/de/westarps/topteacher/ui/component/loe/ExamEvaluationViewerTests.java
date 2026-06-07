package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.Query;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

class ExamEvaluationViewerTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final Course COURSE = new Course(EXAM.courseId(), SchoolClass.CLS_5A, Subject.ENGLISH,
			new SchoolYear(2026), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 30);
	private static final GradingScale GRADING_SCALE = new GradingScale(COURSE.gradingScaleId(), "Standard", 5,
			Lifecycle.ACTIVE);
	private static final Pupil PUPIL = new Pupil(20, "Anna", "Ergebnis", Lifecycle.ACTIVE);
	private static final Pupil SECOND_PUPIL = new Pupil(21, "Berta", "Ergebnis", Lifecycle.ACTIVE);
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final LoeRequirement REQUIREMENT = new LoeRequirement(4, TASK.id(), "Anforderung", 5, false, 0);
	private static final LoeRequirement BONUS_REQUIREMENT = new LoeRequirement(5, TASK.id(), "Bonus", 2, true, 1);

	@Test
	void rendersAggregationColumnsForAllAssignedPupils() {
		final CourseRepository courseRepository = mock(CourseRepository.class);
		when(courseRepository.findById(EXAM.courseId())).thenReturn(Optional.of(COURSE));
		when(courseRepository.findPupils(EXAM.courseId())).thenReturn(List.of(PUPIL, SECOND_PUPIL));

		final LevelOfExpectationsRepository levelOfExpectationsRepository = mock(LevelOfExpectationsRepository.class);
		when(levelOfExpectationsRepository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(levelOfExpectationsRepository.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(levelOfExpectationsRepository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(levelOfExpectationsRepository.findRequirementsByExamId(EXAM.id()))
				.thenReturn(List.of(REQUIREMENT, BONUS_REQUIREMENT));
		when(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(EXAM.id(), PUPIL.id()))
				.thenReturn(List.of(new LoeRequirementResult(REQUIREMENT.id(), PUPIL.id(), 4),
						new LoeRequirementResult(BONUS_REQUIREMENT.id(), PUPIL.id(), 1)));
		when(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id()))
				.thenReturn(List.of(new LoeRequirementResult(REQUIREMENT.id(), SECOND_PUPIL.id(), 2)));

		final GradingScaleRepository gradingScaleRepository = mock(GradingScaleRepository.class);
		when(gradingScaleRepository.findById(COURSE.gradingScaleId())).thenReturn(Optional.of(GRADING_SCALE));
		when(gradingScaleRepository.findRangesByGradingScaleId(GRADING_SCALE.id())).thenReturn(List.of(
				new GradingScaleRange(1, GRADING_SCALE.id(), GradeLevel.SEHR_GUT_PLUS, 5, 5),
				new GradingScaleRange(2, GRADING_SCALE.id(), GradeLevel.AUSREICHEND, 0, 4)));

		final ExamEvaluationViewer viewer =
				new ExamEvaluationViewer(courseRepository, levelOfExpectationsRepository, gradingScaleRepository);

		viewer.setExam(EXAM);

		final Grid<?> grid = components(viewer, Grid.class).getFirst();
		assertThat(grid.getColumns()).hasSize(7);
		assertThat(grid.getColumns().subList(0, 3)).allMatch(Grid.Column::isFrozen);
		assertThat(grid.getColumns().subList(3, grid.getColumns().size())).noneMatch(Grid.Column::isFrozen);
		assertThat(itemCount(grid)).isEqualTo(2);
		assertThat(components(viewer, Span.class).stream().map(Span::getText)).contains("2 Schüler");
		verify(levelOfExpectationsRepository).findRequirementResultsByExamAndPupil(EXAM.id(), PUPIL.id());
		verify(levelOfExpectationsRepository).findRequirementResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static long itemCount(final Grid<?> grid) {
		return grid.getDataProvider().fetch(new Query()).count();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
