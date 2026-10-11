package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.backup.DatabaseBackupService;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;

/** Direct adapters must reject missing authentication before any lookup or write. */
class McpAccessBoundaryTests {

	@Test
	void missingContextCannotReachTheExamAssignmentWriter() {
		final ExamPupilWriter writer = mock(ExamPupilWriter.class);

		assertThatThrownBy(() -> new ExamPupilAssignmentMcpTools(writer).assignPupilsToExam(11, List.of(7)))
				.isInstanceOf(SecurityException.class).hasMessageContaining("authenticated");

		verifyNoInteractions(writer);
	}

	@Test
	void missingContextCannotReadEmptyDiscoveryResultsOrCreateBackups() {
		final CourseRepository courses = mock(CourseRepository.class);
		final SubjectRepository subjects = mock(SubjectRepository.class);
		final GradingScaleRepository scales = mock(GradingScaleRepository.class);
		final ExamRepository exams = mock(ExamRepository.class);
		final LevelOfExpectationsRepository design = mock(LevelOfExpectationsRepository.class);
		final DatabaseBackupService backups = mock(DatabaseBackupService.class);
		final CourseMcpTools courseTools = new CourseMcpTools(courses, subjects, scales);
		final ExamMcpTools examTools = new ExamMcpTools(courses, exams, design);

		assertThatThrownBy(() -> courseTools.listCourses(null)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> courseTools.listCoursePupils(3, null)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(courseTools::getCourseCreationOptions).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> examTools.listExams(3)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> examTools.listExamPupils(11)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> new DatabaseBackupMcpTools(backups).createDatabaseBackup())
				.isInstanceOf(SecurityException.class);

		verifyNoInteractions(courses, subjects, scales, exams, design, backups);
	}

	@Test
	void missingContextCannotReachRemovalLookupsOrWriters() {
		final CourseRepository courses = mock(CourseRepository.class);
		final ExamRepository exams = mock(ExamRepository.class);
		final CoursePupilRemovalWriter courseWriter = mock(CoursePupilRemovalWriter.class);
		final ExamPupilWriter examWriter = mock(ExamPupilWriter.class);

		assertThatThrownBy(() -> new CoursePupilRemovalMcpTools(courses, courseWriter)
				.removePupilsFromCourse(null, 3, List.of(7), null)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> new ExamPupilRemovalMcpTools(courses, exams, examWriter)
				.removePupilsFromExam(null, 11, List.of(7), null)).isInstanceOf(SecurityException.class);

		verifyNoInteractions(courses, exams, courseWriter, examWriter);
	}
}
