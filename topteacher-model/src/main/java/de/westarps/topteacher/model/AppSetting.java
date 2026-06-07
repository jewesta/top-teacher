package de.westarps.topteacher.model;

import java.util.Objects;

public record AppSetting(String key, String value) {

	public AppSetting {
		key = Objects.requireNonNull(key, "key must not be null").trim();
		if (key.isBlank()) {
			throw new IllegalArgumentException("key must not be blank");
		}
		value = Objects.requireNonNull(value, "value must not be null");
	}
}
