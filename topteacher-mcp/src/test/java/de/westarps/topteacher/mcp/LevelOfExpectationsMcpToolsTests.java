package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.authoring.ExamAuthoringService;
import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.LevelOfExpectationsCommand;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;

class LevelOfExpectationsMcpToolsTests extends FullMcpAccessTest {

	private final CourseRepository courses = mock(CourseRepository.class);
	private final ExamRepository exams = mock(ExamRepository.class);
	private final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
	private final LevelOfExpectationsRepository design = mock(LevelOfExpectationsRepository.class);
	private final ExamDesignGuard guard = mock(ExamDesignGuard.class);
	private final ExamAuthoringService authoring = mock(ExamAuthoringService.class);
	private final ExamDesignMcpSupport support = new ExamDesignMcpSupport(courses, exams, gradingScales, design, guard);
	private final LevelOfExpectationsMcpTools tools = new LevelOfExpectationsMcpTools(support, authoring);

	@BeforeEach
	void setUpDesign() {
		when(exams.findById(7)).thenReturn(Optional.of(new Exam(7, 3, "Klausur", LocalDate.of(2026, 9, 18))));
		when(courses.findById(3)).thenReturn(Optional.of(new Course(3, SchoolClass.CLS_Q1,
				new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026), CoursePeriod.FULL_YEAR,
				Lifecycle.ACTIVE, 5)));
		when(design.findPartsByExamId(7)).thenReturn(List.of(new LoePart(11, 7, "Teil A", 0)));
		when(design.findCategoriesByExamId(7)).thenReturn(List.of(new LoeCategory(12, 11, "Inhalt", "Beschreibung", 0)));
		when(design.findTasksByExamId(7)).thenReturn(List.of(new LoeTask(13, 12, "Aufgabe 1", "Aufgabentext", 5, 0)));
		when(guard.revision(7)).thenReturn(4L);
		doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get()).when(guard).read(anyInt(), any());
	}

	@Test
	void returnsRequirementsOnlyAgainstExistingTaskIdsWithReadOnlyContext() {
		when(design.findRequirementsByExamId(7)).thenReturn(List.of(new LoeRequirement(14, 13,
				"Erreicht [das Ziel](crit:1?points=5).", 5, false, 0)));
		when(design.findActiveCriteriaByExamId(7)).thenReturn(List.of(new LoeCriterion(16, 14, "1", "Ziel", 10, 0, true)));

		final var result = tools.getLevelOfExpectations(7);

		assertThat(result.examId()).isEqualTo(7);
		assertThat(result.revision()).isEqualTo(4);
		assertThat(result.points().requirementsMatchTasks()).isTrue();
		assertThat(result.tasks()).singleElement().satisfies(task -> {
			assertThat(task.taskId()).isEqualTo(13);
			assertThat(task.partId()).isEqualTo(11);
			assertThat(task.requirements()).singleElement().satisfies(requirement -> {
				assertThat(requirement.id()).isEqualTo(14);
				assertThat(requirement.criteria().getFirst().pointUnits()).isEqualTo(10);
			});
		});
	}

	@Test
	void exposesIncompleteAllocationsAndFirstResultCorrectionPermissions() {
		when(design.hasResultsForExam(7)).thenReturn(true);

		final var result = tools.getLevelOfExpectations(7);

		assertThat(result.correctionMode()).isTrue();
		assertThat(result.permissions().canEditExamContent()).isFalse();
		assertThat(result.permissions().canEditLevelOfExpectationsStructure()).isFalse();
		assertThat(result.permissions().canCorrectRequirementText()).isTrue();
		assertThat(result.points().requirementsMatchTasks()).isFalse();
		assertThat(result.diagnostics()).anySatisfy(finding -> {
			assertThat(finding.code()).isEqualTo("TASK_POINTS");
			assertThat(finding.nodeId()).isEqualTo(13);
		});
	}

	@Test
	void delegatesTypedRequirementChangesWithRevisionAndRetryKeyInBothModes() {
		final List<LevelOfExpectationsCommand> commands = List.of(new LevelOfExpectationsCommand(
				LevelOfExpectationsCommand.Type.CREATE_REQUIREMENT, null, "requirement", 13, null, "Text", 5,
				false, null, null));
		try (McpAccessContext.Scope ignored = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
			tools.applyLevelOfExpectationsChanges(7, 4, "eh-key", commands);
		}
		verify(authoring).applyLevelOfExpectationsChanges(7, 4, "eh-key", commands);
	}
}
