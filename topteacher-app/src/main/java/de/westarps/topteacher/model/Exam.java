package de.westarps.topteacher.model;

import java.time.LocalDate;

public record Exam(Integer id, Integer courseId, String title, LocalDate date, int maxPoints) {

	public Exam(final Integer id, final Integer courseId, final String title, final LocalDate date) {
		this(id, courseId, title, date, 0);
	}

	public Exam {
		if (courseId == null) {
			throw new IllegalArgumentException("courseId must not be null");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		if (date == null) {
			throw new IllegalArgumentException("date must not be null");
		}
		if (maxPoints < 0) {
			throw new IllegalArgumentException("maxPoints must not be negative");
		}
	}
}
