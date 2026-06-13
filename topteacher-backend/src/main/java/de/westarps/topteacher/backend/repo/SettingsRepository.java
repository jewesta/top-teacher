package de.westarps.topteacher.backend.repo;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import de.westarps.topteacher.model.AppSetting;

@Repository
public class SettingsRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public SettingsRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<String> findValue(final String key) {
		return findByKey(key).map(AppSetting::value);
	}

	public Optional<AppSetting> findByKey(final String key) {
		final AppSetting setting = new AppSetting(key, "");
		return jdbc.query("""
				select setting_key, setting_value
				from app_setting
				where setting_key = :key
				""", Map.of("key", setting.key()), (resultSet,
				rowNumber) -> new AppSetting(resultSet.getString("setting_key"), resultSet.getString("setting_value")))
				.stream().findFirst();
	}

	public void save(final String key, final String value) {
		save(new AppSetting(key, value));
	}

	public void save(final AppSetting setting) {
		final Map<String, String> parameters = Map.of("key", setting.key(), "value", setting.value());
		final int updated = jdbc.update("""
				update app_setting
				set setting_value = :value
				where setting_key = :key
				""", parameters);

		if (updated == 0) {
			jdbc.update("""
					insert into app_setting (setting_key, setting_value)
					values (:key, :value)
					""", parameters);
		}
	}

	public void delete(final String key) {
		final AppSetting setting = new AppSetting(key, "");
		jdbc.update("""
				delete from app_setting
				where setting_key = :key
				""", Map.of("key", setting.key()));
	}
}
