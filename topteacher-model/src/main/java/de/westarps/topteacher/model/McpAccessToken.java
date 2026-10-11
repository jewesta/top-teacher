package de.westarps.topteacher.model;

import java.time.Instant;
import java.util.Objects;

/** Public token metadata, without the credential or its stored hash. */
public record McpAccessToken(String id, String name, McpAccessLevel accessLevel, Instant createdAt, Instant lastUsedAt,
		Instant expiresAt, Instant revokedAt) {

	public static final int MAXIMUM_NAME_LENGTH = 100;

	public McpAccessToken {
		id = Objects.requireNonNull(id, "id must not be null").strip();
		if (id.isEmpty() || id.length() > 36) {
			throw new IllegalArgumentException("id must contain between 1 and 36 characters");
		}
		name = validatedName(name);
		Objects.requireNonNull(accessLevel, "accessLevel must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
	}

	public static String validatedName(final String value) {
		final String name = Objects.requireNonNull(value, "name must not be null").strip();
		if (name.isEmpty() || name.length() > MAXIMUM_NAME_LENGTH || name.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("name must contain between 1 and " + MAXIMUM_NAME_LENGTH
					+ " characters without control characters");
		}
		return name;
	}
}
