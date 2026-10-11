package de.westarps.topteacher.backend.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.repo.SharedFolderMappingRepository;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.SharedFolderMapping;

@Service
public class SharedFilePathResolver {

	private final SharedFolderMappingRepository mappings;
	private final AppSettings appSettings;

	public SharedFilePathResolver(final SharedFolderMappingRepository mappings, final AppSettings appSettings) {
		this.mappings = Objects.requireNonNull(mappings, "mappings");
		this.appSettings = Objects.requireNonNull(appSettings, "appSettings");
	}

	public Path resolveReadableFile(final String clientFilePath) {
		final Path clientFile = absoluteNormalizedPath(clientFilePath, "client file path");
		if (appSettings.ttFileAccessInstallationLocation() == InstallationLocation.LOCAL) {
			return resolveLocalFile(clientFile);
		}
		final SharedFolderMapping mapping = mappings.findAll().stream()
				.filter(candidate -> clientFile.startsWith(absoluteNormalizedPath(candidate.clientPath(), "client root")))
				.max(Comparator.comparingInt(candidate -> absoluteNormalizedPath(candidate.clientPath(), "client root")
						.getNameCount()))
				.orElseThrow(() -> new IllegalArgumentException(
						"No shared-folder mapping matches client file path: " + clientFile));

		final Path clientRoot = absoluteNormalizedPath(mapping.clientPath(), "client root");
		final Path configuredServerRoot = absoluteNormalizedPath(mapping.serverPath(), "server root");
		final Path configuredTarget = configuredServerRoot.resolve(clientRoot.relativize(clientFile)).normalize();

		try {
			final Path serverRoot = configuredServerRoot.toRealPath();
			final Path target = configuredTarget.toRealPath();
			if (!target.startsWith(serverRoot)) {
				throw new IllegalArgumentException("Mapped file resolves outside the configured server root.");
			}
			if (!Files.isRegularFile(target) || !Files.isReadable(target)) {
				throw new IllegalArgumentException("Mapped file is not a readable regular file: " + target);
			}
			return target;
		} catch (final IOException | SecurityException exception) {
			throw new IllegalArgumentException("Mapped file is unavailable: " + configuredTarget, exception);
		}
	}

	private static Path resolveLocalFile(final Path clientFile) {
		try {
			final Path target = clientFile.toRealPath();
			if (!Files.isRegularFile(target) || !Files.isReadable(target)) {
				throw new IllegalArgumentException("Local file is not a readable regular file: " + target);
			}
			return target;
		} catch (final IOException | SecurityException exception) {
			throw new IllegalArgumentException("Local file is unavailable: " + clientFile, exception);
		}
	}

	private static Path absoluteNormalizedPath(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		try {
			final Path path = Path.of(value.trim()).normalize();
			if (!path.isAbsolute()) {
				throw new IllegalArgumentException(name + " must be absolute: " + value);
			}
			return path;
		} catch (final InvalidPathException exception) {
			throw new IllegalArgumentException(name + " is invalid: " + value, exception);
		}
	}
}
