package de.westarps.topteacher.model.loe;

import java.util.Objects;

public record LoeTask(Integer id, Integer categoryId, String title, String descriptionMarkdown, int maxPoints,
		int sortOrder) {

	public LoeTask(final Integer id, final Integer categoryId, final String title, final int sortOrder) {
		this(id, categoryId, title, "", 0, sortOrder);
	}

	public LoeTask {
		categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
		title = requireText(title, "title must not be blank");
		descriptionMarkdown = descriptionMarkdown == null ? "" : descriptionMarkdown;
		if (maxPoints < 0) {
			throw new IllegalArgumentException("maxPoints must not be negative");
		}
	}

	private static String requireText(final String value, final String message) {
		final String trimmedValue = Objects.requireNonNull(value, message).trim();
		if (trimmedValue.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return trimmedValue;
	}
}
