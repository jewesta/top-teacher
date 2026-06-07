package de.westarps.topteacher.model.loe;

public record LoeCriterionResult(Integer criterionId, Integer pupilId, boolean achieved) {

	public LoeCriterionResult {
		if (criterionId == null) {
			throw new IllegalArgumentException("criterionId must not be null");
		}
		if (pupilId == null) {
			throw new IllegalArgumentException("pupilId must not be null");
		}
	}
}
