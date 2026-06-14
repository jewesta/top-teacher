package de.westarps.topteacher.model;

import java.util.Arrays;

public enum GradeLevel implements HasDisplayName {

	SEHR_GUT_PLUS(15, "sehr gut plus", "1+"), SEHR_GUT(14, "sehr gut", "1"), SEHR_GUT_MINUS(13, "sehr gut minus", "1-"),
	GUT_PLUS(12, "gut plus", "2+"), GUT(11, "gut", "2"), GUT_MINUS(10, "gut minus", "2-"),
	BEFRIEDIGEND_PLUS(9, "befriedigend plus", "3+"), BEFRIEDIGEND(8, "befriedigend", "3"),
	BEFRIEDIGEND_MINUS(7, "befriedigend minus", "3-"), AUSREICHEND_PLUS(6, "ausreichend plus", "4+"),
	AUSREICHEND(5, "ausreichend", "4"), AUSREICHEND_MINUS(4, "ausreichend minus", "4-"),
	MANGELHAFT_PLUS(3, "mangelhaft plus", "5+"), MANGELHAFT(2, "mangelhaft", "5"),
	MANGELHAFT_MINUS(1, "mangelhaft minus", "5-"), UNGENUEGEND(0, "ungenügend", "6");

	private final int points;
	private final String displayName;
	private final String shortName;

	GradeLevel(final int points, final String displayName, final String shortName) {
		this.points = points;
		this.displayName = displayName;
		this.shortName = shortName;
	}

	public int getPoints() {
		return points;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	public String getShortName() {
		return shortName;
	}

	public static GradeLevel fromPoints(final int points) {
		return Arrays.stream(values()).filter(gradeLevel -> gradeLevel.points == points).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown grade points: " + points));
	}
}
