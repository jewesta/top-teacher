package de.westarps.topteacher.model.loe;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoeMaterialImageReferences {

	private static final Pattern REFERENCE = Pattern
			.compile("\\(img:([a-z0-9]{8})(?:\\?width=(?:100|[1-9][0-9]?))?\\)");

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
}
