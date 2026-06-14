package de.westarps.topteacher.model;

public enum Half implements HasDisplayName {

	FIRST("1. Hj."), SECOND("2. Hj.");

	private final String displayName;

	Half(final String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

}
