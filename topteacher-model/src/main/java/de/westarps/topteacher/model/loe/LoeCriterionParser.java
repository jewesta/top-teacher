package de.westarps.topteacher.model.loe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.westarps.topteacher.model.loe.LoeCriterionIssue.Kind;

public final class LoeCriterionParser {

	public static final String TAG_NAMESPACE = "eh";

	private static final Pattern CRITERION_PATTERN = Pattern
			.compile("\\[([^\\]\\n]+)]\\(" + TAG_NAMESPACE + ":([^\\s)]+)\\)");
	private static final Pattern CRITERION_KEY_PATTERN = Pattern.compile("[1-9]\\d*");

	private LoeCriterionParser() {
	}

	public static List<LoeCriterion> parse(final int requirementId, final String markdown) {
		return analyze(requirementId, markdown).criteria();
	}

	public static LoeCriterionParseResult analyze(final int requirementId, final String markdown) {
		if (markdown == null || markdown.isBlank()) {
			return new LoeCriterionParseResult(List.of(), List.of(), 0);
		}

		final List<LoeCriterion> criteria = new ArrayList<>();
		final List<LoeCriterionIssue> issues = new ArrayList<>();
		final Set<String> usedKeys = new LinkedHashSet<>();
		final Matcher matcher = CRITERION_PATTERN.matcher(markdown);
		int tagCount = 0;
		while (matcher.find()) {
			tagCount++;
			final String label = label(matcher.group(1));
			final String target = matcher.group(2).trim();
			final int separatorIndex = target.indexOf('/');
			final String criterionKey = separatorIndex < 0 ? target : target.substring(0, separatorIndex);
			final String pointValue = separatorIndex < 0 ? null : target.substring(separatorIndex + 1);

			if (label.isBlank()) {
				issues.add(new LoeCriterionIssue(Kind.INVALID_LABEL, ""));
				continue;
			}
			if (!CRITERION_KEY_PATTERN.matcher(criterionKey).matches()) {
				issues.add(new LoeCriterionIssue(Kind.INVALID_KEY, label));
				continue;
			}
			if (!usedKeys.add(criterionKey)) {
				issues.add(new LoeCriterionIssue(Kind.DUPLICATE_KEY, label));
				continue;
			}

			final OptionalInt pointUnits = pointValue == null ? OptionalInt.of(LoePointUnits.UNITS_PER_POINT)
					: pointValue.contains("/") ? OptionalInt.empty() : LoePointUnits.parsePositive(pointValue);
			if (pointUnits.isEmpty()) {
				issues.add(new LoeCriterionIssue(Kind.INVALID_POINTS, label));
				continue;
			}
			criteria.add(new LoeCriterion(null, requirementId, criterionKey, label, pointUnits.getAsInt(),
					criteria.size(), true));
		}
		return new LoeCriterionParseResult(criteria, issues, tagCount);
	}

	private static String label(final String markdownLabel) {
		return markdownLabel.replaceAll("\\\\([\\\\`*_{}\\[\\]()#+\\-.!])", "$1").replace("**", "")
				.replace("__", "").replace("*", "").replace("_", "").replace("`", "").trim();
	}
}
