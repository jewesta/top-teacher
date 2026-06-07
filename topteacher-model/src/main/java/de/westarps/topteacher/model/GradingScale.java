package de.westarps.topteacher.model;

import java.util.Objects;

public record GradingScale(Integer id, String name, int maxPoints, Lifecycle lifecycle) {

	public GradingScale {
		name = Objects.requireNonNull(name, "name must not be null").trim();
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (maxPoints < 0) {
			throw new IllegalArgumentException("maxPoints must not be negative");
		}
		lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
	}

	public String getDisplayName() {
		return name + " (" + maxPoints + " Punkte)";
	}
}
