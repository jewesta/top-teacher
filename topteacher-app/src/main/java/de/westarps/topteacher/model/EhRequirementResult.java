package de.westarps.topteacher.model;

public record EhRequirementResult(Integer requirementId, Integer pupilId, int points) {

	public EhRequirementResult {
		if (requirementId == null) {
			throw new IllegalArgumentException("requirementId must not be null");
		}
		if (pupilId == null) {
			throw new IllegalArgumentException("pupilId must not be null");
		}
		if (points < 0) {
			throw new IllegalArgumentException("points must not be negative");
		}
	}
}
