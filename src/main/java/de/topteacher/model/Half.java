package de.topteacher.model;

public enum Half {

	FIRST("1. Hj."), SECOND("2. Hj.");

	private final String displayName;

	Half(final String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

}
