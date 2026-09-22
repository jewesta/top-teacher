package de.westarps.vaadin.markdown;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record MarkdownTagValueSelector(List<MarkdownTagValueOption> options, String defaultValue, String separator,
		String toolbarIconText, String customOptionLabel, String customOptionAriaLabel, String customPlaceholder,
		String customValuePattern, String removeLabel) {

	public MarkdownTagValueSelector {
		options = options == null ? List.of() : List.copyOf(options);
		if (options.isEmpty()) {
			throw new IllegalArgumentException("options must not be empty");
		}
		final Set<String> values = new HashSet<>();
		for (final MarkdownTagValueOption option : options) {
			if (option == null) {
				throw new IllegalArgumentException("options must not contain null");
			}
			if (!values.add(option.value())) {
				throw new IllegalArgumentException("option values must be unique");
			}
		}
		if (defaultValue == null || !values.contains(defaultValue)) {
			throw new IllegalArgumentException("defaultValue must identify one of the options");
		}
		separator = required(separator, "separator");
		toolbarIconText = required(toolbarIconText, "toolbarIconText");
		customOptionLabel = required(customOptionLabel, "customOptionLabel");
		customOptionAriaLabel = required(customOptionAriaLabel, "customOptionAriaLabel");
		customPlaceholder = required(customPlaceholder, "customPlaceholder");
		customValuePattern = required(customValuePattern, "customValuePattern");
		removeLabel = required(removeLabel, "removeLabel");
	}

	private static String required(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
