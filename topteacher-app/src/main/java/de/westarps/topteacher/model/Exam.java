package de.westarps.topteacher.model;

import java.time.LocalDate;

public record Exam(Integer id, Integer courseId, String title, LocalDate date) {

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
	}

}
