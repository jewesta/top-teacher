package de.westarps.topteacher.model;

public record EhCriterionResult(Integer criterionId, Integer pupilId, boolean achieved) {

	public EhCriterionResult {
		if (criterionId == null) {
			throw new IllegalArgumentException("criterionId must not be null");
		}
		if (pupilId == null) {
			throw new IllegalArgumentException("pupilId must not be null");
		}
	}
}
