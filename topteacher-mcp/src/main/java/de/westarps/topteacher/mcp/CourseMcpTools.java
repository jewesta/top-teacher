package de.westarps.topteacher.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.Subject;

/**
 * MCP operations for discovering TopTeacher courses.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CourseMcpTools {

	private final CourseRepository courses;
	private final SubjectRepository subjects;
	private final GradingScaleRepository gradingScales;

	public CourseMcpTools(final CourseRepository courses, final SubjectRepository subjects,
			final GradingScaleRepository gradingScales) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.subjects = Objects.requireNonNull(subjects, "subjects");
		this.gradingScales = Objects.requireNonNull(gradingScales, "gradingScales");
	}

	@McpTool(name = "list_courses", title = "List TopTeacher courses",
			description = "List active TopTeacher courses, or all courses when includeArchived is true.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CourseListView listCourses(@McpToolParam(required = false,
			description = "Set to true to include archived courses. Defaults to false.") final Boolean includeArchived) {
		final List<Course> foundCourses = Boolean.TRUE.equals(includeArchived) ? courses.findAll()
				: courses.findActive();
		return new CourseListView(foundCourses.stream().map(CourseMcpTools::courseView).toList());
	}

	@McpTool(name = "list_course_pupils", title = "List pupils assigned to a course",
			description = "List the active pupils currently assigned to one TopTeacher course. Set includeArchived to true only when the user explicitly asks to include archived historical pupils.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CoursePupilListView listCoursePupils(
			@McpToolParam(description = "Course ID returned by list_courses.") final int courseId,
			@McpToolParam(required = false,
					description = "Set to true to include archived historical pupils. Defaults to false.") final Boolean includeArchived) {
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		final List<CoursePupilView> assignedPupils = courses.findPupils(courseId).stream()
				.filter(pupil -> Boolean.TRUE.equals(includeArchived) || pupil.lifecycle() == Lifecycle.ACTIVE)
				.map(CourseMcpTools::pupilView).toList();
		return new CoursePupilListView(courseView(course), assignedPupils);
	}

	@McpTool(name = "get_course_creation_options", title = "Get course creation options",
			description = "Get the valid school classes and course periods plus active, preconfigured subjects and grading scales required by create_course_with_pupils.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CourseCreationOptions getCourseCreationOptions() {
		return new CourseCreationOptions(
				Arrays.stream(SchoolClass.values()).map(value -> new Option(value.name(), value.getDisplayName()))
						.toList(),
				subjects.findActive().stream().map(CourseMcpTools::subjectView).toList(),
				gradingScales.findActive().stream().map(CourseMcpTools::gradingScaleView).toList(),
				Arrays.stream(CoursePeriod.values()).map(value -> new Option(value.name(), value.getDisplayName()))
						.toList());
	}

	static CourseView courseView(final Course course) {
		return new CourseView(course.id(), course.getDisplayName(), course.schoolClass().name(),
				course.schoolClass().getDisplayName(), course.subject().id(), course.subject().name(),
				course.schoolYear().getCalendarYear(), course.schoolYear().getDisplayName(),
				course.coursePeriod().name(), course.coursePeriod().getDisplayName(), course.lifecycle().name(),
				course.gradingScaleId());
	}

	private static SubjectOption subjectView(final Subject subject) {
		return new SubjectOption(subject.id(), subject.name());
	}

	private static GradingScaleOption gradingScaleView(final GradingScale gradingScale) {
		return new GradingScaleOption(gradingScale.id(), gradingScale.name(), gradingScale.maxPoints(),
				gradingScale.getDisplayName());
	}

	private static CoursePupilView pupilView(final Pupil pupil) {
		return new CoursePupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name());
	}

	public record CourseListView(List<CourseView> courses) {

		public CourseListView {
			courses = List.copyOf(Objects.requireNonNull(courses, "courses"));
		}
	}

	public record CourseView(int id, String displayName, String schoolClass, String schoolClassDisplayName,
			int subjectId, String subjectName, int schoolYear, String schoolYearDisplayName, String coursePeriod,
			String coursePeriodDisplayName, String lifecycle, int gradingScaleId) {
	}

	public record CoursePupilListView(CourseView course, List<CoursePupilView> pupils) {

		public CoursePupilListView {
			Objects.requireNonNull(course, "course");
			pupils = List.copyOf(Objects.requireNonNull(pupils, "pupils"));
		}
	}

	public record CoursePupilView(int id, String name, String surname, String lifecycle) {
	}

	public record CourseCreationOptions(List<Option> schoolClasses, List<SubjectOption> subjects,
			List<GradingScaleOption> gradingScales, List<Option> coursePeriods) {

		public CourseCreationOptions {
			schoolClasses = List.copyOf(Objects.requireNonNull(schoolClasses, "schoolClasses"));
			subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
			gradingScales = List.copyOf(Objects.requireNonNull(gradingScales, "gradingScales"));
			coursePeriods = List.copyOf(Objects.requireNonNull(coursePeriods, "coursePeriods"));
		}
	}

	public record Option(String value, String displayName) {
	}

	public record SubjectOption(int id, String name) {
	}

	public record GradingScaleOption(int id, String name, int maxPoints, String displayName) {
	}
}
