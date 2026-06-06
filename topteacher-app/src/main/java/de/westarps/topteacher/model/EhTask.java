package de.westarps.topteacher.model;

import java.util.Objects;

public record EhTask(Integer id, Integer categoryId, String title, int sortOrder) {

	public EhTask {
		categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
		title = requireText(title, "title must not be blank");
	}

	private static String requireText(final String value, final String message) {
		final String trimmedValue = Objects.requireNonNull(value, message).trim();
		if (trimmedValue.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return trimmedValue;
	}
}
