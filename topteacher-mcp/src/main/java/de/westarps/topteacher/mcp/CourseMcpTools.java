package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.model.Course;

/**
 * MCP operations for discovering TopTeacher courses.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CourseMcpTools {

	private final CourseRepository courses;

	public CourseMcpTools(final CourseRepository courses) {
		this.courses = Objects.requireNonNull(courses, "courses");
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

	static CourseView courseView(final Course course) {
		return new CourseView(course.id(), course.getDisplayName(), course.schoolClass().name(),
				course.schoolClass().getDisplayName(), course.subject().id(), course.subject().name(),
				course.schoolYear().getCalendarYear(), course.schoolYear().getDisplayName(),
				course.coursePeriod().name(), course.coursePeriod().getDisplayName(), course.lifecycle().name(),
				course.gradingScaleId());
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
}
