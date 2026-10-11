package de.westarps.topteacher.backend.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import de.westarps.topteacher.backend.repo.SharedFolderMappingRepository;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.SharedFolderMapping;

@SpringBootTest
class FileAccessSettingsServiceTests {

	@Autowired
	private FileAccessSettingsService service;

	@Autowired
	private SharedFolderMappingRepository mappings;

	@Autowired
	private AppSettings appSettings;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void resetSettings() {
		mappings.replaceAll(List.of());
		appSettings.saveTtFileAccessInstallationLocation(InstallationLocation.LOCAL);
	}

	@Test
	void savesRemoteLocationAndOrderedMappingsTogether() {
		service.save(InstallationLocation.REMOTE, List.of(new SharedFolderMapping("/Volumes/One", "/mnt/one", 7),
				new SharedFolderMapping("/Volumes/Two", "/mnt/two", 4)));

		assertThat(service.installationLocation()).isEqualTo(InstallationLocation.REMOTE);
		assertThat(service.mappings()).containsExactly(new SharedFolderMapping("/Volumes/One", "/mnt/one", 0),
				new SharedFolderMapping("/Volumes/Two", "/mnt/two", 1));
	}

	@Test
	void savesLocalLocationWithoutValidatingOrReplacingMappings() {
		final SharedFolderMapping savedMapping = new SharedFolderMapping("/Volumes/Saved", "/mnt/saved", 0);
		service.save(InstallationLocation.REMOTE, List.of(savedMapping));

		service.save(InstallationLocation.LOCAL, null);

		assertThat(service.installationLocation()).isEqualTo(InstallationLocation.LOCAL);
		assertThat(service.mappings()).containsExactly(savedMapping);
	}

	@Test
	void rejectsInvalidRemoteMappingsWithoutChangingTheLocationOrSavedMappings() {
		final SharedFolderMapping savedMapping = new SharedFolderMapping("/Volumes/Saved", "/mnt/saved", 0);
		mappings.replaceAll(List.of(savedMapping));
		appSettings.saveTtFileAccessInstallationLocation(InstallationLocation.LOCAL);

		assertThatThrownBy(() -> service.save(InstallationLocation.REMOTE, List.of(
				new SharedFolderMapping("/Volumes/Duplicate", "/mnt/one", 0),
				new SharedFolderMapping("/Volumes/Duplicate", "/mnt/two", 1))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unique");
		assertThat(service.installationLocation()).isEqualTo(InstallationLocation.LOCAL);
		assertThat(service.mappings()).containsExactly(savedMapping);
	}

	@Test
	void rollsBackMappingReplacementWhenSavingTheLocationFails() {
		final SharedFolderMapping savedMapping = new SharedFolderMapping("/Volumes/Saved", "/mnt/saved", 0);
		mappings.replaceAll(List.of(savedMapping));
		appSettings.saveTtFileAccessInstallationLocation(InstallationLocation.LOCAL);
		jdbc.execute("""
			alter table app_setting add constraint file_access_location_test_check
			check (setting_key <> 'tt.file_access.installation_location' or setting_value = 'LOCAL')
			""");
		try {
			assertThatThrownBy(() -> service.save(InstallationLocation.REMOTE,
					List.of(new SharedFolderMapping("/Volumes/New", "/mnt/new", 0))))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThat(service.installationLocation()).isEqualTo(InstallationLocation.LOCAL);
			assertThat(service.mappings()).containsExactly(savedMapping);
		} finally {
			jdbc.execute("alter table app_setting drop constraint file_access_location_test_check");
		}
	}
}
