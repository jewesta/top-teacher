package de.westarps.vaadin.markdown;

public record MarkdownTagValueOption(String value, String label) {

	public MarkdownTagValueOption {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("value must not be blank");
		}
		if (label == null || label.isBlank()) {
			throw new IllegalArgumentException("label must not be blank");
		}
	}
}
