package de.westarps.topteacher.backend.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;

import de.westarps.topteacher.backend.database.DatabaseInitializationMode;
import de.westarps.topteacher.backend.database.DatabaseInitializationService;
import de.westarps.topteacher.backend.repo.SettingsRepository;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.McpAccessToken;

class McpAccessTokenServiceTests {

	private static final Instant CREATED_AT = Instant.parse("2026-10-11T10:00:00Z");
	private static final String LEGACY_CREDENTIAL = "legacy-test-credential-with-32-characters-minimum";

	private SingleConnectionDataSource dataSource;
	private JdbcTemplate jdbc;
	private McpAccessTokenRepository repository;
	private MutableClock clock;
	private McpAccessTokenService service;

	@BeforeEach
	void setUp() {
		dataSource = new SingleConnectionDataSource("jdbc:h2:mem:mcp-tokens-" + UUID.randomUUID(), "sa", "", true);
		jdbc = new JdbcTemplate(dataSource);
		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"))
				.execute(dataSource);
		repository = new McpAccessTokenRepository(new NamedParameterJdbcTemplate(dataSource));
		clock = new MutableClock(CREATED_AT);
		service = new McpAccessTokenService(repository, clock);
	}

	@AfterEach
	void tearDown() {
		jdbc.execute("shutdown");
		dataSource.destroy();
	}

	@Test
	void createsIndependentRandomCredentialsAndStoresOnlyTheirHashes() throws Exception {
		final var first = service.create("  Chat client  ", McpAccessLevel.PSEUDONYMIZED, null);
		final var second = service.create("Other client", McpAccessLevel.FULL, null);

		assertThat(first.metadata().name()).isEqualTo("Chat client");
		assertThat(first.metadata().accessLevel()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
		assertThat(first.metadata().createdAt()).isEqualTo(CREATED_AT);
		assertThat(first.metadata().lastUsedAt()).isNull();
		assertThat(first.credential()).matches("tt_mcp_[A-Za-z0-9_-]{43}");
		assertThat(second.credential()).isNotEqualTo(first.credential());
		assertThat(second.metadata().id()).isNotEqualTo(first.metadata().id());
		final byte[] storedHash = jdbc.queryForObject("select credential_hash from mcp_access_token where id = ?",
				byte[].class, first.metadata().id());
		assertThat(storedHash).hasSize(32)
				.isEqualTo(MessageDigest.getInstance("SHA-256").digest(first.credential().getBytes(StandardCharsets.UTF_8)));
		assertThat(first.toString()).doesNotContain(first.credential());
		assertThat(repository.findCredential(storedHash).orElseThrow().toString()).contains("hash=[redacted]");
		assertThat(service.tokens()).hasSize(2);
	}

	@Test
	void authenticatesAcrossServiceInstancesAndRecordsUseOnlyAfterSuccessfulAuthentication() {
		final var created = service.create("Chat", McpAccessLevel.PSEUDONYMIZED, null);
		clock.now = CREATED_AT.plusSeconds(45);
		final var restarted = new McpAccessTokenService(repository, clock);

		assertThat(restarted.authenticate("wrong-credential")).isEmpty();
		assertThat(service.tokens().getFirst().lastUsedAt()).isNull();
		final var authenticated = restarted.authenticate(created.credential()).orElseThrow();
		assertThat(authenticated.id()).isEqualTo(created.metadata().id());
		assertThat(authenticated.lastUsedAt()).isEqualTo(clock.now);
		assertThat(authenticated.accessLevel()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
	}

	@Test
	void appliesAccessChangesAndRevocationToTheNextAuthentication() {
		final var created = service.create("Chat", McpAccessLevel.FULL, null);
		assertThat(service.authenticate(created.credential())).isPresent();

		service.update(created.metadata().id(), "Renamed", McpAccessLevel.PSEUDONYMIZED, CREATED_AT.plusSeconds(90));
		final var changed = service.authenticate(created.credential()).orElseThrow();
		assertThat(changed.name()).isEqualTo("Renamed");
		assertThat(changed.accessLevel()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);

		clock.now = CREATED_AT.plusSeconds(60);
		service.revoke(created.metadata().id());
		assertThat(service.authenticate(created.credential())).isEmpty();
		assertThat(service.tokens().getFirst().revokedAt()).isEqualTo(clock.now);
		clock.now = CREATED_AT.plusSeconds(61);
		service.revoke(created.metadata().id());
		assertThat(service.tokens().getFirst().revokedAt()).isEqualTo(CREATED_AT.plusSeconds(60));
		assertThatThrownBy(() -> service.update(created.metadata().id(), "Cannot revive", McpAccessLevel.FULL, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAStaleEditorWithoutRegrantingNamesAfterAnotherSessionDowngradesAccess() {
		final var created = service.create("Chat", McpAccessLevel.FULL, null);
		final McpAccessToken staleEditor = created.metadata();
		service.update(staleEditor, "Chat", McpAccessLevel.PSEUDONYMIZED, null);

		assertThatThrownBy(() -> service.update(staleEditor, "Changed label", McpAccessLevel.FULL, null))
				.isInstanceOf(McpAccessTokenConflictException.class);
		final var authenticated = service.authenticate(created.credential()).orElseThrow();
		assertThat(authenticated.name()).isEqualTo("Chat");
		assertThat(authenticated.accessLevel()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
	}

	@Test
	void rejectsStaleLabelsAndPreciseExpiryChangesButIgnoresLastUseMetadata() {
		final Instant originalExpiry = CREATED_AT.plusSeconds(60).plusNanos(123);
		final var created = service.create("Chat", McpAccessLevel.PSEUDONYMIZED, originalExpiry);
		final McpAccessToken editor = created.metadata();
		clock.now = CREATED_AT.plusSeconds(1);
		service.authenticate(created.credential()).orElseThrow();
		service.update(editor, "Renamed", McpAccessLevel.PSEUDONYMIZED, originalExpiry);

		assertThatThrownBy(() -> service.update(editor, "Old label again", McpAccessLevel.PSEUDONYMIZED, originalExpiry))
				.isInstanceOf(McpAccessTokenConflictException.class);
		final var expiryEditor = service.tokens().getFirst();
		final Instant newExpiry = originalExpiry.plusNanos(1);
		service.update(expiryEditor, "Renamed", McpAccessLevel.PSEUDONYMIZED, newExpiry);
		assertThatThrownBy(() -> service.update(expiryEditor, "Overwrite expiry", McpAccessLevel.PSEUDONYMIZED,
				originalExpiry)).isInstanceOf(McpAccessTokenConflictException.class);
		assertThat(service.tokens().getFirst().expiresAt()).isEqualTo(newExpiry);
	}

	@Test
	void rejectsAStaleSaveAfterRevocationWithoutReactivatingTheCredential() {
		final var created = service.create("Chat", McpAccessLevel.FULL, null);
		service.revoke(created.metadata().id());

		assertThatThrownBy(() -> service.update(created.metadata(), "Cannot revive", McpAccessLevel.FULL, null))
				.isInstanceOf(McpAccessTokenConflictException.class);
		assertThat(service.authenticate(created.credential())).isEmpty();
	}

	@Test
	void expiresAtTheExactBoundaryAndDoesNotRecordRejectedUse() {
		final Instant expiresAt = CREATED_AT.plusSeconds(60);
		final var created = service.create("Temporary", McpAccessLevel.FULL, expiresAt);
		clock.now = expiresAt.minusNanos(1);
		assertThat(service.authenticate(created.credential())).isPresent();
		clock.now = expiresAt;
		assertThat(service.authenticate(created.credential())).isEmpty();
		assertThat(service.tokens().getFirst().lastUsedAt()).isEqualTo(expiresAt.minusNanos(1));
	}

	@Test
	void rejectsInvalidMetadataAndCredentialsWithoutEchoingTheCredential() {
		assertThatThrownBy(() -> service.create(" ", McpAccessLevel.FULL, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create("x".repeat(McpAccessToken.MAXIMUM_NAME_LENGTH + 1), McpAccessLevel.FULL,
				null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create("Two\nlines", McpAccessLevel.FULL, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create("Expired", McpAccessLevel.FULL, CREATED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(service.authenticate(null)).isEmpty();
		assertThat(service.authenticate(" ")).isEmpty();
		assertThat(service.authenticate("x".repeat(16_385))).isEmpty();
		assertThat(service.tokens()).isEmpty();
		assertThatThrownBy(() -> service.importLegacyToken("too-short-secret"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("too-short-secret");
		assertThat(service.tokens()).isEmpty();
	}

	@Test
	void importsLegacyCredentialOnlyOnceAndNeverResurrectsRevokedOrDeletedTokens() {
		assertThat(service.legacyImportComplete()).isFalse();
		service.importLegacyToken(LEGACY_CREDENTIAL);
		assertThat(service.legacyImportComplete()).isTrue();
		final var imported = service.authenticate(LEGACY_CREDENTIAL).orElseThrow();
		assertThat(imported.accessLevel()).isEqualTo(McpAccessLevel.FULL);
		service.revoke(imported.id());

		final var restarted = new McpAccessTokenService(repository, clock);
		restarted.importLegacyToken(LEGACY_CREDENTIAL);
		assertThat(restarted.authenticate(LEGACY_CREDENTIAL)).isEmpty();
		assertThat(restarted.tokens()).hasSize(1);

		jdbc.update("delete from mcp_access_token");
		restarted.importLegacyToken(LEGACY_CREDENTIAL);
		restarted.importLegacyToken("a-different-file-credential-at-the-next-start");
		assertThat(restarted.tokens()).isEmpty();
	}

	@Test
	void completingMigrationWithoutAFilePreventsAddingAnUnmanagedTokenLater() {
		service.importLegacyToken(null);
		assertThat(service.legacyImportComplete()).isTrue();
		service.importLegacyToken(LEGACY_CREDENTIAL);
		assertThat(service.tokens()).isEmpty();
	}

	@Test
	void schemaAndDemoDataNeverCreateAnActiveCredentialOrResetSecurityState() {
		assertThat(service.tokens()).isEmpty();
		final var created = service.create("Retained", McpAccessLevel.PSEUDONYMIZED, null);
		service.importLegacyToken(LEGACY_CREDENTIAL);
		final var legacy = service.authenticate(LEGACY_CREDENTIAL).orElseThrow();
		service.revoke(legacy.id());

		new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"), new ClassPathResource("db/base-data.sql"),
				new ClassPathResource("db/demo-data.sql")).execute(dataSource);
		assertThat(service.tokens()).hasSize(2);
		assertThat(service.authenticate(created.credential())).isPresent();
		assertThat(service.authenticate(LEGACY_CREDENTIAL)).isEmpty();
		assertThat(jdbc.queryForObject("select legacy_token_imported from mcp_security_state where id = 1", Boolean.class))
				.isTrue();
	}

	@Test
	void datasetInitializationPreservesManagedCredentialsAndLegacyRevocation() {
		final var created = service.create("Retained", McpAccessLevel.PSEUDONYMIZED, null);
		service.importLegacyToken(LEGACY_CREDENTIAL);
		final var legacy = service.authenticate(LEGACY_CREDENTIAL).orElseThrow();
		service.revoke(legacy.id());
		final var settings = new AppSettings(new SettingsRepository(new NamedParameterJdbcTemplate(dataSource)));
		final var initialization = new DatabaseInitializationService(settings, dataSource, new MockEnvironment(), jdbc,
				new DefaultResourceLoader());

		initialization.initialize(DatabaseInitializationMode.EMPTY);
		initialization.initialize(DatabaseInitializationMode.DEMO);
		service.importLegacyToken(LEGACY_CREDENTIAL);

		assertThat(service.tokens()).hasSize(2);
		assertThat(service.authenticate(created.credential())).isPresent();
		assertThat(service.authenticate(LEGACY_CREDENTIAL)).isEmpty();
		assertThat(service.tokens()).filteredOn(token -> token.id().equals(legacy.id()))
				.allMatch(token -> token.revokedAt() != null);
	}

	private static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(final Instant now) {
			this.now = now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(final ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
