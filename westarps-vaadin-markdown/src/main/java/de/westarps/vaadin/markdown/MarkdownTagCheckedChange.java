package de.westarps.vaadin.markdown;

public record MarkdownTagCheckedChange(String key, boolean checked) {

	public MarkdownTagCheckedChange {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("key must not be blank");
		}
	}
}
