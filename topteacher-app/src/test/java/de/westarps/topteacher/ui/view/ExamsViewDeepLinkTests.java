package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
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
import de.westarps.topteacher.ui.component.MultiSelectionGrid;

class ExamsViewDeepLinkTests {

	private static final GradingScale GRADING_SCALE = new GradingScale(30, "Standard", 50, Lifecycle.ACTIVE);
	private static final Course COURSE = new Course(10, SchoolClass.CLS_10F,
			new Subject(20, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026), CoursePeriod.FULL_YEAR,
			Lifecycle.ACTIVE, GRADING_SCALE.id());
	private static final Exam EXAM = new Exam(40, COURSE.id(), "Klausur", LocalDate.of(2026, 9, 19), null,
			GRADING_SCALE.id());
	private static final Pupil FIRST_PUPIL = new Pupil(50, "Ada", "Lovelace", Lifecycle.ACTIVE);
	private static final Pupil SECOND_PUPIL = new Pupil(51, "Grace", "Hopper", Lifecycle.ACTIVE);

	private final CourseRepository courses = mock(CourseRepository.class);
	private final ExamRepository exams = mock(ExamRepository.class);
	private final LevelOfExpectationsRepository levelOfExpectations = mock(LevelOfExpectationsRepository.class);
	private final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
	private ExamsView view;

	@BeforeEach
	void setUp() {
		when(courses.findActive()).thenReturn(List.of(COURSE));
		when(courses.findById(COURSE.id())).thenReturn(Optional.of(COURSE));
		when(courses.findPupils(COURSE.id())).thenReturn(List.of(FIRST_PUPIL, SECOND_PUPIL));
		when(exams.findById(EXAM.id())).thenReturn(Optional.of(EXAM));
		when(exams.findByCourseId(COURSE.id())).thenReturn(List.of(EXAM));
		when(exams.findMainExamsByCourseId(COURSE.id())).thenReturn(List.of(EXAM));
		when(exams.findNumbersByCourseId(COURSE.id())).thenReturn(Map.of());
		when(exams.findPupils(EXAM.id())).thenReturn(List.of(FIRST_PUPIL, SECOND_PUPIL));
		when(exams.findAssignablePupils(EXAM.id())).thenReturn(List.of());
		when(exams.findPupilRemovalLocks(EXAM.id())).thenReturn(Map.of());
		when(gradingScales.findActive()).thenReturn(List.of(GRADING_SCALE));
		when(gradingScales.findById(GRADING_SCALE.id())).thenReturn(Optional.of(GRADING_SCALE));
		view = new ExamsView(courses, exams, levelOfExpectations, gradingScales);
	}

	@Test
	void routeKeepsTheExamListAndAddsOptionalDeepLinkSegments() {
		assertThat(ExamsView.class.getAnnotation(Route.class).value()).isEqualTo("exams/:examId?/:section?/:pupilId?");
	}

	@Test
	void opensAnExamWithoutSelectingAnAdditionalSection() {
		view.beforeEnter(event(Map.of("examId", EXAM.id().toString())));

		assertThat(selectedExams()).containsExactly(EXAM);
		assertThat(tabSheet().getSelectedTab().getLabel()).isEqualTo("Klausur");
	}

	@Test
	void opensTheLevelOfExpectationsForAnExam() {
		view.beforeEnter(
				event(Map.of("examId", EXAM.id().toString(), "section", ExamsView.LEVEL_OF_EXPECTATIONS_SECTION)));

		assertThat(selectedExams()).containsExactly(EXAM);
		assertThat(tabSheet().getSelectedTab().getLabel()).isEqualTo("EH");
	}

	@Test
	void opensResultsWithTheRequestedPupilSelected() {
		view.beforeEnter(event(Map.of("examId", EXAM.id().toString(), "section", ExamsView.RESULTS_SECTION, "pupilId",
				SECOND_PUPIL.id().toString())));

		assertThat(selectedExams()).containsExactly(EXAM);
		assertThat(tabSheet().getSelectedTab().getLabel()).isEqualTo("Ergebnisse");
		verify(levelOfExpectations).findRequirementResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id());
	}

	private BeforeEnterEvent event(final Map<String, String> parameters) {
		final BeforeEnterEvent event = mock(BeforeEnterEvent.class);
		when(event.getRouteParameters()).thenReturn(new RouteParameters(parameters));
		return event;
	}

	private TabSheet tabSheet() {
		return components(view, TabSheet.class).getFirst();
	}

	@SuppressWarnings("unchecked")
	private List<Exam> selectedExams() {
		return ((MultiSelectionGrid<Exam>) components(view, MultiSelectionGrid.class).getFirst()).getSelectedItems()
				.stream().toList();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
