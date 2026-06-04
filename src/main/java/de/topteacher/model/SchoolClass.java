package de.topteacher.model;

public enum SchoolClass {

	CLS_5A("5a"), CLS_5B("5b"), CLS_5C("5c"), CLS_5D("5d"), CLS_5E("5e"), CLS_5F("5f"),

	CLS_6A("6a"), CLS_6B("6b"), CLS_6C("6c"), CLS_6D("6d"), CLS_6E("6e"), CLS_6F("6f"),

	CLS_7A("7a"), CLS_7B("7b"), CLS_7C("7c"), CLS_7D("7d"), CLS_7E("7e"), CLS_7F("7f"),

	CLS_8A("8a"), CLS_8B("8b"), CLS_8C("8c"), CLS_8D("8d"), CLS_8E("8e"), CLS_8F("8f"),

	CLS_9A("9a"), CLS_9B("9b"), CLS_9C("9c"), CLS_9D("9d"), CLS_9E("9e"), CLS_9F("9f"),

	CLS_10A("10a"), CLS_10B("10b"), CLS_10C("10c"), CLS_10D("10d"), CLS_10E("10e"), CLS_10F("10f"),

	CLS_EF("EF"), CLS_Q1("Q1"), CLS_Q2("Q2");

	private final String displayName;

	SchoolClass(final String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

}
