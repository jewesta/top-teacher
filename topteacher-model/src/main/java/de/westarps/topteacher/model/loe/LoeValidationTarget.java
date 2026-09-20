package de.westarps.topteacher.model.loe;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public record LoeValidationTarget(Kind kind, Integer requirementId) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public enum Kind {
		TOTAL_POINTS,
		REQUIREMENT_CRITERIA
	}

	public LoeValidationTarget {
		kind = Objects.requireNonNull(kind, "kind must not be null");
		if (kind == Kind.TOTAL_POINTS && requirementId != null) {
			throw new IllegalArgumentException("total-points target must not reference a requirement");
		}
	}

	public static LoeValidationTarget totalPoints() {
		return new LoeValidationTarget(Kind.TOTAL_POINTS, null);
	}

	public static LoeValidationTarget requirementCriteria(final Integer requirementId) {
		return new LoeValidationTarget(Kind.REQUIREMENT_CRITERIA, requirementId);
	}
}
