package de.westarps.topteacher.mcp;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.westarps.topteacher.mcp.CourseMcpTools.CourseView;
import de.westarps.topteacher.mcp.ExamMcpTools.ExamView;

/** Explicit MCP read models for pupil-facing exam content and assessment context. */
public final class ExamDesignMcpSchema {

	private ExamDesignMcpSchema() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ExamDefinitionView(CourseView course, ExamView exam, long revision,
			@Nullable GradingScaleView gradingScale, String footerMarkdown, ExamPermissions permissions,
			PointSummaryView points, List<DesignDiagnostic> diagnostics, List<PartView> parts) {
	}

	public record ExamPermissions(boolean canEditExamContent, boolean canImportMaterialImages,
			boolean canEditLevelOfExpectationsStructure, boolean canCorrectRequirementText, boolean correctionMode,
			boolean archivedCourse) {
	}

	public record PartView(int id, String title, int sortOrder, List<MaterialView> materials,
			List<CategoryView> categories) {
	}

	public record MaterialView(int id, String title, String descriptionMarkdown, int sortOrder,
			List<ImageReferenceView> images) {
	}

	public record ImageReferenceView(String imageId, String mimeType, int pixelWidth, int pixelHeight, long byteSize) {
	}

	public record CategoryView(int id, String title, String descriptionMarkdown, int sortOrder, List<TaskView> tasks) {
	}

	public record TaskView(int id, String title, String descriptionMarkdown, int maxPoints, int sortOrder) {
	}

	public record GradingScaleView(int id, String name, int maxPoints, String lifecycle,
			List<GradingScaleRangeView> ranges) {
	}

	public record GradingScaleRangeView(String gradeLevel, String displayName, String shortName, int minPoints,
			int maxPoints) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PointSummaryView(int regularMaximum, int bonusMaximum, @Nullable Integer gradingScaleMaximum,
			boolean regularMaximumMatchesGradingScale, boolean requirementsMatchTasks, boolean validDesign) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record DesignDiagnostic(String code, String severity, String nodeType, @Nullable Integer nodeId,
			String field, String message) {
	}
}
