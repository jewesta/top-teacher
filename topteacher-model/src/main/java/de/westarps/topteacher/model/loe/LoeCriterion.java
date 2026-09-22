package de.westarps.topteacher.model.loe;

public record LoeCriterion(Integer id, Integer requirementId, String criterionKey, String label, int pointUnits,
		int sortOrder, boolean active) {

	public LoeCriterion {
		if (requirementId == null) {
			throw new IllegalArgumentException("requirementId must not be null");
		}
		if (criterionKey == null || criterionKey.isBlank()) {
			throw new IllegalArgumentException("criterionKey must not be blank");
		}
		if (label == null || label.isBlank()) {
			throw new IllegalArgumentException("label must not be blank");
		}
		if (pointUnits <= 0) {
			throw new IllegalArgumentException("pointUnits must be positive");
		}
		if (sortOrder < 0) {
			throw new IllegalArgumentException("sortOrder must not be negative");
		}
	}

	public LoeCriterion(final Integer id, final Integer requirementId, final String criterionKey, final String label,
			final int sortOrder, final boolean active) {
		this(id, requirementId, criterionKey, label, LoePointUnits.UNITS_PER_POINT, sortOrder, active);
	}
}
