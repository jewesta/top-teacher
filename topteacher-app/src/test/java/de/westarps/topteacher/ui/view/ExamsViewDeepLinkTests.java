package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
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
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
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
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
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
	private static final LoePart PART = new LoePart(60, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(61, PART.id(), "Inhalt", "", 0);
	private static final LoeTask TASK = new LoeTask(62, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final LoeRequirement REQUIREMENT = new LoeRequirement(63, TASK.id(), "Anforderung", 5, false, 0);

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
		when(levelOfExpectations.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(levelOfExpectations.findCategoriesByExamId(EXAM.id())).thenReturn(List.of(CATEGORY));
		when(levelOfExpectations.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK));
		when(levelOfExpectations.findRequirementsByExamId(EXAM.id())).thenReturn(List.of(REQUIREMENT));
		view = new ExamsView(courses, exams, levelOfExpectations, gradingScales);
	}

	@Test
	void routeKeepsTheExamListAndAddsOptionalDeepLinkSegments() {
		assertThat(ExamsView.class.getAnnotation(Route.class).value()).isEqualTo("exams/:examId?/:section?/:pupilId?");
		assertThat(Stream.of(ExamsView.class.getAnnotationsByType(RouteAlias.class)).map(RouteAlias::value))
				.containsExactlyInAnyOrder(
						"exams/:examId/:section/part/:partId/category/:categoryId/task/:taskId/requirement/:requirementId",
						"exams/:examId/:section/:pupilId/part/:partId/category/:categoryId/task/:taskId/requirement/:requirementId");
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
		assertThat(levelOfExpectationsStatusIcon()).isEqualTo("vaadin:pencil");
	}

	@Test
	void showsTheCompleteLevelOfExpectationsStateInTheTab() {
		when(gradingScales.findById(GRADING_SCALE.id()))
				.thenReturn(Optional.of(new GradingScale(GRADING_SCALE.id(), "Leer", 0, Lifecycle.ACTIVE)));
		when(levelOfExpectations.findRequirementsByExamId(EXAM.id()))
				.thenReturn(List.of(new LoeRequirement(REQUIREMENT.id(), TASK.id(), "", 0, false, 0)));
		view = new ExamsView(courses, exams, levelOfExpectations, gradingScales);

		view.beforeEnter(
				event(Map.of("examId", EXAM.id().toString(), "section", ExamsView.LEVEL_OF_EXPECTATIONS_SECTION)));

		assertThat(levelOfExpectationsStatusIcon()).isEqualTo("vaadin:check");
	}

	@Test
	void showsTheLockedLevelOfExpectationsStateInTheTabWhenResultsExist() {
		when(levelOfExpectations.hasResultsForExam(EXAM.id())).thenReturn(true);
		view = new ExamsView(courses, exams, levelOfExpectations, gradingScales);

		view.beforeEnter(
				event(Map.of("examId", EXAM.id().toString(), "section", ExamsView.LEVEL_OF_EXPECTATIONS_SECTION)));

		assertThat(levelOfExpectationsStatusIcon()).isEqualTo("vaadin:lock");
	}

	@Test
	void opensAConcreteLevelOfExpectationsPath() {
		view.beforeEnter(
				event(Map.of("examId", EXAM.id().toString(), "section", ExamsView.LEVEL_OF_EXPECTATIONS_SECTION,
						"partId", PART.id().toString(), "categoryId", CATEGORY.id().toString(), "taskId",
						TASK.id().toString(), "requirementId", REQUIREMENT.id().toString())));

		assertThat(selectedExams()).containsExactly(EXAM);
		assertThat(tabSheet().getSelectedTab().getLabel()).isEqualTo("EH");
	}

	@Test
	void opensResultsWithTheRequestedPupilSelected() {
		view.beforeEnter(event(Map.of("examId", EXAM.id().toString(), "section", ExamsView.RESULTS_SECTION, "pupilId",
				SECOND_PUPIL.id().toString())));

		assertThat(selectedExams()).containsExactly(EXAM);
		assertThat(tabSheet().getSelectedTab().getLabel()).isEqualTo("Ergebnisse");
		verify(levelOfExpectations, atLeastOnce()).findRequirementResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id());
	}

	@Test
	void opensAConcreteResultPathForTheRequestedPupil() {
		view.beforeEnter(event(Map.of("examId", EXAM.id().toString(), "section", ExamsView.RESULTS_SECTION, "pupilId",
				SECOND_PUPIL.id().toString(), "partId", PART.id().toString(), "categoryId", CATEGORY.id().toString(),
				"taskId", TASK.id().toString(), "requirementId", REQUIREMENT.id().toString())));

		assertThat(tabSheet().getSelectedTab().getLabel()).isEqualTo("Ergebnisse");
		verify(levelOfExpectations, atLeastOnce()).findRequirementResultsByExamAndPupil(EXAM.id(), SECOND_PUPIL.id());
	}

	private BeforeEnterEvent event(final Map<String, String> parameters) {
		final BeforeEnterEvent event = mock(BeforeEnterEvent.class);
		when(event.getRouteParameters()).thenReturn(new RouteParameters(parameters));
		return event;
	}

	private TabSheet tabSheet() {
		return components(view, TabSheet.class).getFirst();
	}

	private String levelOfExpectationsStatusIcon() {
		final TabSheet tabSheet = tabSheet();
		for (int index = 0; index < tabSheet.getTabCount(); index++) {
			final Tab tab = tabSheet.getTabAt(index);
			if ("EH".equals(tab.getLabel())) {
				return tab.getChildren().filter(Icon.class::isInstance).map(Icon.class::cast)
						.peek(icon -> assertThat(icon.getClassNames()).contains("tt-tab-status-icon"))
						.map(icon -> icon.getElement().getAttribute("icon")).findFirst().orElseThrow();
			}
		}
		throw new IllegalStateException("Missing EH tab");
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
