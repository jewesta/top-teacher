package de.westarps.topteacher.model.loe;

import java.util.Objects;

public record LoeRequirement(Integer id, Integer taskId, String descriptionMarkdown, int maxPoints, boolean bonus,
		int sortOrder) {

	public LoeRequirement {
		taskId = Objects.requireNonNull(taskId, "taskId must not be null");
		descriptionMarkdown = descriptionMarkdown == null ? "" : descriptionMarkdown;
		if (maxPoints < 0) {
			throw new IllegalArgumentException("maxPoints must not be negative");
		}
	}
}
