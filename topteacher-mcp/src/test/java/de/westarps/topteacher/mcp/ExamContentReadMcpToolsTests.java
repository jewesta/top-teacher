package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

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
import de.westarps.topteacher.model.loe.LoeCategoryOutline;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImageMetadata;
import de.westarps.topteacher.model.loe.LoeMaterialOutline;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeTaskOutline;

class ExamContentReadMcpToolsTests extends FullMcpAccessTest {

	private final CourseRepository courses = mock(CourseRepository.class);
	private final ExamRepository exams = mock(ExamRepository.class);
	private final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
	private final LevelOfExpectationsRepository design = mock(LevelOfExpectationsRepository.class);
	private final ExamDesignGuard guard = mock(ExamDesignGuard.class);
	private final ExamDesignMcpSupport support = new ExamDesignMcpSupport(courses, exams, gradingScales, design, guard);
	private final ExamContentReadMcpTools tools = new ExamContentReadMcpTools(support, design);
	private final LoeTask task = new LoeTask(13, 12, "Erste Aufgabe", "**Aufgabe**\n\nOriginal Markdown", 5, 0);

	@BeforeEach
	void setUpReadContext() {
		when(exams.findById(7)).thenReturn(Optional.of(new Exam(7, 3, "Klausur", LocalDate.of(2026, 9, 18))));
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.ACTIVE)));
		when(guard.revision(7)).thenReturn(4L);
		doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get()).when(guard).read(anyInt(), any());
		when(design.findTaskByExamIdAndId(7, 13)).thenReturn(Optional.of(task));
		when(design.findExamFooterMarkdown(7)).thenReturn("Viel Erfolg!");
	}

	@Test
	void taskReadReturnsOnlySelectedOriginalContentWithinConsistencyBoundary() {
		final AtomicBoolean reading = new AtomicBoolean();
		doAnswer(invocation -> {
			reading.set(true);
			try {
				return ((Supplier<?>) invocation.getArgument(1)).get();
			} finally {
				reading.set(false);
			}
		}).when(guard).read(anyInt(), any());
		when(design.findTaskByExamIdAndId(7, 13)).thenAnswer(invocation -> {
			assertThat(reading).isTrue();
			return Optional.of(task);
		});

		final var result = tools.getTask(7, 13);

		assertThat(result.examId()).isEqualTo(7);
		assertThat(result.revision()).isEqualTo(4);
		assertThat(result.task().categoryId()).isEqualTo(12);
		assertThat(result.task().descriptionMarkdown()).isEqualTo(task.descriptionMarkdown());
		assertThat(result.task().maxPoints()).isEqualTo(5);
		assertThat(result.permissions().canEditExamContent()).isTrue();
		verify(design, never()).findTasksByExamId(anyInt());
		verify(design, never()).findMaterialsByExamId(anyInt());
		verify(design, never()).findRequirementsByExamId(anyInt());
		verify(design, never()).findMaterialImageMetadataByIds(any());
		verify(design, never()).findExamFooterMarkdown(anyInt());
		verify(exams, never()).findPupils(anyInt());
		verifyNoInteractions(gradingScales);
	}

	@Test
	void materialReadLoadsOnlyItsReferencedImageMetadata() {
		final String markdown = "![Diagramm](img:7q3m6r9t)\n![Foto](img:4n8k2p7c?width=75)";
		when(design.findMaterialByExamIdAndId(7, 15)).thenReturn(
				Optional.of(new LoeMaterial(15, 11, "Quelle", markdown, 0)));
		when(design.findMaterialImageMetadataByIds(Set.of("7q3m6r9t", "4n8k2p7c"))).thenReturn(List.of(
				new LoeMaterialImageMetadata("7q3m6r9t", "image/jpeg", 200, 150, 800),
				new LoeMaterialImageMetadata("4n8k2p7c", "image/png", 100, 50, 128)));

		final var result = tools.getMaterial(7, 15);

		assertThat(result.material().partId()).isEqualTo(11);
		assertThat(result.material().descriptionMarkdown()).isEqualTo(markdown);
		assertThat(result.material().images()).extracting(image -> image.imageId())
				.containsExactly("4n8k2p7c", "7q3m6r9t");
		assertThat(result.material().images().getFirst().byteSize()).isEqualTo(128);
		verify(design).findMaterialImageMetadataByIds(Set.of("7q3m6r9t", "4n8k2p7c"));
		verify(design, never()).findMaterialImagesByIds(any());
		verify(design, never()).findMaterialsByExamId(anyInt());
		verify(design, never()).findTasksByExamId(anyInt());
		verify(design, never()).findRequirementsByExamId(anyInt());
	}

	@Test
	void materialWithoutImageReferencesDoesNotQueryImageStorage() {
		when(design.findMaterialByExamIdAndId(7, 15)).thenReturn(
				Optional.of(new LoeMaterial(15, 11, "Quelle", "Nur Text", 0)));

		assertThat(tools.getMaterial(7, 15).material().images()).isEmpty();

		verify(design, never()).findMaterialImageMetadataByIds(any());
		verify(design, never()).findMaterialImagesByIds(any());
	}

	@Test
	void foreignOrMissingNodeIdsAreRejectedWithinTheRequestedExam() {
		when(design.findTaskByExamIdAndId(7, 99)).thenReturn(Optional.empty());
		when(design.findMaterialByExamIdAndId(7, 99)).thenReturn(Optional.empty());

		assertThatIllegalArgumentException().isThrownBy(() -> tools.getTask(7, 99))
				.withMessage("Task does not exist in exam 7: 99");
		assertThatIllegalArgumentException().isThrownBy(() -> tools.getTaskRequirements(7, 99))
				.withMessage("Task does not exist in exam 7: 99");
		assertThatIllegalArgumentException().isThrownBy(() -> tools.getMaterial(7, 99))
				.withMessage("Material does not exist in exam 7: 99");
		verify(design, never()).findRequirementsByExamIdAndTaskId(anyInt(), anyInt());
		verify(design, never()).findActiveCriteriaByExamIdAndTaskId(anyInt(), anyInt());
		verify(design, never()).findMaterialImageMetadataByIds(any());
	}

	@Test
	void footerReadHandlesEmptyTextWithoutLoadingOtherContent() {
		when(design.findExamFooterMarkdown(7)).thenReturn(null);

		final var result = tools.getExamFooter(7);

		assertThat(result.footerMarkdown()).isEmpty();
		assertThat(result.revision()).isEqualTo(4);
		verify(design, never()).findPartsByExamId(anyInt());
		verify(design, never()).findMaterialsByExamId(anyInt());
		verify(design, never()).findTasksByExamId(anyInt());
		verify(design, never()).findRequirementsByExamId(anyInt());
		verifyNoInteractions(gradingScales);
	}

	@Test
	void requirementsAreOrderedAndExcludePupilFacingTaskMarkdown() throws Exception {
		when(design.findRequirementsByExamIdAndTaskId(7, 13)).thenReturn(List.of(
				new LoeRequirement(22, 13, "Zusatz", 1, true, 1),
				new LoeRequirement(21, 13, "[Beleg](crit:1?points=2.5)", 5, false, 0)));
		when(design.findActiveCriteriaByExamIdAndTaskId(7, 13)).thenReturn(List.of(
				new LoeCriterion(31, 21, "2", "Erklärung", 5, 1, true),
				new LoeCriterion(30, 21, "1", "Beleg", 5, 0, true)));

		final var result = tools.getTaskRequirements(7, 13);

		assertThat(result.task().id()).isEqualTo(13);
		assertThat(result.task().maxPoints()).isEqualTo(5);
		assertThat(result.requirements()).extracting(requirement -> requirement.id()).containsExactly(21, 22);
		assertThat(result.requirements().getFirst().criteria()).extracting(criterion -> criterion.id())
				.containsExactly(30, 31);
		assertThat(result.requirements().getFirst().criteria().getFirst().pointUnits()).isEqualTo(5);
		assertThat(result.requirements().getLast().bonus()).isTrue();
		assertThat(JsonMapper.builder().build().writeValueAsString(result)).doesNotContain("Original Markdown");
		verify(design, never()).findRequirementsByExamId(anyInt());
		verify(design, never()).findActiveCriteriaByExamId(anyInt());
		verify(design, never()).findTasksByExamId(anyInt());
		verify(design, never()).findMaterialImagesByIds(any());
	}

	@Test
	void outlineOrdersEachSiblingListAndDoesNotLoadTaskMaterialMarkdownOrAssessment() throws Exception {
		when(design.findPartsByExamId(7)).thenReturn(List.of(new LoePart(19, 7, "Teil B", 1),
				new LoePart(11, 7, "Teil A", 0)));
		when(design.findCategoryOutlinesByExamId(7)).thenReturn(List.of(
				new LoeCategoryOutline(18, 19, "B Inhalt", 0),
				new LoeCategoryOutline(17, 11, "Sprache", 1),
				new LoeCategoryOutline(12, 11, "Inhalt", 0)));
		when(design.findTaskOutlinesByExamId(7)).thenReturn(List.of(
				new LoeTaskOutline(14, 12, "Zweite", 3, 0),
				new LoeTaskOutline(13, 12, "Erste", 5, 0),
				new LoeTaskOutline(20, 18, "B Aufgabe", 2, 0)));
		when(design.findMaterialOutlinesByExamId(7)).thenReturn(List.of(
				new LoeMaterialOutline(16, 11, "Quelle 2", 1),
				new LoeMaterialOutline(15, 11, "Quelle 1", 0)));

		final var result = tools.getExamStructure(7);

		assertThat(result.exam().title()).isEqualTo("Klausur");
		assertThat(result.closingTextPresent()).isTrue();
		assertThat(result.parts()).extracting(part -> part.id()).containsExactly(11, 19);
		assertThat(result.parts().getFirst().materials()).extracting(material -> material.id()).containsExactly(15, 16);
		assertThat(result.parts().getFirst().categories()).extracting(category -> category.id()).containsExactly(12, 17);
		assertThat(result.parts().getFirst().categories().getFirst().tasks()).extracting(task -> task.id())
				.containsExactly(13, 14);
		assertThat(result.parts().getLast().categories().getFirst().tasks()).extracting(task -> task.id())
				.containsExactly(20);
		assertThat(JsonMapper.builder().build().writeValueAsString(result))
				.doesNotContain("descriptionMarkdown", "footerMarkdown", "Viel Erfolg!", "requirements", "images", "pupils");
		verify(design, never()).findCategoriesByExamId(anyInt());
		verify(design, never()).findTasksByExamId(anyInt());
		verify(design, never()).findMaterialsByExamId(anyInt());
		verify(design, never()).findRequirementsByExamId(anyInt());
		verify(design, never()).findActiveCriteriaByExamId(anyInt());
		verify(design, never()).findMaterialImageMetadataByIds(any());
		verify(design, never()).findMaterialImagesByIds(any());
		verify(exams, never()).findPupils(anyInt());
		verifyNoInteractions(gradingScales);
	}

	@Test
	void sharedContentIsIdenticalForPseudonymizedAccessAndArchivedCoursesStayReadOnly() {
		when(courses.findById(3)).thenReturn(Optional.of(course(Lifecycle.INACTIVE)));
		final var full = tools.getTask(7, 13);

		assertThat(full.permissions().archivedCourse()).isTrue();
		assertThat(full.permissions().canEditExamContent()).isFalse();
		try (McpAccessContext.Scope ignored = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
			assertThat(tools.getTask(7, 13)).isEqualTo(full);
		}
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 5);
	}
}
