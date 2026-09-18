package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@ExtendWith(MockitoExtension.class)
class CourseMcpToolsTests {

	@Mock
	private CourseRepository courses;

	private CourseMcpTools tools;

	@BeforeEach
	void setUp() {
		tools = new CourseMcpTools(courses, mock(SubjectRepository.class), mock(GradingScaleRepository.class));
	}

	@Test
	void listsActiveCoursePupilsByDefaultAndArchivedPupilsOnExplicitRequest() {
		final Course course = course();
		final Pupil active = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		final Pupil archived = new Pupil(8, "Grace", "Hopper", Lifecycle.INACTIVE);
		when(courses.findById(3)).thenReturn(Optional.of(course));
		when(courses.findPupils(3)).thenReturn(List.of(active, archived));

		final var activeResult = tools.listCoursePupils(3, null);
		final var allResult = tools.listCoursePupils(3, true);

		assertThat(activeResult.course().id()).isEqualTo(3);
		assertThat(activeResult.pupils()).singleElement().satisfies(pupil -> {
			assertThat(pupil.id()).isEqualTo(7);
			assertThat(pupil.lifecycle()).isEqualTo("ACTIVE");
		});
		assertThat(allResult.pupils()).extracting(CourseMcpTools.CoursePupilView::id).containsExactly(7, 8);
	}

	@Test
	void rejectsAnUnknownCourseWithoutLookingUpAssignments() {
		when(courses.findById(404)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> tools.listCoursePupils(404, null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Course does not exist");
		verify(courses, never()).findPupils(404);
	}

	private static Course course() {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 1);
	}
}
