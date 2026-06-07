package de.westarps.vaadin.markdown;

import java.util.regex.Pattern;

public record MarkdownTag(String namespace, String toolbarLabel, MarkdownTagIdGenerator idGenerator) {

	private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*");
	private static final String DEFAULT_TOOLBAR_LABEL = "Tag markieren";

	public MarkdownTag {
		if (namespace == null || !NAMESPACE_PATTERN.matcher(namespace).matches()) {
			throw new IllegalArgumentException(
					"Markdown tag namespace must be a URI-scheme-like value, e.g. 'tag' or 'note'.");
		}
		toolbarLabel = toolbarLabel == null || toolbarLabel.isBlank() ? DEFAULT_TOOLBAR_LABEL : toolbarLabel;
		idGenerator = idGenerator == null ? MarkdownTagIdGenerator.NEXT_NUMBER : idGenerator;
	}

	public static MarkdownTag nextNumber(final String namespace, final String toolbarLabel) {
		return new MarkdownTag(namespace, toolbarLabel, MarkdownTagIdGenerator.NEXT_NUMBER);
	}
}
