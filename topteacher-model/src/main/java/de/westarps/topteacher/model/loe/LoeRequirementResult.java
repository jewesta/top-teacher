package de.westarps.topteacher.model.loe;

public record LoeRequirementResult(Integer requirementId, Integer pupilId, int pointUnits, int adjustmentUnits,
		String comment) {

	public LoeRequirementResult(final Integer requirementId, final Integer pupilId, final int points) {
		this(requirementId, pupilId, LoePointUnits.fromWholePoints(points), 0, "");
	}

	public LoeRequirementResult(final Integer requirementId, final Integer pupilId, final int points,
			final String comment) {
		this(requirementId, pupilId, LoePointUnits.fromWholePoints(points), 0, comment);
	}

	public LoeRequirementResult {
		if (requirementId == null) {
			throw new IllegalArgumentException("requirementId must not be null");
		}
		if (pupilId == null) {
			throw new IllegalArgumentException("pupilId must not be null");
		}
		if (pointUnits < 0 || adjustmentUnits < 0) {
			throw new IllegalArgumentException("point units must not be negative");
		}
		if (adjustmentUnits > pointUnits) {
			throw new IllegalArgumentException("adjustmentUnits must not exceed pointUnits");
		}
		comment = comment == null ? "" : comment;
	}

	public int points() {
		return LoePointUnits.roundedWholePoints(pointUnits);
	}
}
