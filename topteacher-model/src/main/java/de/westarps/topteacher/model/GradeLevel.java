package de.westarps.topteacher.model;

import java.util.Arrays;

public enum GradeLevel {

	SEHR_GUT_PLUS(15, "sehr gut plus"), SEHR_GUT(14, "sehr gut"), SEHR_GUT_MINUS(13, "sehr gut minus"),
	GUT_PLUS(12, "gut plus"), GUT(11, "gut"), GUT_MINUS(10, "gut minus"), BEFRIEDIGEND_PLUS(9, "befriedigend plus"),
	BEFRIEDIGEND(8, "befriedigend"), BEFRIEDIGEND_MINUS(7, "befriedigend minus"),
	AUSREICHEND_PLUS(6, "ausreichend plus"), AUSREICHEND(5, "ausreichend"), AUSREICHEND_MINUS(4, "ausreichend minus"),
	MANGELHAFT_PLUS(3, "mangelhaft plus"), MANGELHAFT(2, "mangelhaft"), MANGELHAFT_MINUS(1, "mangelhaft minus"),
	UNGENUEGEND(0, "ungenügend");

	private final int points;
	private final String displayName;

	GradeLevel(final int points, final String displayName) {
		this.points = points;
		this.displayName = displayName;
	}

	public int getPoints() {
		return points;
	}

	public String getDisplayName() {
		return displayName;
	}

	public static GradeLevel fromPoints(final int points) {
		return Arrays.stream(values()).filter(gradeLevel -> gradeLevel.points == points).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown grade points: " + points));
	}
}
