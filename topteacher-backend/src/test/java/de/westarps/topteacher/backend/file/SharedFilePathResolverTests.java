package de.westarps.topteacher.backend.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.westarps.topteacher.backend.repo.SharedFolderMappingRepository;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.SharedFolderMapping;

class SharedFilePathResolverTests {

	@TempDir
	private Path tempDir;

	@Test
	void resolvesAClientPathThroughTheMostSpecificMapping() throws Exception {
		final Path generalRoot = Files.createDirectory(tempDir.resolve("general"));
		final Path examRoot = Files.createDirectory(tempDir.resolve("exams"));
		final Path material = Files.writeString(examRoot.resolve("material.png"), "image");
		final SharedFolderMappingRepository repository = mock(SharedFolderMappingRepository.class);
		when(repository.findAll()).thenReturn(List.of(
				new SharedFolderMapping("/Volumes/School", generalRoot.toString(), 0),
				new SharedFolderMapping("/Volumes/School/Exams", examRoot.toString(), 1)));

		final Path resolved = resolver(repository, InstallationLocation.REMOTE)
				.resolveReadableFile("/Volumes/School/Exams/material.png");

		assertThat(resolved).isEqualTo(material.toRealPath());
	}

	@Test
	void rejectsRelativeAndUnmappedPaths() {
		final SharedFolderMappingRepository repository = mock(SharedFolderMappingRepository.class);
		when(repository.findAll()).thenReturn(List.of());
		final SharedFilePathResolver resolver = resolver(repository, InstallationLocation.REMOTE);

		assertThatThrownBy(() -> resolver.resolveReadableFile("material.png"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absolute");
		assertThatThrownBy(() -> resolver.resolveReadableFile("/Volumes/Unknown/material.png"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No shared-folder mapping");
	}

	@Test
	void rejectsFilesThatEscapeTheServerRootThroughASymbolicLink() throws Exception {
		final Path serverRoot = Files.createDirectory(tempDir.resolve("server"));
		final Path outside = Files.createDirectory(tempDir.resolve("outside"));
		Files.writeString(outside.resolve("secret.png"), "secret");
		Files.createSymbolicLink(serverRoot.resolve("linked"), outside);
		final SharedFolderMappingRepository repository = mock(SharedFolderMappingRepository.class);
		when(repository.findAll()).thenReturn(
				List.of(new SharedFolderMapping("/Volumes/Share", serverRoot.toString(), 0)));

		assertThatThrownBy(() -> resolver(repository, InstallationLocation.REMOTE)
				.resolveReadableFile("/Volumes/Share/linked/secret.png"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside");
	}

	@Test
	void resolvesLocalFilesDirectlyWithoutReadingMappings() throws Exception {
		final Path material = Files.writeString(tempDir.resolve("material.png"), "image");
		final Path link = Files.createSymbolicLink(tempDir.resolve("linked.png"), material);
		final SharedFolderMappingRepository repository = mock(SharedFolderMappingRepository.class);
		final SharedFilePathResolver resolver = resolver(repository, InstallationLocation.LOCAL);

		assertThat(resolver.resolveReadableFile(material.toString())).isEqualTo(material.toRealPath());
		assertThat(resolver.resolveReadableFile(link.toString())).isEqualTo(material.toRealPath());
		verifyNoInteractions(repository);
	}

	@Test
	void rejectsRelativeMissingAndDirectoryPathsInLocalMode() {
		final SharedFolderMappingRepository repository = mock(SharedFolderMappingRepository.class);
		final SharedFilePathResolver resolver = resolver(repository, InstallationLocation.LOCAL);

		assertThatThrownBy(() -> resolver.resolveReadableFile("material.png"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absolute");
		assertThatThrownBy(() -> resolver.resolveReadableFile(tempDir.resolve("missing.png").toString()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unavailable");
		assertThatThrownBy(() -> resolver.resolveReadableFile(tempDir.toString()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("readable regular file");
		verifyNoInteractions(repository);
	}

	private static SharedFilePathResolver resolver(final SharedFolderMappingRepository repository,
			final InstallationLocation location) {
		final AppSettings appSettings = mock(AppSettings.class);
		when(appSettings.ttFileAccessInstallationLocation()).thenReturn(location);
		return new SharedFilePathResolver(repository, appSettings);
	}
}
