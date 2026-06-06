package de.westarps.topteacher.model;

public record EhRequirementResult(Integer requirementId, Integer pupilId, int points, String comment) {

	public EhRequirementResult(final Integer requirementId, final Integer pupilId, final int points) {
		this(requirementId, pupilId, points, "");
	}

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
		comment = comment == null ? "" : comment;
	}
}
