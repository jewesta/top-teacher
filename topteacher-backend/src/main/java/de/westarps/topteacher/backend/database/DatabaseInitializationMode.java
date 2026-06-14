package de.westarps.topteacher.backend.database;

import de.westarps.topteacher.model.HasDisplayName;

public enum DatabaseInitializationMode implements HasDisplayName {

	EMPTY("Leere Datenbank"), DEMO("Demodaten");

	private final String displayName;

	DatabaseInitializationMode(final String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

}
