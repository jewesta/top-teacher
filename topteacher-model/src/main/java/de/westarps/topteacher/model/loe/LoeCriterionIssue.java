package de.westarps.topteacher.model.loe;

import java.util.Objects;

public record LoeCriterionIssue(Kind kind, String label) {

	public enum Kind {
		INVALID_KEY,
		INVALID_LABEL,
		INVALID_POINTS,
		DUPLICATE_KEY
	}

	public LoeCriterionIssue {
		kind = Objects.requireNonNull(kind, "kind must not be null");
		label = label == null ? "" : label;
	}
}
