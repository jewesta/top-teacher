package de.westarps.topteacher.model.loe;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoeMaterialImageReferences {

	private static final Pattern REFERENCE = Pattern
			.compile("\\(img:([a-z0-9]{8})(?:\\?width=(?:100|[1-9][0-9]?))?\\)");
	private static final Pattern IMAGE_REFERENCE = Pattern.compile(
			"!\\[((?:\\\\[\\s\\S]|[^\\\\\\]])*)\\]\\(img:([a-z0-9]{8})(?:\\?width=(100|[1-9][0-9]?))?\\)");

	private LoeMaterialImageReferences() {
	}

	public static Set<String> ids(final String markdown) {
		final Set<String> ids = new LinkedHashSet<>();
		final Matcher matcher = REFERENCE.matcher(markdown == null ? "" : markdown);
		while (matcher.find()) {
			ids.add(matcher.group(1));
		}
		return Set.copyOf(ids);
	}

	public static String replaceId(final String markdown, final String currentId, final String replacementId) {
		if (markdown == null || markdown.isEmpty() || currentId.equals(replacementId)) {
			return markdown == null ? "" : markdown;
		}
		return markdown.replace("(img:" + currentId + ")", "(img:" + replacementId + ")")
				.replace("(img:" + currentId + "?", "(img:" + replacementId + "?");
	}

	public static Optional<Occurrence> findAt(final String markdown, final int selectionStart, final int selectionEnd) {
		final String source = markdown == null ? "" : markdown;
		final int start = Math.max(0, Math.min(selectionStart, source.length()));
		final int end = Math.max(start, Math.min(selectionEnd, source.length()));
		final Matcher matcher = IMAGE_REFERENCE.matcher(source);
		while (matcher.find()) {
			final boolean containsCaret = start == end && start >= matcher.start() && start <= matcher.end();
			final boolean intersectsSelection = start < end && start < matcher.end() && end > matcher.start();
			if (containsCaret || intersectsSelection) {
				return Optional.of(new Occurrence(matcher.group(2), unescapeDescription(matcher.group(1)),
						matcher.group(3) == null ? null : Integer.valueOf(matcher.group(3)), matcher.start(),
						matcher.end()));
			}
		}
		return Optional.empty();
	}

	private static String unescapeDescription(final String value) {
		final StringBuilder unescaped = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			final char character = value.charAt(index);
			if (character == '\\' && index + 1 < value.length()
					&& (value.charAt(index + 1) == '\\' || value.charAt(index + 1) == ']')) {
				unescaped.append(value.charAt(++index));
			} else {
				unescaped.append(character);
			}
		}
		return unescaped.toString();
	}

	public record Occurrence(String id, String description, Integer width, int start, int end) {
	}
}
