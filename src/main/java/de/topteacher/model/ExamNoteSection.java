package de.topteacher.model;

import java.util.Objects;

public record ExamNoteSection(Integer id, Integer examId, String title, String descriptionMarkdown, int sortOrder) {

	public ExamNoteSection {
		examId = Objects.requireNonNull(examId, "examId must not be null");
		title = requireText(title, "title must not be blank");
		descriptionMarkdown = descriptionMarkdown == null ? "" : descriptionMarkdown;
	}

	private static String requireText(final String value, final String message) {
		final String trimmedValue = Objects.requireNonNull(value, message).trim();
		if (trimmedValue.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return trimmedValue;
	}
}
