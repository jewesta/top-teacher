package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

import tools.jackson.databind.json.JsonMapper;

import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderedPng;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.ExamFooterView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.MaterialContentView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskContentView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskRequirementsContentView;
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
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageMetadata;
import de.westarps.topteacher.model.loe.LoeMaterialOutline;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeTaskOutline;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

class ExamContentReadMcpToolsTests extends FullMcpAccessTest {

	private final CourseRepository courses = mock(CourseRepository.class);
	private final ExamRepository exams = mock(ExamRepository.class);
	private final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
	private final LevelOfExpectationsRepository design = mock(LevelOfExpectationsRepository.class);
	private final ExamDesignGuard guard = mock(ExamDesignGuard.class);
	private final FaithfulMarkdownRenderer renderer = mock(FaithfulMarkdownRenderer.class);
	private final ExamDesignMcpSupport support = new ExamDesignMcpSupport(courses, exams, gradingScales, design, guard);
	private final ExamContentReadMcpTools tools = new ExamContentReadMcpTools(support, design, renderer);
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

	@ParameterizedTest
	@NullSource
	@ValueSource(booleans = false)
	void nullAndFalseFlagsKeepAllFocusedReadsTextOnly(final Boolean includeFaithfulRender) {
		when(design.findMaterialByExamIdAndId(7, 15))
				.thenReturn(Optional.of(new LoeMaterial(15, 11, "Quelle", "![Bild](img:7q3m6r9t)", 0)));
		final var results = List.of(tools.getTask(7, 13, includeFaithfulRender), tools.getMaterial(7, 15, includeFaithfulRender),
				tools.getExamFooter(7, includeFaithfulRender), tools.getTaskRequirements(7, 13, includeFaithfulRender));

		assertThat(results).allSatisfy(result -> {
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).singleElement().isInstanceOfSatisfying(TextContent.class,
					text -> assertThat(text.text()).doesNotContain("faithfulRender"));
			assertThat(JsonMapper.builder().build().writeValueAsString(result.structuredContent()))
					.doesNotContain("faithfulRender");
		});
		verifyNoInteractions(renderer);
		verify(design, never()).findMaterialImagesByIds(any());
	}

	@Test
	void omittedFlagIsOptionalThroughTheMcpInvocationPath() {
		final var specification = new SyncMcpToolProvider(List.of(tools)).getToolSpecifications().stream()
				.filter(spec -> spec.tool().name().equals("get_task")).findFirst().orElseThrow();

		final var result = specification.callHandler().apply(null,
				new CallToolRequest("get_task", Map.of("examId", 7, "taskId", 13), null));

		assertThat(result.isError()).isFalse();
		assertThat(result.structuredContent()).isEqualTo(tools.getTask(7, 13));
		verifyNoInteractions(renderer);
	}

	@ParameterizedTest
	@EnumSource(McpAccessLevel.class)
	void taskRenderRunsAfterTheReadGuardAndReturnsNativePngForBothAccessModes(final McpAccessLevel access) throws Exception {
		final AtomicBoolean reading = new AtomicBoolean();
		doAnswer(invocation -> {
			reading.set(true);
			try {
				return ((Supplier<?>) invocation.getArgument(1)).get();
			} finally {
				reading.set(false);
			}
		}).when(guard).read(anyInt(), any());
		final byte[] bytes = png();
		when(renderer.render(any())).thenAnswer(invocation -> {
			assertThat(reading).isFalse();
			final var request = (FaithfulMarkdownRenderer.RenderRequest) invocation.getArgument(0);
			assertThat(request.sections()).singleElement().satisfies(section -> {
				assertThat(section.markdown()).isEqualTo(task.descriptionMarkdown());
				assertThat(section.variant()).isEqualTo(Variant.STANDARD);
			});
			assertThat(request.images()).isEmpty();
			return new RenderedPng(bytes, 2, 3);
		});
		try (McpAccessContext.Scope ignored = McpAccessContext.open(access)) {
			final var result = tools.getTask(7, 13, true);
			final TaskContentView view = (TaskContentView) result.structuredContent();

			assertThat(view.task()).isEqualTo(tools.getTask(7, 13).task());
			assertThat(view.revision()).isEqualTo(4);
			assertThat(view.faithfulRender().status()).isEqualTo("rendered");
			assertThat(view.faithfulRender().mimeType()).isEqualTo("image/png");
			assertThat(view.faithfulRender().byteSize()).isEqualTo((long) bytes.length);
			assertThat(result.content()).hasSize(2);
			final TextContent text = (TextContent) result.content().getFirst();
			assertThat(text.text()).isEqualTo(JsonMapper.builder().build().writeValueAsString(view));
			final ImageContent image = (ImageContent) result.content().getLast();
			assertThat(image.mimeType()).isEqualTo("image/png");
			final BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image.data())));
			assertThat(decoded.getWidth()).isEqualTo(2);
			assertThat(decoded.getHeight()).isEqualTo(3);
		}
		verify(design, never()).findMaterialImagesByIds(any());
		verify(exams, never()).findPupils(anyInt());
	}

	@Test
	void materialRenderLoadsOnlyReferencedPixelsWithinTheReadSnapshot() throws Exception {
		final AtomicBoolean reading = new AtomicBoolean();
		doAnswer(invocation -> {
			reading.set(true);
			try {
				return ((Supplier<?>) invocation.getArgument(1)).get();
			} finally {
				reading.set(false);
			}
		}).when(guard).read(anyInt(), any());
		final String markdown = "![Bild](img:7q3m6r9t)";
		final byte[] bytes = png();
		final LoeMaterialImage stored = new LoeMaterialImage("7q3m6r9t", "a".repeat(64), "image/png", 2, 3, bytes.length, bytes);
		when(design.findMaterialByExamIdAndId(7, 15)).thenReturn(Optional.of(new LoeMaterial(15, 11, "Quelle", markdown, 0)));
		when(design.findMaterialImagesByIds(Set.of("7q3m6r9t"))).thenAnswer(invocation -> {
			assertThat(reading).isTrue();
			return List.of(stored);
		});
		when(renderer.render(any())).thenAnswer(invocation -> {
			assertThat(reading).isFalse();
			final var request = (FaithfulMarkdownRenderer.RenderRequest) invocation.getArgument(0);
			assertThat(request.sections()).singleElement().satisfies(section -> {
				assertThat(section.markdown()).isEqualTo(markdown);
				assertThat(section.variant()).isEqualTo(Variant.MATERIAL);
			});
			assertThat(request.images()).containsExactly(stored);
			return new RenderedPng(bytes, 2, 3);
		});

		final MaterialContentView view = (MaterialContentView) tools.getMaterial(7, 15, true).structuredContent();

		assertThat(view.material().descriptionMarkdown()).isEqualTo(markdown);
		assertThat(view.faithfulRender().status()).isEqualTo("rendered");
		verify(design).findMaterialImagesByIds(Set.of("7q3m6r9t"));
		verify(design, never()).findMaterialsByExamId(anyInt());
	}

	@Test
	void footerAndRequirementsRenderOnlyTheirSelectedCanonicalMarkdown() throws Exception {
		when(design.findRequirementsByExamIdAndTaskId(7, 13)).thenReturn(List.of(
				new LoeRequirement(22, 13, "Zusatz", 1, true, 1),
				new LoeRequirement(21, 13, "[Beleg](crit:1?points=2.5)", 5, false, 0)));
		when(renderer.render(any())).thenAnswer(invocation -> {
			final var request = (FaithfulMarkdownRenderer.RenderRequest) invocation.getArgument(0);
			assertThat(request.images()).isEmpty();
			assertThat(request.sections()).allSatisfy(section -> assertThat(section.markdown()).doesNotContain("Original Markdown"));
			return new RenderedPng(png(), 2, 3);
		});

		final ExamFooterView footer = (ExamFooterView) tools.getExamFooter(7, true).structuredContent();
		final TaskRequirementsContentView requirements = (TaskRequirementsContentView) tools.getTaskRequirements(7, 13, true).structuredContent();

		assertThat(footer.footerMarkdown()).isEqualTo("Viel Erfolg!");
		assertThat(footer.faithfulRender().status()).isEqualTo("rendered");
		assertThat(requirements.requirements()).extracting(requirement -> requirement.descriptionMarkdown())
				.containsExactly("[Beleg](crit:1?points=2.5)", "Zusatz");
		assertThat(requirements.faithfulRender().status()).isEqualTo("rendered");
		verify(renderer).render(new FaithfulMarkdownRenderer.RenderRequest(List.of(
				new FaithfulMarkdownRenderer.Section("", "[Beleg](crit:1?points=2.5)", Variant.REQUIREMENT),
				new FaithfulMarkdownRenderer.Section("", "Zusatz", Variant.REQUIREMENT)), List.of()));
		verify(design, never()).findMaterialImagesByIds(any());
		verify(exams, never()).findPupils(anyInt());
	}

	@Test
	void renderFailureRetainsMarkdownAndUsesASafeMessageWithoutNativeImage() {
		when(renderer.render(any())).thenThrow(new IllegalStateException("Browser failure at /private/internal/session"));

		final var result = tools.getTask(7, 13, true);
		final TaskContentView view = (TaskContentView) result.structuredContent();

		assertThat(result.isError()).isFalse();
		assertThat(view.task().descriptionMarkdown()).isEqualTo(task.descriptionMarkdown());
		assertThat(view.faithfulRender().status()).isEqualTo("unavailable");
		assertThat(view.faithfulRender().message()).contains("original Markdown").doesNotContain("/private", "Browser failure");
		assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
	}

	@Test
	void missingReferencedPixelsPreserveMaterialAndAvoidTheRenderer() {
		final String markdown = "![Bild](img:7q3m6r9t)";
		when(design.findMaterialByExamIdAndId(7, 15)).thenReturn(Optional.of(new LoeMaterial(15, 11, "Quelle", markdown, 0)));

		final MaterialContentView view = (MaterialContentView) tools.getMaterial(7, 15, true).structuredContent();

		assertThat(view.material().descriptionMarkdown()).isEqualTo(markdown);
		assertThat(view.faithfulRender().status()).isEqualTo("unavailable");
		verifyNoInteractions(renderer);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4 })
	void oversizedEmptyOrMalformedRendersRemainUnavailableWithoutLosingMarkdown(final int invalidCase) {
		final RenderedPng invalid = switch (invalidCase) {
			case 0 -> new RenderedPng(new byte[0], 2, 3);
			case 1 -> new RenderedPng(new byte[1], 961, 3);
			case 2 -> new RenderedPng(new byte[1], 960, 12_001);
			case 3 -> new RenderedPng(new byte[1], 2, 3);
			default -> new RenderedPng(new byte[8 * 1024 * 1024 + 1], 2, 3);
		};
		when(renderer.render(any())).thenReturn(invalid);

		final var result = tools.getTask(7, 13, true);
		final TaskContentView view = (TaskContentView) result.structuredContent();

		assertThat(view.faithfulRender().status()).isEqualTo("unavailable");
		assertThat(view.task().descriptionMarkdown()).isEqualTo(task.descriptionMarkdown());
		assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
	}

	@Test
	void renderFlagDoesNotTurnOwnershipOrAuthenticationErrorsIntoSuccess() {
		assertThatIllegalArgumentException().isThrownBy(() -> tools.getTask(7, 99, true));
		assertThatIllegalArgumentException().isThrownBy(() -> tools.getMaterial(7, 99, true));
		assertThatIllegalArgumentException().isThrownBy(() -> tools.getTaskRequirements(7, 99, true));
		closeMcpAccess();
		assertThatThrownBy(() -> tools.getExamFooter(7, true)).isInstanceOf(SecurityException.class);
		verifyNoInteractions(renderer);
	}

	private static byte[] png() throws Exception {
		final ByteArrayOutputStream data = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", data);
		return data.toByteArray();
	}

	private static Course course(final Lifecycle lifecycle) {
		return new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, lifecycle, 5);
	}
}
