package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.SettingsRepository;
import de.westarps.topteacher.model.AppSetting;

@SpringBootTest
class SettingsRepositoryTests {

	@Autowired
	private SettingsRepository settingsRepository;

	@Test
	void savesUpdatesAndDeletesSettings() {
		final String key = "test.setting.repository";

		settingsRepository.delete(key);
		assertThat(settingsRepository.findValue(key)).isEmpty();

		settingsRepository.save(key, "one");
		assertThat(settingsRepository.findValue(key)).contains("one");
		assertThat(settingsRepository.findByKey(key)).contains(new AppSetting(key, "one"));

		settingsRepository.save(key, "two");
		assertThat(settingsRepository.findValue(key)).contains("two");

		settingsRepository.delete(key);
		assertThat(settingsRepository.findValue(key)).isEmpty();
	}
}
