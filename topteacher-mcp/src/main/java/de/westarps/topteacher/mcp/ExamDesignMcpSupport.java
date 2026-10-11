package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.CategoryView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.DesignDiagnostic;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ExamDefinitionView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ExamPermissions;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.GradingScaleRangeView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.GradingScaleView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ImageReferenceView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.MaterialView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.PartView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.PointSummaryView;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.TaskView;
import de.westarps.topteacher.mcp.ExamMcpTools.ExamView;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImageMetadata;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.model.loe.LoeValidator;

/** Builds consistent, pupil-independent context for aggregate and focused design reads. */
@Component
public class ExamDesignMcpSupport {

	private final CourseRepository courses;
	private final ExamRepository exams;
	private final GradingScaleRepository gradingScales;
	private final LevelOfExpectationsRepository design;
	private final ExamDesignGuard guard;

	public ExamDesignMcpSupport(final CourseRepository courses, final ExamRepository exams,
			final GradingScaleRepository gradingScales, final LevelOfExpectationsRepository design,
			final ExamDesignGuard guard) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.exams = Objects.requireNonNull(exams, "exams");
		this.gradingScales = Objects.requireNonNull(gradingScales, "gradingScales");
		this.design = Objects.requireNonNull(design, "design");
		this.guard = Objects.requireNonNull(guard, "guard");
	}

	public <T> T read(final int examId, final Function<DesignSnapshot, T> projection) {
		McpAccessContext.accessLevel();
		return guard.read(examId, () -> projection.apply(snapshot(examId)));
	}

	public <T> T readContext(final int examId, final Function<ExamReadContext, T> projection) {
		McpAccessContext.accessLevel();
		return guard.read(examId, () -> projection.apply(context(examId)));
	}

	public ExamView examMetadata(final ExamReadContext context) {
		return ExamMcpTools.examView(context.exam(), exams.findNumberById(context.exam().id()).orElse(null),
				context.permissions().correctionMode());
	}

	public ExamDefinitionView examView(final DesignSnapshot snapshot) {
		final List<PartView> parts = snapshot.parts().stream().map(part -> {
			final List<MaterialView> materials = snapshot.materials().stream()
					.filter(material -> material.partId().equals(part.id())).map(material -> new MaterialView(
							material.id(), material.title(), material.descriptionMarkdown(), material.sortOrder(),
							LoeMaterialImageReferences.ids(material.descriptionMarkdown()).stream().sorted()
									.map(snapshot.images()::get).filter(Objects::nonNull)
									.map(image -> new ImageReferenceView(image.id(), image.mediaType(), image.pixelWidth(),
											image.pixelHeight(), image.byteSize())).toList())).toList();
			final List<CategoryView> categories = snapshot.categories().stream()
					.filter(category -> category.partId().equals(part.id())).map(category -> new CategoryView(
							category.id(), category.title(), category.descriptionMarkdown(), category.sortOrder(),
							snapshot.tasks().stream().filter(task -> task.categoryId().equals(category.id()))
									.map(task -> new TaskView(task.id(), task.title(), task.descriptionMarkdown(),
											task.maxPoints(), task.sortOrder())).toList())).toList();
			return new PartView(part.id(), part.title(), part.sortOrder(), materials, categories);
		}).toList();
		return new ExamDefinitionView(CourseMcpTools.courseView(snapshot.course()),
				ExamMcpTools.examView(snapshot.exam(), exams.findNumberById(snapshot.exam().id()).orElse(null),
						snapshot.permissions().correctionMode()), snapshot.revision(), snapshot.gradingScale(),
				snapshot.footerMarkdown(), snapshot.permissions(), snapshot.points(), snapshot.diagnostics(), parts);
	}

	public GradingScaleView gradingScaleView(final GradingScale scale) {
		return new GradingScaleView(scale.id(), scale.name(), scale.maxPoints(), scale.lifecycle().name(),
				gradingScales.findRangesByGradingScaleId(scale.id()).stream().map(range -> new GradingScaleRangeView(
						range.gradeLevel().name(), range.gradeLevel().getDisplayName(), range.gradeLevel().getShortName(),
						range.minPoints(), range.maxPoints())).toList());
	}

	private ExamReadContext context(final int examId) {
		final Exam exam = exams.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		final Course course = courses.findById(exam.courseId())
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
		final boolean correctionMode = design.hasResultsForExam(examId);
		final boolean active = course.lifecycle() == Lifecycle.ACTIVE;
		final ExamPermissions permissions = new ExamPermissions(active && !correctionMode, active && !correctionMode,
				active && !correctionMode, active, correctionMode, !active);
		return new ExamReadContext(exam, course, guard.revision(examId), permissions);
	}

	private DesignSnapshot snapshot(final int examId) {
		final ExamReadContext context = context(examId);
		final Exam exam = context.exam();
		final List<LoePart> parts = sorted(design.findPartsByExamId(examId), LoePart::sortOrder, LoePart::id);
		final List<LoeCategory> categories = sorted(design.findCategoriesByExamId(examId), LoeCategory::sortOrder,
				LoeCategory::id);
		final List<LoeTask> tasks = sorted(design.findTasksByExamId(examId), LoeTask::sortOrder, LoeTask::id);
		final List<LoeMaterial> materials = sorted(design.findMaterialsByExamId(examId), LoeMaterial::sortOrder,
				LoeMaterial::id);
		final List<LoeRequirement> requirements = sorted(design.findRequirementsByExamId(examId),
				LoeRequirement::sortOrder, LoeRequirement::id);
		final List<LoeCriterion> criteria = sorted(design.findActiveCriteriaByExamId(examId), LoeCriterion::sortOrder,
				LoeCriterion::id);
		final Set<String> imageIds = materials.stream()
				.flatMap(material -> LoeMaterialImageReferences.ids(material.descriptionMarkdown()).stream())
				.collect(Collectors.toSet());
		final Map<String, LoeMaterialImageMetadata> images = design.findMaterialImageMetadataByIds(imageIds).stream()
				.collect(Collectors.toMap(LoeMaterialImageMetadata::id, Function.identity()));
		final GradingScaleView scale = exam.gradingScaleId() == null ? null
				: gradingScales.findById(exam.gradingScaleId()).map(this::gradingScaleView).orElse(null);
		final List<DesignDiagnostic> diagnostics = diagnostics(examId, tasks, materials, requirements, images, scale);
		final int maximum = tasks.stream().mapToInt(LoeTask::maxPoints).sum();
		final int bonus = requirements.stream().filter(LoeRequirement::bonus).mapToInt(LoeRequirement::maxPoints).sum();
		final boolean taskMatch = tasks.stream().allMatch(task -> requirements.stream()
				.filter(requirement -> requirement.taskId().equals(task.id()) && !requirement.bonus())
				.mapToInt(LoeRequirement::maxPoints).sum() == task.maxPoints());
		final boolean scaleMatch = scale != null && maximum == scale.maxPoints();
		final PointSummaryView points = new PointSummaryView(maximum, bonus, scale == null ? null : scale.maxPoints(),
				scaleMatch, taskMatch, scaleMatch && taskMatch);
		return new DesignSnapshot(exam, context.course(), context.revision(), scale,
				Objects.requireNonNullElse(design.findExamFooterMarkdown(examId), ""), context.permissions(), points,
				List.copyOf(diagnostics), parts, categories, tasks, materials, requirements, criteria, images);
	}

	private static List<DesignDiagnostic> diagnostics(final int examId, final List<LoeTask> tasks,
			final List<LoeMaterial> materials, final List<LoeRequirement> requirements,
			final Map<String, LoeMaterialImageMetadata> images, final GradingScaleView scale) {
		final List<DesignDiagnostic> findings = new ArrayList<>();
		if (tasks.isEmpty()) {
			findings.add(new DesignDiagnostic("NO_TASKS", "WARNING", "EXAM", examId, "tasks",
					"Add pupil-facing tasks before recording results."));
		}
		if (scale == null) {
			findings.add(new DesignDiagnostic("MISSING_GRADING_SCALE", "WARNING", "EXAM", examId, "gradingScaleId",
					"The exam has no available grading scale."));
		}
		final var validation = scale == null ? LoeValidator.validate(tasks, requirements)
				: LoeValidator.validate(scale.maxPoints(), tasks, requirements);
		validation.getResults().forEach(result -> result.getTestResults().forEach(test -> {
			final var target = result.getTarget();
			final String nodeType = switch (target.kind()) {
				case TOTAL_POINTS -> "EXAM";
				case TASK_POINTS -> "TASK";
				case REQUIREMENT_CRITERIA -> "REQUIREMENT";
			};
			final Integer nodeId = target.taskId() != null ? target.taskId()
					: target.requirementId() != null ? target.requirementId() : examId;
			findings.add(new DesignDiagnostic(target.kind().name(), test.severity().name(), nodeType, nodeId,
					target.kind().name().equals("REQUIREMENT_CRITERIA") ? "descriptionMarkdown" : "maxPoints",
					test.message()));
		}));
		materials.forEach(material -> LoeMaterialImageReferences.ids(material.descriptionMarkdown()).stream()
				.filter(id -> !images.containsKey(id)).forEach(id -> findings.add(new DesignDiagnostic(
						"MISSING_IMAGE", "ERROR", "MATERIAL", material.id(), "descriptionMarkdown",
						"Managed image " + id + " is not available. Import it through import_material_image."))));
		return findings;
	}

	private static <T> List<T> sorted(final Collection<T> values, final Function<T, Integer> sortOrder,
			final Function<T, Integer> id) {
		return values.stream().sorted(Comparator.comparing(sortOrder).thenComparing(id)).toList();
	}

	public record ExamReadContext(Exam exam, Course course, long revision, ExamPermissions permissions) {
	}

	public record DesignSnapshot(Exam exam, Course course, long revision, GradingScaleView gradingScale,
			String footerMarkdown, ExamPermissions permissions, PointSummaryView points,
			List<DesignDiagnostic> diagnostics, List<LoePart> parts, List<LoeCategory> categories, List<LoeTask> tasks,
			List<LoeMaterial> materials, List<LoeRequirement> requirements, List<LoeCriterion> criteria,
			Map<String, LoeMaterialImageMetadata> images) {
	}
}
