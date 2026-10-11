package de.westarps.topteacher.model;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/** Maps a client-visible shared-folder root to the corresponding server root. */
public record SharedFolderMapping(String clientPath, String serverPath, int sortOrder) {
	public static final int MAXIMUM_PATH_LENGTH = 1_000;

	public SharedFolderMapping {
		clientPath = normalizedAbsolutePath(clientPath, "clientPath");
		serverPath = normalizedAbsolutePath(serverPath, "serverPath");
		if (sortOrder < 0) {
			throw new IllegalArgumentException("sortOrder must not be negative");
		}
	}

	private static String normalizedAbsolutePath(final String value, final String name) {
		Objects.requireNonNull(value, name + " must not be null");
		final String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		try {
			final Path path = Path.of(trimmed).normalize();
			if (!path.isAbsolute()) {
				throw new IllegalArgumentException(name + " must be absolute");
			}
			final String normalized = path.toString();
			if (normalized.length() > MAXIMUM_PATH_LENGTH) {
				throw new IllegalArgumentException(name + " must not exceed " + MAXIMUM_PATH_LENGTH + " characters");
			}
			return normalized;
		} catch (final InvalidPathException exception) {
			throw new IllegalArgumentException(name + " is invalid", exception);
		}
	}
}
