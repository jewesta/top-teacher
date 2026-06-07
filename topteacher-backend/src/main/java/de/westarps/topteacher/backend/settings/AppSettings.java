package de.westarps.topteacher.backend.settings;

import java.util.Locale;

import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.SettingsRepository;

@Component
public class AppSettings {

	public static final String TT_LOE_EXPORT_SHOW_WATERMARK_KEY = "tt.loe.export.show_watermark";
	public static final boolean TT_LOE_EXPORT_SHOW_WATERMARK_DEFAULT = true;

	private final SettingsRepository settingsRepository;

	public AppSettings(final SettingsRepository settingsRepository) {
		this.settingsRepository = settingsRepository;
	}

	public boolean ttLoeExportShowWatermark() {
		return settingsRepository.findValue(TT_LOE_EXPORT_SHOW_WATERMARK_KEY)
				.map(value -> parseBoolean(TT_LOE_EXPORT_SHOW_WATERMARK_KEY, value))
				.orElse(TT_LOE_EXPORT_SHOW_WATERMARK_DEFAULT);
	}

	private static boolean parseBoolean(final String key, final String value) {
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "true" -> true;
			case "false" -> false;
			default -> throw new IllegalArgumentException(
					"Setting " + key + " must be true or false, but was '" + value + "'.");
		};
	}
}
