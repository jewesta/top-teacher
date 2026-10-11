package de.westarps.topteacher.model.loe;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public record LoeValidationTarget(Kind kind, Integer taskId, Integer requirementId) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public enum Kind {
		TOTAL_POINTS,
		TASK_POINTS,
		REQUIREMENT_CRITERIA
	}

	public LoeValidationTarget {
		kind = Objects.requireNonNull(kind, "kind must not be null");
		if (kind == Kind.TOTAL_POINTS && (taskId != null || requirementId != null)) {
			throw new IllegalArgumentException("total-points target must not reference a task or requirement");
		}
		if (kind == Kind.TASK_POINTS && requirementId != null) {
			throw new IllegalArgumentException("task-points target must not reference a requirement");
		}
		if (kind == Kind.REQUIREMENT_CRITERIA && taskId != null) {
			throw new IllegalArgumentException("requirement-criteria target must not reference a task");
		}
	}

	public static LoeValidationTarget totalPoints() {
		return new LoeValidationTarget(Kind.TOTAL_POINTS, null, null);
	}

	public static LoeValidationTarget taskPoints(final Integer taskId) {
		return new LoeValidationTarget(Kind.TASK_POINTS, taskId, null);
	}

	public static LoeValidationTarget requirementCriteria(final Integer requirementId) {
		return new LoeValidationTarget(Kind.REQUIREMENT_CRITERIA, null, requirementId);
	}
}
