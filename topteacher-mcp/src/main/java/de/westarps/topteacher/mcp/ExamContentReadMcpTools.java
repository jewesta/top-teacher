package de.westarps.topteacher.mcp;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.CategoryOutline;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.ExamFooterView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.ExamStructureView;
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
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoeMaterialOutline;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeTaskOutline;

/** Narrow exam reads share the same authentication, revision and consistency boundary. */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamContentReadMcpTools {

	private final ExamDesignMcpSupport support;
	private final LevelOfExpectationsRepository design;

	public ExamContentReadMcpTools(final ExamDesignMcpSupport support, final LevelOfExpectationsRepository design) {
		this.support = Objects.requireNonNull(support, "support");
		this.design = Objects.requireNonNull(design, "design");
	}

	@McpTool(name = "get_task", title = "Get one exam task",
			description = "Read one pupil-facing task's original Markdown and points. Discover IDs with get_exam_structure. A missing task or one belonging to another exam is rejected. Requirements and unrelated exam content are omitted.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public TaskContentView getTask(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId,
			@McpToolParam(description = "Task ID belonging to this exam from get_exam_structure or get_exam.") final int taskId) {
		return support.readContext(examId, context -> {
			final LoeTask task = requireTask(examId, taskId);
			return new TaskContentView(examId, context.revision(), context.permissions(), new TaskContent(task.id(),
					task.categoryId(), task.title(), task.descriptionMarkdown(), task.maxPoints(), task.sortOrder()));
		});
	}

	@McpTool(name = "get_material", title = "Get one exam material",
			description = "Read one material's original Markdown and referenced image metadata. Retrieve image pixels separately with get_material_image. Discover IDs with get_exam_structure. A missing material or one belonging to another exam is rejected. Other materials are omitted.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public MaterialContentView getMaterial(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId,
			@McpToolParam(description = "Material ID belonging to this exam from get_exam_structure or get_exam.") final int materialId) {
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
			return new MaterialContentView(examId, context.revision(), context.permissions(), new MaterialContent(
					material.id(), material.partId(), material.title(), material.descriptionMarkdown(), material.sortOrder(),
					images));
		});
	}

	@McpTool(name = "get_exam_footer", title = "Get exam closing text",
			description = "Read only the exam's original closing/footer Markdown, revision and permissions. Empty text is returned as an empty string. No task, material or assessment content is loaded.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ExamFooterView getExamFooter(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId) {
		return support.readContext(examId, context -> new ExamFooterView(examId, context.revision(), context.permissions(),
				Objects.requireNonNullElse(design.findExamFooterMarkdown(examId), "")));
	}

	@McpTool(name = "get_task_requirements", title = "Get requirements for one exam task",
			description = "Read one task's ordered level-of-expectations requirements and active criteria, with read-only task identity and points. Pupil-facing task Markdown and other tasks' requirements are omitted. A missing task or one belonging to another exam is rejected.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public TaskRequirementsContentView getTaskRequirements(
			@McpToolParam(description = "Exam ID from list_exams.") final int examId,
			@McpToolParam(description = "Task ID belonging to this exam from get_exam_structure or get_exam.") final int taskId) {
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
			return new TaskRequirementsContentView(examId, context.revision(), context.permissions(), new TaskReference(
					task.id(), task.categoryId(), task.title(), task.maxPoints(), task.sortOrder()), views);
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

	private static <T> List<T> sorted(final Collection<T> values, final Function<T, Integer> sortOrder,
			final Function<T, Integer> id) {
		return values.stream().sorted(Comparator.comparing(sortOrder).thenComparing(id)).toList();
	}
}
