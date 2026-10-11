package de.westarps.topteacher.mcp;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Section;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.CategoryOutline;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.ExamFooterView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.ExamStructureView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.FaithfulRender;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.MaterialContent;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.MaterialContentView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.MaterialOutline;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.PartOutline;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskContent;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskContentView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskOutline;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskReference;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskRequirementsContentView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ImageReferenceView;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.CriterionView;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.RequirementView;
import de.westarps.topteacher.model.loe.LoeCategoryOutline;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoeMaterialOutline;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeTaskOutline;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import tools.jackson.databind.json.JsonMapper;

/** Narrow exam reads share the same authentication, revision and consistency boundary. */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamContentReadMcpTools {

	private static final int MAX_RENDER_BYTES = 8 * 1024 * 1024;
	private static final int MAX_RENDER_WIDTH = 960;
	private static final int MAX_RENDER_HEIGHT = 12_000;
	private static final String RENDER_UNAVAILABLE_MESSAGE = "Faithful PNG rendering is unavailable. The original Markdown is still available.";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ExamDesignMcpSupport support;
	private final LevelOfExpectationsRepository design;
	private final FaithfulMarkdownRenderer renderer;

	public ExamContentReadMcpTools(final ExamDesignMcpSupport support, final LevelOfExpectationsRepository design) {
		this(support, design, request -> {
			throw new IllegalStateException("Faithful renderer is unavailable.");
		});
	}

	@Autowired
	public ExamContentReadMcpTools(final ExamDesignMcpSupport support, final LevelOfExpectationsRepository design,
			final FaithfulMarkdownRenderer renderer) {
		this.support = Objects.requireNonNull(support, "support");
		this.design = Objects.requireNonNull(design, "design");
		this.renderer = Objects.requireNonNull(renderer, "renderer");
	}

	@McpTool(name = "get_task", title = "Get one exam task",
			description = "Read one pupil-facing task's original Markdown and points. Optionally include a faithful PNG for inline display. Discover IDs with get_exam_structure. A missing task or one belonging to another exam is rejected. Requirements and unrelated exam content are omitted.",
			generateOutputSchema = false, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult getTask(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId,
			@McpToolParam(description = "Task ID belonging to this exam from get_exam_structure or get_exam.") final int taskId,
			@McpToolParam(required = false, description = "Include a faithful PNG of this content. Defaults to false; original Markdown is always returned.") final @Nullable Boolean includeFaithfulRender) {
		final var snapshot = taskSnapshot(examId, taskId, Boolean.TRUE.equals(includeFaithfulRender));
		return result(snapshot, (view, render) -> new TaskContentView(view.examId(), view.revision(), view.permissions(),
				view.task(), render));
	}

	public TaskContentView getTask(final int examId, final int taskId) {
		return taskSnapshot(examId, taskId, false).view();
	}

	private ReadSnapshot<TaskContentView> taskSnapshot(final int examId, final int taskId, final boolean render) {
		return support.readContext(examId, context -> {
			final LoeTask task = requireTask(examId, taskId);
			final var view = new TaskContentView(examId, context.revision(), context.permissions(), new TaskContent(task.id(),
					task.categoryId(), task.title(), task.descriptionMarkdown(), task.maxPoints(), task.sortOrder()));
			return snapshot(view, render, () -> List.of(new Section(task.title(), task.descriptionMarkdown(), Variant.STANDARD)),
					List::of);
		});
	}

	@McpTool(name = "get_material", title = "Get one exam material",
			description = "Read one material's original Markdown and referenced image metadata. Optionally include a faithful PNG with its referenced images. Retrieve original image pixels with get_material_image. Discover IDs with get_exam_structure. A missing material or one belonging to another exam is rejected. Other materials are omitted.",
			generateOutputSchema = false, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult getMaterial(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId,
			@McpToolParam(description = "Material ID belonging to this exam from get_exam_structure or get_exam.") final int materialId,
			@McpToolParam(required = false, description = "Include a faithful PNG of this material and its referenced images. Defaults to false; original Markdown is always returned.") final @Nullable Boolean includeFaithfulRender) {
		final var snapshot = materialSnapshot(examId, materialId, Boolean.TRUE.equals(includeFaithfulRender));
		return result(snapshot, (view, render) -> new MaterialContentView(view.examId(), view.revision(), view.permissions(),
				view.material(), render));
	}

	public MaterialContentView getMaterial(final int examId, final int materialId) {
		return materialSnapshot(examId, materialId, false).view();
	}

	private ReadSnapshot<MaterialContentView> materialSnapshot(final int examId, final int materialId, final boolean render) {
		return support.readContext(examId, context -> {
			final var material = design.findMaterialByExamIdAndId(examId, materialId)
					.orElseThrow(() -> new IllegalArgumentException(
							"Material does not exist in exam " + examId + ": " + materialId));
			final Set<String> imageIds = LoeMaterialImageReferences.ids(material.descriptionMarkdown());
			final List<ImageReferenceView> images = imageIds.isEmpty() ? List.of()
					: design.findMaterialImageMetadataByIds(imageIds).stream().filter(image -> imageIds.contains(image.id()))
							.sorted(Comparator.comparing(image -> image.id()))
							.map(image -> new ImageReferenceView(image.id(), image.mediaType(), image.pixelWidth(),
									image.pixelHeight(), image.byteSize())).toList();
			final var view = new MaterialContentView(examId, context.revision(), context.permissions(), new MaterialContent(
					material.id(), material.partId(), material.title(), material.descriptionMarkdown(), material.sortOrder(),
					images));
			return snapshot(view, render, () -> List.of(new Section(material.title(), material.descriptionMarkdown(), Variant.MATERIAL)),
					() -> referencedImages(imageIds));
		});
	}

	@McpTool(name = "get_exam_footer", title = "Get exam closing text",
			description = "Read only the exam's original closing/footer Markdown, revision and permissions. Optionally include a faithful PNG. Empty text is returned as an empty string. No task, material or assessment content is loaded.",
			generateOutputSchema = false, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult getExamFooter(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId,
			@McpToolParam(required = false, description = "Include a faithful PNG of the closing text. Defaults to false; original Markdown is always returned.") final @Nullable Boolean includeFaithfulRender) {
		final var snapshot = footerSnapshot(examId, Boolean.TRUE.equals(includeFaithfulRender));
		return result(snapshot, (view, render) -> new ExamFooterView(view.examId(), view.revision(), view.permissions(),
				view.footerMarkdown(), render));
	}

	public ExamFooterView getExamFooter(final int examId) {
		return footerSnapshot(examId, false).view();
	}

	private ReadSnapshot<ExamFooterView> footerSnapshot(final int examId, final boolean render) {
		return support.readContext(examId, context -> {
			final var view = new ExamFooterView(examId, context.revision(), context.permissions(),
					Objects.requireNonNullElse(design.findExamFooterMarkdown(examId), ""));
			return snapshot(view, render, () -> List.of(new Section("", view.footerMarkdown(), Variant.STANDARD)), List::of);
		});
	}

	@McpTool(name = "get_task_requirements", title = "Get requirements for one exam task",
			description = "Read one task's ordered level-of-expectations requirements and active criteria, with read-only task identity and points. Optionally include a faithful PNG of the canonical requirements. Pupil-facing task Markdown and other tasks' requirements are omitted. A missing task or one belonging to another exam is rejected.",
			generateOutputSchema = false, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult getTaskRequirements(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId,
			@McpToolParam(description = "Task ID belonging to this exam from get_exam_structure or get_exam.") final int taskId,
			@McpToolParam(required = false, description = "Include a faithful PNG of these requirements and criteria. Defaults to false; canonical Markdown is always returned.") final @Nullable Boolean includeFaithfulRender) {
		final var snapshot = requirementsSnapshot(examId, taskId, Boolean.TRUE.equals(includeFaithfulRender));
		return result(snapshot, (view, render) -> new TaskRequirementsContentView(view.examId(), view.revision(), view.permissions(),
				view.task(), view.requirements(), render));
	}

	public TaskRequirementsContentView getTaskRequirements(final int examId, final int taskId) {
		return requirementsSnapshot(examId, taskId, false).view();
	}

	private ReadSnapshot<TaskRequirementsContentView> requirementsSnapshot(final int examId, final int taskId,
			final boolean render) {
		return support.readContext(examId, context -> {
			final LoeTask task = requireTask(examId, taskId);
			final List<LoeRequirement> requirements = sorted(design.findRequirementsByExamIdAndTaskId(examId, taskId),
					LoeRequirement::sortOrder, LoeRequirement::id);
			final List<LoeCriterion> criteria = sorted(design.findActiveCriteriaByExamIdAndTaskId(examId, taskId),
					LoeCriterion::sortOrder, LoeCriterion::id);
			final List<RequirementView> views = requirements.stream().map(requirement -> new RequirementView(
					requirement.id(), requirement.descriptionMarkdown(), requirement.maxPoints(), requirement.bonus(),
					requirement.sortOrder(), criteria.stream()
							.filter(criterion -> criterion.active() && criterion.requirementId().equals(requirement.id()))
							.map(criterion -> new CriterionView(criterion.id(), criterion.criterionKey(), criterion.label(),
									criterion.pointUnits(), criterion.sortOrder())).toList())).toList();
			final var view = new TaskRequirementsContentView(examId, context.revision(), context.permissions(), new TaskReference(
					task.id(), task.categoryId(), task.title(), task.maxPoints(), task.sortOrder()), views);
			return snapshot(view, render, () -> views.isEmpty() ? List.of(new Section(task.title(), "", Variant.REQUIREMENT))
					: views.stream().map(requirement -> new Section("", requirement.descriptionMarkdown(), Variant.REQUIREMENT)).toList(),
					List::of);
		});
	}

	@McpTool(name = "get_exam_structure", title = "Get exam structure",
			description = "Discover ordered part, category, task and material IDs, titles and task points in a compact outline. Includes exam metadata, revision, permissions and whether closing text exists. No Markdown, images, requirements or pupils are returned. Read selected content with get_task, get_material, get_exam_footer or get_task_requirements.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamStructureView getExamStructure(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId) {
		return support.readContext(examId, context -> {
			final List<LoePart> parts = sorted(design.findPartsByExamId(examId), LoePart::sortOrder, LoePart::id);
			final List<LoeCategoryOutline> categories = sorted(design.findCategoryOutlinesByExamId(examId),
					LoeCategoryOutline::sortOrder, LoeCategoryOutline::id);
			final List<LoeTaskOutline> tasks = sorted(design.findTaskOutlinesByExamId(examId), LoeTaskOutline::sortOrder,
					LoeTaskOutline::id);
			final List<LoeMaterialOutline> materials = sorted(design.findMaterialOutlinesByExamId(examId),
					LoeMaterialOutline::sortOrder, LoeMaterialOutline::id);
			final List<PartOutline> outline = parts.stream().map(part -> new PartOutline(part.id(), part.title(),
					part.sortOrder(), materials.stream().filter(material -> material.partId() == part.id())
							.map(material -> new MaterialOutline(material.id(), material.title(), material.sortOrder())).toList(),
					categories.stream().filter(category -> category.partId() == part.id())
							.map(category -> new CategoryOutline(category.id(), category.title(), category.sortOrder(),
									tasks.stream().filter(task -> task.categoryId() == category.id())
											.map(task -> new TaskOutline(task.id(), task.title(), task.maxPoints(), task.sortOrder()))
											.toList())).toList())).toList();
			return new ExamStructureView(examId, context.revision(), context.permissions(), support.examMetadata(context),
					outline, !Objects.requireNonNullElse(design.findExamFooterMarkdown(examId), "").isBlank());
		});
	}

	private LoeTask requireTask(final int examId, final int taskId) {
		return design.findTaskByExamIdAndId(examId, taskId)
				.orElseThrow(() -> new IllegalArgumentException("Task does not exist in exam " + examId + ": " + taskId));
	}

	private List<LoeMaterialImage> referencedImages(final Set<String> imageIds) {
		if (imageIds.isEmpty()) {
			return List.of();
		}
		final List<LoeMaterialImage> images = design.findMaterialImagesByIds(imageIds).stream()
				.filter(image -> imageIds.contains(image.id())).sorted(Comparator.comparing(LoeMaterialImage::id)).toList();
		if (!images.stream().map(LoeMaterialImage::id).collect(java.util.stream.Collectors.toSet()).equals(imageIds)) {
			throw new IllegalStateException("A referenced material image is unavailable.");
		}
		return images;
	}

	private static <T> ReadSnapshot<T> snapshot(final T view, final boolean render,
			final Supplier<List<Section>> sections, final Supplier<List<LoeMaterialImage>> images) {
		if (!render) {
			return new ReadSnapshot<>(view, null, false);
		}
		try {
			return new ReadSnapshot<>(view, new RenderRequest(sections.get(), images.get()), false);
		} catch (final RuntimeException exception) {
			return new ReadSnapshot<>(view, null, true);
		}
	}

	private <T> CallToolResult result(final ReadSnapshot<T> snapshot,
			final BiFunction<T, FaithfulRender, T> withRender) {
		T view = snapshot.view();
		ImageContent image = null;
		if (snapshot.request() != null || snapshot.renderUnavailable()) {
			FaithfulRender render;
			try {
				if (snapshot.renderUnavailable()) {
					throw new IllegalStateException("Rendering input is unavailable.");
				}
				final var png = renderer.render(snapshot.request());
				final byte[] data = png.data();
				if (data.length == 0 || data.length > MAX_RENDER_BYTES || png.pixelWidth() < 1
						|| png.pixelWidth() > MAX_RENDER_WIDTH || png.pixelHeight() < 1 || png.pixelHeight() > MAX_RENDER_HEIGHT) {
					throw new IllegalStateException("Rendered PNG exceeds the configured limits.");
				}
				image = McpImageContentFactory.fromPng(data);
				render = new FaithfulRender("rendered", "image/png", png.pixelWidth(), png.pixelHeight(),
						(long) data.length, null);
			} catch (final RuntimeException exception) {
				render = new FaithfulRender("unavailable", null, null, null, null, RENDER_UNAVAILABLE_MESSAGE);
			}
			view = withRender.apply(view, render);
		}
		final var result = CallToolResult.builder().structuredContent(view).addTextContent(JSON.writeValueAsString(view));
		if (image != null) {
			result.addContent(image);
		}
		return result.build();
	}

	private record ReadSnapshot<T>(T view, @Nullable RenderRequest request, boolean renderUnavailable) {
	}

	private static <T> List<T> sorted(final Collection<T> values, final Function<T, Integer> sortOrder,
			final Function<T, Integer> id) {
		return values.stream().sorted(Comparator.comparing(sortOrder).thenComparing(id)).toList();
	}
}
