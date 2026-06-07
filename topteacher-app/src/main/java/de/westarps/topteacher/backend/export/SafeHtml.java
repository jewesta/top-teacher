package de.westarps.topteacher.backend.export;

public record SafeHtml(String value) {

	public SafeHtml {
		value = value == null ? "" : value;
	}
}
