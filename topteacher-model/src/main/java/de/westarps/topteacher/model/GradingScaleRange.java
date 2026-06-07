package de.westarps.topteacher.model;

import java.util.Objects;

public record GradingScaleRange(Integer id, Integer gradingScaleId, GradeLevel gradeLevel, int minPoints,
		int maxPoints) {

	public GradingScaleRange {
		gradingScaleId = Objects.requireNonNull(gradingScaleId, "gradingScaleId must not be null");
		gradeLevel = Objects.requireNonNull(gradeLevel, "gradeLevel must not be null");
		if (minPoints < 0) {
			throw new IllegalArgumentException("minPoints must not be negative");
		}
		if (maxPoints < minPoints) {
			throw new IllegalArgumentException("maxPoints must be greater than or equal to minPoints");
		}
	}

	public String getPointRangeDisplayName() {
		return minPoints + " - " + maxPoints;
	}
}
