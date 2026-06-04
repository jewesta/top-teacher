package de.topteacher.model;

public enum CoursePeriod {
	FIRST_HALF("1. Hj."), SECOND_HALF("2. Hj."), FULL_YEAR("Ganzjahr");

	private final String displayName;

	CoursePeriod(final String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
