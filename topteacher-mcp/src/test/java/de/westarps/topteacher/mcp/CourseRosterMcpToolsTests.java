package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.CourseDraft;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.CourseRosterResult;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.CourseRosterStatus;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.PupilConflictAction;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.PupilConflictResolution;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.PupilDraft;
import de.westarps.topteacher.mcp.CourseRosterWriter.CreatedCourseRoster;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.Subject;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

@ExtendWith(MockitoExtension.class)
class CourseRosterMcpToolsTests {

	private static final Subject SUBJECT = new Subject(3, "Englisch", Lifecycle.ACTIVE);
	private static final GradingScale GRADING_SCALE = new GradingScale(5, "Test", 100, Lifecycle.ACTIVE);
	private static final CourseDraft COURSE = new CourseDraft(SchoolClass.CLS_8A, SUBJECT.id(), 2035,
			CoursePeriod.FULL_YEAR, GRADING_SCALE.id());

	@Mock
	private CourseRepository courses;

	@Mock
	private PupilRepository pupils;

	@Mock
	private SubjectRepository subjects;

	@Mock
	private GradingScaleRepository gradingScales;

	@Mock
	private CourseRosterWriter writer;

	private CourseRosterMcpTools tools;

	@BeforeEach
	void setUp() {
		when(subjects.findById(SUBJECT.id())).thenReturn(Optional.of(SUBJECT));
		when(gradingScales.findById(GRADING_SCALE.id())).thenReturn(Optional.of(GRADING_SCALE));
		when(courses.findByNaturalKey(any(), anyInt(), any(), any())).thenReturn(Optional.empty());
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of());
		tools = new CourseRosterMcpTools(courses, subjects, gradingScales, new PupilRosterConflictResolver(pupils),
				writer);
	}

	@Test
	void returnsStructuredConflictsWithoutWritingWhenClientHasNoElicitation() {
		final Pupil existing = new Pupil(42, "Anna", "Müller", Lifecycle.ACTIVE);
		when(pupils.findByExactName("Anna", "Müller")).thenReturn(List.of(existing));
		final McpSyncRequestContext context = contextWithoutElicitation();

		final CourseRosterResult result = tools.createCourseWithPupils(context, COURSE,
				List.of(new PupilDraft("row-1", "Anna", "Müller")), null);

		assertThat(result.status()).isEqualTo(CourseRosterStatus.NEEDS_RESOLUTION);
		assertThat(result.createdCourses()).isEmpty();
		assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
			assertThat(conflict.entryKey()).isEqualTo("row-1");
			assertThat(conflict.allowedActions()).containsExactly("REUSE", "CREATE", "SKIP");
			assertThat(conflict.existingPupils()).singleElement()
					.satisfies(candidate -> assertThat(candidate.id()).isEqualTo(42));
		});
		verifyNoInteractions(writer);
	}

	@Test
	void conversationalRetryReusesTheSelectedPupilAndCreatesEverythingOnce() {
		final Pupil existing = new Pupil(42, "Anna", "Müller", Lifecycle.ACTIVE);
		when(pupils.findByExactName("Anna", "Müller")).thenReturn(List.of(existing));
		when(writer.create(any(), anyList())).thenAnswer(invocation -> {
			final Course draft = invocation.getArgument(0);
			return new CreatedCourseRoster(withId(draft, 99), List.of(existing));
		});

		final CourseRosterResult result = tools.createCourseWithPupils(contextWithoutElicitation(), COURSE,
				List.of(new PupilDraft("row-1", "Anna", "Müller")),
				List.of(new PupilConflictResolution("row-1", PupilConflictAction.REUSE, 42)));

		assertThat(result.status()).isEqualTo(CourseRosterStatus.CREATED);
		assertThat(result.createdCourses()).singleElement().satisfies(course -> assertThat(course.id()).isEqualTo(99));
		assertThat(result.assignedPupils()).extracting(CourseRosterMcpTools.RosterPupilView::id).containsExactly(42);
		final ArgumentCaptor<List<ResolvedPupil>> pupilsCaptor = resolvedPupilsCaptor();
		verify(writer).create(any(), pupilsCaptor.capture());
		assertThat(pupilsCaptor.getValue()).containsExactly(new ResolvedPupil(42, "Anna", "Müller"));
	}

	@Test
	void usesNativeElicitationWhenTheClientAdvertisesIt() {
		final Pupil existing = new Pupil(42, "Anna", "Müller", Lifecycle.ACTIVE);
		when(pupils.findByExactName("Anna", "Müller")).thenReturn(List.of(existing));
		final McpSyncRequestContext context = mock(McpSyncRequestContext.class);
		when(context.elicitEnabled()).thenReturn(true);
		when(context.elicit(any(ElicitFormRequest.class)))
				.thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("action", "CREATE")));
		final Pupil newlyCreated = new Pupil(77, "Anna", "Müller", Lifecycle.ACTIVE);
		when(writer.create(any(), anyList())).thenAnswer(
				invocation -> new CreatedCourseRoster(withId(invocation.getArgument(0), 99), List.of(newlyCreated)));

		final CourseRosterResult result = tools.createCourseWithPupils(context, COURSE,
				List.of(new PupilDraft("row-1", "Anna", "Müller")), null);

		assertThat(result.status()).isEqualTo(CourseRosterStatus.CREATED);
		final ArgumentCaptor<List<ResolvedPupil>> pupilsCaptor = resolvedPupilsCaptor();
		verify(writer).create(any(), pupilsCaptor.capture());
		assertThat(pupilsCaptor.getValue()).containsExactly(new ResolvedPupil(null, "Anna", "Müller"));
	}

	@Test
	void cancellingNativeElicitationLeavesTheDatabaseUntouched() {
		when(pupils.findByExactName("Anna", "Müller"))
				.thenReturn(List.of(new Pupil(42, "Anna", "Müller", Lifecycle.ACTIVE)));
		final McpSyncRequestContext context = mock(McpSyncRequestContext.class);
		when(context.elicitEnabled()).thenReturn(true);
		when(context.elicit(any(ElicitFormRequest.class)))
				.thenReturn(new ElicitResult(ElicitResult.Action.CANCEL, null));

		final CourseRosterResult result = tools.createCourseWithPupils(context, COURSE,
				List.of(new PupilDraft("row-1", "Anna", "Müller")), null);

		assertThat(result.status()).isEqualTo(CourseRosterStatus.CANCELLED);
		verify(writer, never()).create(any(), anyList());
	}

	@Test
	void repeatedNamesWithinTheRosterNeedAnExplicitCreateOrSkipDecision() {
		when(pupils.findByExactName("Sam", "Taylor")).thenReturn(List.of());

		final CourseRosterResult result = tools.createCourseWithPupils(contextWithoutElicitation(), COURSE,
				List.of(new PupilDraft("row-1", "Sam", "Taylor"), new PupilDraft("row-2", "Sam", "Taylor")), null);

		assertThat(result.status()).isEqualTo(CourseRosterStatus.NEEDS_RESOLUTION);
		assertThat(result.conflicts()).hasSize(2).allSatisfy(conflict -> {
			assertThat(conflict.repeatedInRoster()).isTrue();
			assertThat(conflict.allowedActions()).containsExactly("CREATE", "SKIP");
			assertThat(conflict.existingPupils()).isEmpty();
		});
		verifyNoInteractions(writer);
	}

	private static McpSyncRequestContext contextWithoutElicitation() {
		return mock(McpSyncRequestContext.class);
	}

	private static Course withId(final Course course, final int id) {
		return new Course(id, course.schoolClass(), course.subject(), course.schoolYear(), course.coursePeriod(),
				course.lifecycle(), course.gradingScaleId());
	}

	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	private static ArgumentCaptor<List<ResolvedPupil>> resolvedPupilsCaptor() {
		return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
	}
}
