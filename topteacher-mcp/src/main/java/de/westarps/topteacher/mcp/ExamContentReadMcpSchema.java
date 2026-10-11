package de.westarps.topteacher.mcp;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ExamPermissions;
import de.westarps.topteacher.mcp.ExamDesignMcpSchema.ImageReferenceView;
import de.westarps.topteacher.mcp.ExamMcpTools.ExamView;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.RequirementView;

/** Focused read models keep unrelated exam content out of individual reads. */
public final class ExamContentReadMcpSchema {

	private ExamContentReadMcpSchema() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TaskContentView(int examId, long revision, ExamPermissions permissions, TaskContent task,
			@Nullable FaithfulRender faithfulRender) {
		public TaskContentView(final int examId, final long revision, final ExamPermissions permissions,
				final TaskContent task) {
			this(examId, revision, permissions, task, null);
		}
	}

	public record TaskContent(int id, int categoryId, String title, String descriptionMarkdown, int maxPoints,
			int sortOrder) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MaterialContentView(int examId, long revision, ExamPermissions permissions,
			MaterialContent material, @Nullable FaithfulRender faithfulRender) {
		public MaterialContentView(final int examId, final long revision, final ExamPermissions permissions,
				final MaterialContent material) {
			this(examId, revision, permissions, material, null);
		}
	}

	public record MaterialContent(int id, int partId, String title, String descriptionMarkdown, int sortOrder,
			List<ImageReferenceView> images) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ExamFooterView(int examId, long revision, ExamPermissions permissions, String footerMarkdown,
			@Nullable FaithfulRender faithfulRender) {
		public ExamFooterView(final int examId, final long revision, final ExamPermissions permissions,
				final String footerMarkdown) {
			this(examId, revision, permissions, footerMarkdown, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TaskRequirementsContentView(int examId, long revision, ExamPermissions permissions,
			TaskReference task, List<RequirementView> requirements, @Nullable FaithfulRender faithfulRender) {
		public TaskRequirementsContentView(final int examId, final long revision, final ExamPermissions permissions,
				final TaskReference task, final List<RequirementView> requirements) {
			this(examId, revision, permissions, task, requirements, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FaithfulRender(String status, @Nullable String mimeType, @Nullable Integer pixelWidth,
			@Nullable Integer pixelHeight, @Nullable Long byteSize, @Nullable String message) {
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
