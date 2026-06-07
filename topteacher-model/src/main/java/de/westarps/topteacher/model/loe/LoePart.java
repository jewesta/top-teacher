package de.westarps.topteacher.model.loe;

import java.util.Objects;

public record LoePart(Integer id, Integer examId, String title, int sortOrder) {

	public LoePart {
		examId = Objects.requireNonNull(examId, "examId must not be null");
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
