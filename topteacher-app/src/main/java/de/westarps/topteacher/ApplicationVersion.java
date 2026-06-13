package de.westarps.topteacher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

public final class ApplicationVersion {

	public static final String COPYRIGHT = "\u00A9 2026 Jens Westarp";
	public static final String DISPLAY_APP_NAME = "TopTeacher!";
	private static final String APP_VERSION_PROPERTY = "tt.app.version";
	private static final String JPACKAGE_VERSION_PROPERTY = "jpackage.app-version";
	private static final String BUILD_VERSION_RESOURCE = "/topteacher-build.properties";
	private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";
	private static final String MINIMUM_DISPLAY_VERSION = "1.0.0";

	private ApplicationVersion() {
	}

	public static Optional<String> displayVersion() {
		return normalizedDisplayVersion(System.getProperty(APP_VERSION_PROPERTY))
				.or(() -> normalizedDisplayVersion(System.getProperty(JPACKAGE_VERSION_PROPERTY)))
				.or(ApplicationVersion::buildResourceVersion)
				.or(() -> normalizedDisplayVersion(packageImplementationVersion()));
	}

	static Optional<String> normalizedDisplayVersion(final String version) {
		if (version == null || version.isBlank()) {
			return Optional.empty();
		}

		String normalizedVersion = version.trim();
		if (normalizedVersion.contains("${")
				|| (normalizedVersion.startsWith("@") && normalizedVersion.endsWith("@"))) {
			return Optional.empty();
		}
		if (normalizedVersion.endsWith(SNAPSHOT_SUFFIX)) {
			normalizedVersion = normalizedVersion.substring(0, normalizedVersion.length() - SNAPSHOT_SUFFIX.length());
		}
		if (normalizedVersion.equals("0") || normalizedVersion.startsWith("0.")) {
			normalizedVersion = MINIMUM_DISPLAY_VERSION;
		}
		return Optional.of(normalizedVersion);
	}

	static Optional<String> buildResourceVersion() {
		final Properties buildProperties = new Properties();
		try (InputStream inputStream = ApplicationVersion.class.getResourceAsStream(BUILD_VERSION_RESOURCE)) {
			if (inputStream == null) {
				return Optional.empty();
			}
			buildProperties.load(inputStream);
		} catch (final IOException exception) {
			return Optional.empty();
		}
		return normalizedDisplayVersion(buildProperties.getProperty(APP_VERSION_PROPERTY));
	}

	private static String packageImplementationVersion() {
		return TopTeacherApplication.class.getPackage().getImplementationVersion();
	}
}
