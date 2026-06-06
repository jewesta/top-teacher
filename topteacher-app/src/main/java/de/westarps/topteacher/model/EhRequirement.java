package de.westarps.topteacher.model;

import java.util.Objects;

public record EhRequirement(Integer id, Integer taskId, String descriptionMarkdown, int maxPoints, boolean bonus,
		int sortOrder) {

	public EhRequirement {
		taskId = Objects.requireNonNull(taskId, "taskId must not be null");
		descriptionMarkdown = descriptionMarkdown == null ? "" : descriptionMarkdown;
		if (maxPoints < 0) {
			throw new IllegalArgumentException("maxPoints must not be negative");
		}
	}
}
