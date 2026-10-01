package de.westarps.topteacher.model.loe;

import java.util.List;

public record LoeCriterionParseResult(List<LoeCriterion> criteria, List<LoeCriterionIssue> issues, int tagCount) {

	public LoeCriterionParseResult {
		criteria = criteria == null ? List.of() : List.copyOf(criteria);
		issues = issues == null ? List.of() : List.copyOf(issues);
		if (tagCount < 0) {
			throw new IllegalArgumentException("tagCount must not be negative");
		}
	}

	public boolean hasTags() {
		return tagCount > 0;
	}
}
