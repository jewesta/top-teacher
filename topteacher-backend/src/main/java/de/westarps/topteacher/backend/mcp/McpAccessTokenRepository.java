package de.westarps.topteacher.backend.mcp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.McpAccessToken;

@Repository
public class McpAccessTokenRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public McpAccessTokenRepository(final NamedParameterJdbcTemplate jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	public List<McpAccessToken> findAll() {
		return jdbc.query("""
			select id, name, access_level, created_at, last_used_at, expires_at, revoked_at
			from mcp_access_token
			order by created_at desc, id
			""", Map.of(), (resultSet, rowNumber) -> metadata(resultSet));
	}

	Optional<McpAccessToken> findById(final String id) {
		return jdbc.query("""
			select id, name, access_level, created_at, last_used_at, expires_at, revoked_at
			from mcp_access_token
			where id = :id
			""", Map.of("id", id), (resultSet, rowNumber) -> metadata(resultSet)).stream().findFirst();
	}

	Optional<StoredCredential> findCredential(final byte[] hash) {
		return jdbc.query("""
			select id, name, access_level, created_at, last_used_at, expires_at, revoked_at, credential_hash
			from mcp_access_token
			where credential_hash = :hash
			""", Map.of("hash", hash), (resultSet, rowNumber) -> new StoredCredential(metadata(resultSet),
				resultSet.getBytes("credential_hash"))).stream().findFirst();
	}

	void insert(final McpAccessToken token, final byte[] hash) {
		jdbc.update("""
			insert into mcp_access_token (id, name, access_level, created_at, expires_at, credential_hash)
			values (:id, :name, :level, :createdAt, :expiresAt, :hash)
			""", parameters(token).addValue("hash", hash));
	}

	boolean update(final McpAccessToken expected, final String name, final McpAccessLevel level,
			final Instant expiresAt) {
		return jdbc.update("""
			update mcp_access_token
			set name = :name, access_level = :level, expires_at = :expiresAt
			where id = :id and revoked_at is null
			  and name = :expectedName
			  and access_level = :expectedLevel
			  and (expires_at = :expectedExpiresAt or (expires_at is null and :expectedExpiresAt is null))
			""", new MapSqlParameterSource().addValue("id", expected.id()).addValue("name", name)
				.addValue("level", level.name()).addValue("expiresAt", timestamp(expiresAt))
				.addValue("expectedName", expected.name()).addValue("expectedLevel", expected.accessLevel().name())
				.addValue("expectedExpiresAt", timestamp(expected.expiresAt()))) == 1;
	}

	void revoke(final String id, final Instant now) {
		jdbc.update("""
			update mcp_access_token
			set revoked_at = :now
			where id = :id and revoked_at is null
			""", Map.of("id", id, "now", Timestamp.from(now)));
	}

	boolean recordUseIfActive(final String id, final Instant now) {
		return jdbc.update("""
			update mcp_access_token
			set last_used_at = greatest(coalesce(last_used_at, :now), :now)
			where id = :id
			  and revoked_at is null
			  and (expires_at is null or expires_at > :now)
			""", Map.of("id", id, "now", Timestamp.from(now))) == 1;
	}

	boolean legacyImportComplete() {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
			select legacy_token_imported
			from mcp_security_state
			where id = 1
			""", Map.of(), Boolean.class));
	}

	boolean legacyImportCompletedForUpdate() {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
			select legacy_token_imported
			from mcp_security_state
			where id = 1
			for update
			""", Map.of(), Boolean.class));
	}

	void completeLegacyImport() {
		jdbc.update("update mcp_security_state set legacy_token_imported = true where id = 1", Map.of());
	}

	private static MapSqlParameterSource parameters(final McpAccessToken token) {
		return new MapSqlParameterSource().addValue("id", token.id()).addValue("name", token.name())
				.addValue("level", token.accessLevel().name()).addValue("createdAt", Timestamp.from(token.createdAt()))
				.addValue("expiresAt", timestamp(token.expiresAt()));
	}

	private static McpAccessToken metadata(final ResultSet resultSet) throws SQLException {
		return new McpAccessToken(resultSet.getString("id"), resultSet.getString("name"),
				McpAccessLevel.valueOf(resultSet.getString("access_level")), instant(resultSet, "created_at"),
				instant(resultSet, "last_used_at"), instant(resultSet, "expires_at"), instant(resultSet, "revoked_at"));
	}

	private static Instant instant(final ResultSet resultSet, final String column) throws SQLException {
		final Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Timestamp timestamp(final Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	record StoredCredential(McpAccessToken metadata, byte[] hash) {
		@Override
		public String toString() {
			return "StoredCredential[metadata=" + metadata + ", hash=[redacted]]";
		}
	}
}
