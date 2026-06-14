package de.westarps.topteacher.model;

import java.time.LocalDate;

public record Exam(Integer id, Integer courseId, String title, LocalDate date, Integer originalExamId,
		Integer gradingScaleId) {

	public Exam(final Integer id, final Integer courseId, final String title, final LocalDate date) {
		this(id, courseId, title, date, null, null);
	}

	public Exam(final Integer id, final Integer courseId, final String title, final LocalDate date,
			final Integer originalExamId) {
		this(id, courseId, title, date, originalExamId, null);
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
		if (id != null && id.equals(originalExamId)) {
			throw new IllegalArgumentException("originalExamId must not reference the same exam");
		}
	}

	public Exam withGradingScaleId(final Integer gradingScaleId) {
		return new Exam(id, courseId, title, date, originalExamId, gradingScaleId);
	}

	public boolean isMakeupExam() {
		return originalExamId != null;
	}
}
