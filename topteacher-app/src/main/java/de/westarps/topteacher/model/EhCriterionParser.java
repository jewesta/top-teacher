package de.westarps.topteacher.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EhCriterionParser {

	public static final String TAG_NAMESPACE = "eh";

	private static final Pattern CRITERION_PATTERN = Pattern
			.compile("\\[([^\\]\\n]+)]\\(" + TAG_NAMESPACE + ":([^\\s)]+)\\)");

	private EhCriterionParser() {
	}

	public static List<EhCriterion> parse(final int requirementId, final String markdown) {
		if (markdown == null || markdown.isBlank()) {
			return List.of();
		}

		final Matcher matcher = CRITERION_PATTERN.matcher(markdown);
		final Map<String, EhCriterion> criteriaByKey = new LinkedHashMap<>();
		while (matcher.find()) {
			final String criterionKey = matcher.group(2).trim();
			final String label = label(matcher.group(1));
			if (criterionKey.isBlank() || label.isBlank() || criteriaByKey.containsKey(criterionKey)) {
				continue;
			}
			criteriaByKey.put(criterionKey,
					new EhCriterion(null, requirementId, criterionKey, label, criteriaByKey.size(), true));
		}
		return new ArrayList<>(criteriaByKey.values());
	}

	private static String label(final String markdownLabel) {
		return markdownLabel.replaceAll("\\\\([\\\\`*_{}\\[\\]()#+\\-.!])", "$1").replace("**", "").replace("__", "")
				.replace("*", "").replace("_", "").replace("`", "").trim();
	}
}
