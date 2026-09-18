package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.CourseMcpTools.CourseView;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.ExamNumber;
import de.westarps.topteacher.model.Pupil;

/**
 * MCP operations for discovering exams and their assigned pupils.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamMcpTools {

	private final CourseRepository courses;
	private final ExamRepository exams;
	private final LevelOfExpectationsRepository levelOfExpectations;

	public ExamMcpTools(final CourseRepository courses, final ExamRepository exams,
			final LevelOfExpectationsRepository levelOfExpectations) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.exams = Objects.requireNonNull(exams, "exams");
		this.levelOfExpectations = Objects.requireNonNull(levelOfExpectations, "levelOfExpectations");
	}

	@McpTool(name = "list_exams", title = "List exams for a course",
			description = "List the exams belonging to one TopTeacher course, including their stable IDs and state.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamListView listExams(
			@McpToolParam(description = "Course ID returned by list_courses.") final int courseId) {
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		final Map<Integer, ExamNumber> numbers = exams.findNumbersByCourseId(courseId);
		final List<ExamView> examViews = exams.findByCourseId(courseId).stream()
				.map(exam -> examView(exam, numbers.get(exam.id()), levelOfExpectations.hasResultsForExam(exam.id())))
				.toList();
		return new ExamListView(CourseMcpTools.courseView(course), examViews);
	}

	@McpTool(name = "list_exam_pupils", title = "List pupils assigned to an exam",
			description = "List the pupils assigned to one exam so their stable IDs can be used for result retrieval.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamPupilListView listExamPupils(
			@McpToolParam(description = "Exam ID returned by list_exams.") final int examId) {
		exams.findById(examId).orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		return new ExamPupilListView(examId, exams.findPupils(examId).stream().map(ExamMcpTools::pupilView).toList());
	}

	static ExamView examView(final Exam exam, final ExamNumber number, final boolean hasResults) {
		return new ExamView(exam.id(), exam.courseId(), exam.title(), exam.date().toString(),
				number == null ? 0 : number.number(), number != null && number.makeupExam(), exam.originalExamId(),
				exam.gradingScaleId(), hasResults);
	}

	static PupilView pupilView(final Pupil pupil) {
		return new PupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name());
	}

	public record ExamListView(CourseView course, List<ExamView> exams) {

		public ExamListView {
			Objects.requireNonNull(course, "course");
			exams = List.copyOf(Objects.requireNonNull(exams, "exams"));
		}
	}

	public record ExamView(int id, int courseId, String title, String date, int number, boolean makeupExam,
			Integer originalExamId, Integer gradingScaleId, boolean hasResults) {
	}

	public record ExamPupilListView(int examId, List<PupilView> pupils) {

		public ExamPupilListView {
			pupils = List.copyOf(Objects.requireNonNull(pupils, "pupils"));
		}
	}

	public record PupilView(int id, String name, String surname, String lifecycle) {
	}
}
