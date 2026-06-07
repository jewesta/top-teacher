package de.westarps.topteacher.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.SettingsRepository;

@SpringBootTest
class AppSettingsTests {

	@Autowired
	private AppSettings appSettings;

	@Autowired
	private SettingsRepository settingsRepository;

	@Test
	void defaultsToShowingTheTeacherWatermark() {
		settingsRepository.delete(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY);

		try {
			assertThat(appSettings.ttLoeExportShowWatermark()).isTrue();
		} finally {
			settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "true");
		}
	}

	@Test
	void readsTheTeacherWatermarkSettingFromTheDatabase() {
		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "false");
		assertThat(appSettings.ttLoeExportShowWatermark()).isFalse();

		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "true");
		assertThat(appSettings.ttLoeExportShowWatermark()).isTrue();
	}

	@Test
	void rejectsInvalidBooleanValues() {
		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "sometimes");

		try {
			assertThatThrownBy(appSettings::ttLoeExportShowWatermark)
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY);
		} finally {
			settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "true");
		}
	}
}
