package de.westarps.topteacher.model;

public record EhCriterion(Integer id, Integer requirementId, String criterionKey, String label, int sortOrder,
		boolean active) {

	public EhCriterion {
		if (requirementId == null) {
			throw new IllegalArgumentException("requirementId must not be null");
		}
		if (criterionKey == null || criterionKey.isBlank()) {
			throw new IllegalArgumentException("criterionKey must not be blank");
		}
		if (label == null || label.isBlank()) {
			throw new IllegalArgumentException("label must not be blank");
		}
		if (sortOrder < 0) {
			throw new IllegalArgumentException("sortOrder must not be negative");
		}
	}
}
