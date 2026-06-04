package de.topteacher.model;

public enum Subject {

	ENGLISH("Englisch"), SPANISH("Spanisch");

	private final String displayName;

	Subject(final String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

}
