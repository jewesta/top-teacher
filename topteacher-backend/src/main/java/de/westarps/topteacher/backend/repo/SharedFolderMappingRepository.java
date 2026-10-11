package de.westarps.topteacher.backend.repo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.model.SharedFolderMapping;

@Repository
public class SharedFolderMappingRepository {

	public static final int MAXIMUM_MAPPINGS = 50;

	private final NamedParameterJdbcTemplate jdbc;

	public SharedFolderMappingRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	public List<SharedFolderMapping> findAll() {
		return jdbc.query("""
			select client_path, server_path, sort_order
			from shared_folder_mapping
			order by sort_order, client_path
			""", Map.of(), (resultSet, rowNumber) -> new SharedFolderMapping(resultSet.getString("client_path"),
				resultSet.getString("server_path"), resultSet.getInt("sort_order")));
	}

	@Transactional
	public void replaceAll(final List<SharedFolderMapping> mappings) {
		Objects.requireNonNull(mappings, "mappings must not be null");
		if (mappings.size() > MAXIMUM_MAPPINGS) {
			throw new IllegalArgumentException("shared-folder mappings must not contain more than " + MAXIMUM_MAPPINGS
					+ " entries");
		}

		final Set<String> clientPaths = new HashSet<>();
		final MapSqlParameterSource[] parameters = new MapSqlParameterSource[mappings.size()];
		for (int index = 0; index < mappings.size(); index++) {
			final SharedFolderMapping mapping = mappings.get(index);
			if (!clientPaths.add(mapping.clientPath())) {
				throw new IllegalArgumentException("client paths must be unique: " + mapping.clientPath());
			}
			parameters[index] = new MapSqlParameterSource().addValue("clientPath", mapping.clientPath())
					.addValue("serverPath", mapping.serverPath()).addValue("sortOrder", index);
		}

		jdbc.update("delete from shared_folder_mapping", Map.of());
		if (parameters.length > 0) {
			jdbc.batchUpdate("""
				insert into shared_folder_mapping (client_path, server_path, sort_order)
				values (:clientPath, :serverPath, :sortOrder)
				""", parameters);
		}
	}
}
