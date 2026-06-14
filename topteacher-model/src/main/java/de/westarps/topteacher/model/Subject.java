package de.westarps.topteacher.model;

import java.util.Objects;

public record Subject(Integer id, String name, Lifecycle lifecycle) implements HasDisplayName {

	public Subject {
		name = Objects.requireNonNull(name, "name must not be null").trim();
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
	}

	@Override
	public String getDisplayName() {
		return name;
	}
}
