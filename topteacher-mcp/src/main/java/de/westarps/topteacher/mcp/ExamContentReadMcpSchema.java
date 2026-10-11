package de.westarps.topteacher.mcp;

import java.util.List;

import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ExamPermissions;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ImageReferenceView;
import de.westarps.topteacher.mcp.ExamMcpTools.ExamView;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.RequirementView;

/** Focused read models keep unrelated exam content out of individual reads. */
public final class ExamContentReadMcpSchema {

	private ExamContentReadMcpSchema() {
	}

	public record TaskContentView(int examId, long revision, ExamPermissions permissions, TaskContent task) {
	}

	public record TaskContent(int id, int categoryId, String title, String descriptionMarkdown, int maxPoints,
			int sortOrder) {
	}

	public record MaterialContentView(int examId, long revision, ExamPermissions permissions,
			MaterialContent material) {
	}

	public record MaterialContent(int id, int partId, String title, String descriptionMarkdown, int sortOrder,
			List<ImageReferenceView> images) {
	}

	public record ExamFooterView(int examId, long revision, ExamPermissions permissions, String footerMarkdown) {
	}

	public record TaskRequirementsContentView(int examId, long revision, ExamPermissions permissions,
			TaskReference task, List<RequirementView> requirements) {
	}

	public record TaskReference(int id, int categoryId, String title, int maxPoints, int sortOrder) {
	}

	public record ExamStructureView(int examId, long revision, ExamPermissions permissions, ExamView exam,
			List<PartOutline> parts, boolean closingTextPresent) {
	}

	public record PartOutline(int id, String title, int sortOrder, List<MaterialOutline> materials,
			List<CategoryOutline> categories) {
	}

	public record MaterialOutline(int id, String title, int sortOrder) {
	}

	public record CategoryOutline(int id, String title, int sortOrder, List<TaskOutline> tasks) {
	}

	public record TaskOutline(int id, String title, int maxPoints, int sortOrder) {
	}
}
