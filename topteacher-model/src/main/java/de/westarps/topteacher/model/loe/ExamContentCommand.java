package de.westarps.topteacher.model.loe;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One explicit change to pupil-facing exam content. Null update fields are
 * omitted; an empty Markdown string explicitly clears that field.
 */
public record ExamContentCommand(Type type, @Nullable Integer id, @Nullable String clientKey,
		@Nullable Integer parentId, @Nullable String parentKey, @Nullable String title, @Nullable String markdown,
		@Nullable Integer maxPoints, @Nullable List<ExamNodeReference> order, @Nullable Boolean acknowledgeDeletion) {

	public ExamContentCommand {
		order = order == null ? null : List.copyOf(order);
	}

	public enum Type {
		CREATE_PART, UPDATE_PART, DELETE_PART, REORDER_PARTS,
		CREATE_CATEGORY, UPDATE_CATEGORY, DELETE_CATEGORY, REORDER_CATEGORIES,
		CREATE_TASK, UPDATE_TASK, DELETE_TASK, REORDER_TASKS,
		CREATE_MATERIAL, UPDATE_MATERIAL, DELETE_MATERIAL, REORDER_MATERIALS,
		SET_FOOTER
	}
}
