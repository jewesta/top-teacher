package de.westarps.topteacher.model.loe;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** An explicit requirement change against the existing exam task hierarchy. */
public record LevelOfExpectationsCommand(Type type, @Nullable Integer id, @Nullable String clientKey,
		@Nullable Integer taskId, @Nullable String taskKey, @Nullable String markdown, @Nullable Integer maxPoints,
		@Nullable Boolean bonus, @Nullable List<ExamNodeReference> order, @Nullable Boolean acknowledgeDeletion) {

	public LevelOfExpectationsCommand {
		order = order == null ? null : List.copyOf(order);
	}

	public enum Type {
		CREATE_REQUIREMENT, UPDATE_REQUIREMENT, DELETE_REQUIREMENT, REORDER_REQUIREMENTS
	}
}
