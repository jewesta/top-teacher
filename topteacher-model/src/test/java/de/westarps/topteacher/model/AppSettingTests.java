package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AppSettingTests {

	@Test
	void trimsAndStoresSettings() {
		final AppSetting setting = new AppSetting(" tt.loe.export.show_watermark ", "true");

		assertThat(setting.key()).isEqualTo("tt.loe.export.show_watermark");
		assertThat(setting.value()).isEqualTo("true");
	}

	@Test
	void rejectsBlankKeysAndNullValues() {
		assertThatThrownBy(() -> new AppSetting(" ", "true"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("key must not be blank");
		assertThatThrownBy(() -> new AppSetting("tt.loe.export.show_watermark", null))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("value must not be null");
	}
}
