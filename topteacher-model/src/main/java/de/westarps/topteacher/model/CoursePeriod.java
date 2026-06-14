package de.westarps.topteacher.model;

public enum CoursePeriod implements HasDisplayName {

	FIRST_HALF("1. Hj."), SECOND_HALF("2. Hj."), FULL_YEAR("Ganzjahr");

	private final String displayName;

	CoursePeriod(final String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

}
