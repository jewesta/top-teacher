package de.westarps.topteacher.backend.file;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.SharedFolderMappingRepository;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.SharedFolderMapping;

@Service
public class FileAccessSettingsService {

	private final AppSettings appSettings;
	private final SharedFolderMappingRepository mappings;

	public FileAccessSettingsService(final AppSettings appSettings, final SharedFolderMappingRepository mappings) {
		this.appSettings = Objects.requireNonNull(appSettings, "appSettings");
		this.mappings = Objects.requireNonNull(mappings, "mappings");
	}

	public InstallationLocation installationLocation() {
		return appSettings.ttFileAccessInstallationLocation();
	}

	public List<SharedFolderMapping> mappings() {
		return mappings.findAll();
	}

	@Transactional
	public void save(final InstallationLocation location, final List<SharedFolderMapping> folderMappings) {
		Objects.requireNonNull(location, "location");
		if (location == InstallationLocation.REMOTE) {
			mappings.replaceAll(folderMappings);
		}
		appSettings.saveTtFileAccessInstallationLocation(location);
	}
}
