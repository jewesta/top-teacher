package de.westarps.topteacher.model.loe;

import java.util.Objects;

public record LoeMaterial(Integer id, Integer partId, String title, String descriptionMarkdown, int sortOrder) {

	public LoeMaterial {
		partId = Objects.requireNonNull(partId, "partId must not be null");
		title = requireText(title, "title must not be blank");
		descriptionMarkdown = descriptionMarkdown == null ? "" : descriptionMarkdown;
		if (sortOrder < 0) {
			throw new IllegalArgumentException("sortOrder must not be negative");
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
