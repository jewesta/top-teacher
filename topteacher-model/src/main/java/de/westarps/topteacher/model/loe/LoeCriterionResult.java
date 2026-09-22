package de.westarps.topteacher.model.loe;

public record LoeCriterionResult(Integer criterionId, Integer pupilId, int pointUnits) {

	public LoeCriterionResult {
		if (criterionId == null) {
			throw new IllegalArgumentException("criterionId must not be null");
		}
		if (pupilId == null) {
			throw new IllegalArgumentException("pupilId must not be null");
		}
		if (pointUnits < 0) {
			throw new IllegalArgumentException("pointUnits must not be negative");
		}
	}
}
