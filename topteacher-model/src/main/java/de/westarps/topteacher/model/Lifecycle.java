package de.westarps.topteacher.model;

public enum Lifecycle implements HasDisplayName {

	ACTIVE("Aktiv"), INACTIVE("Inaktiv");

	private final String displayName;

	Lifecycle(final String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}
}
