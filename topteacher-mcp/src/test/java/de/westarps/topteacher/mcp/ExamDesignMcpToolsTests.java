package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.ExamContentCommand;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImageMetadata;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeTask;

class ExamDesignMcpToolsTests extends FullMcpAccessTest {

	private final CourseRepository courses = mock(CourseRepository.class);
	private final ExamRepository exams = mock(ExamRepository.class);
	private final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
	private final LevelOfExpectationsRepository design = mock(LevelOfExpectationsRepository.class);
	private final ExamDesignGuard guard = mock(ExamDesignGuard.class);
	private final ExamAuthoringService authoring = mock(ExamAuthoringService.class);
	private final ExamDesignMcpSupport support = new ExamDesignMcpSupport(courses, exams, gradingScales, design, guard);
	private final ExamDesignMcpTools tools = new ExamDesignMcpTools(support, authoring, courses, gradingScales);

	@BeforeEach
	void setUpDesign() {
		when(exams.findById(7)).thenReturn(Optional.of(new Exam(7, 3, "Klausur", LocalDate.of(2026, 9, 18))));
		when(courses.findById(3)).thenReturn(Optional.of(new Course(3, SchoolClass.CLS_Q1,
				new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026), CoursePeriod.FULL_YEAR,
				Lifecycle.ACTIVE, 5)));
		when(design.findExamFooterMarkdown(7)).thenReturn("Viel Erfolg!");
		when(design.findPartsByExamId(7)).thenReturn(List.of(new LoePart(11, 7, "Teil A", 0)));
		when(design.findCategoriesByExamId(7)).thenReturn(List.of(new LoeCategory(12, 11, "Inhalt", "Beschreibung", 0)));
		when(design.findTasksByExamId(7)).thenReturn(List.of(new LoeTask(14, 12, "Zweite", "B", 3, 1),
				new LoeTask(13, 12, "Erste", "A", 5, 0)));
		when(design.findMaterialsByExamId(7)).thenReturn(List.of(new LoeMaterial(15, 11, "Diagramm",
				"![Diagramm](img:4n8k2p7c?width=75)", 0)));
		when(design.findMaterialImageMetadataByIds(Set.of("4n8k2p7c"))).thenReturn(
				List.of(new LoeMaterialImageMetadata("4n8k2p7c", "image/png", 100, 50, 128)));
		when(guard.revision(7)).thenReturn(4L);
		doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get()).when(guard).read(anyInt(), any());
	}

	@Test
	void readsOrderedPupilContentFooterAndMetadataWithoutLoadingImagesOrRosters() {
		final var result = tools.getExam(7);
		assertThat(result.footerMarkdown()).isEqualTo("Viel Erfolg!");
		assertThat(result.revision()).isEqualTo(4);
		assertThat(result.parts()).singleElement().satisfies(part -> {
			assertThat(part.categories().getFirst().tasks()).extracting(task -> task.id()).containsExactly(13, 14);
			assertThat(part.materials()).singleElement().satisfies(material -> {
				assertThat(material.images()).singleElement().satisfies(image -> {
					assertThat(image.imageId()).isEqualTo("4n8k2p7c");
					assertThat(image.byteSize()).isEqualTo(128);
				});
			});
		});
		verify(design, never()).findMaterialImagesByIds(any());
		verify(exams, never()).findPupils(anyInt());
	}

	@Test
	void sharedExamContentIsIdenticalUnderPseudonymizedAccess() {
		final var full = tools.getExam(7);
		try (McpAccessContext.Scope ignored = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
			assertThat(tools.getExam(7)).isEqualTo(full);
		}
	}

	@Test
	void reportsMissingImagesAndIncompleteAssessmentWithoutBlockingReads() {
		when(design.findMaterialImageMetadataByIds(any())).thenReturn(List.of());
		assertThat(tools.getExam(7).diagnostics()).extracting(finding -> finding.code())
				.contains("MISSING_IMAGE", "MISSING_GRADING_SCALE", "TASK_POINTS");
	}

	@Test
	void optionsOfferActiveGradingScalesAndContentCommands() {
		when(gradingScales.findActive()).thenReturn(List.of(new GradingScale(5, "20 Punkte", 20, Lifecycle.ACTIVE)));
		final var options = tools.getExamAuthoringOptions(3);
		assertThat(options.defaultGradingScaleId()).isEqualTo(5);
		assertThat(options.gradingScales()).extracting(scale -> scale.id()).containsExactly(5);
		assertThat(options.contentCommandTypes()).contains("CREATE_PART", "SET_FOOTER", "REORDER_TASKS");
	}

	@Test
	void delegatesContentOnlyBatchWithoutImplicitRequirements() {
		final List<ExamContentCommand> commands = List.of(new ExamContentCommand(ExamContentCommand.Type.SET_FOOTER,
				null, null, null, null, null, "", null, null, null));
		tools.applyExamContentChanges(7, 4, "content-key", commands, null);
		verify(authoring).applyContentChanges(7, 4, "content-key", commands, List.of());
	}
}
