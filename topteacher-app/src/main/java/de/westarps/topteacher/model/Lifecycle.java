package de.westarps.topteacher.model;

public enum Lifecycle {

	ACTIVE("Aktiv"), INACTIVE("Inaktiv");

	private final String displayName;

	Lifecycle(final String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
