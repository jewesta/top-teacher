package de.westarps.topteacher.backend.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.McpAccessToken;

/** Maintains MCP credentials; only the creation result contains a plaintext token. */
@Service
public class McpAccessTokenService {

	private static final int MAXIMUM_CREDENTIAL_LENGTH = 16_384;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final McpAccessTokenRepository repository;
	private final Clock clock;

	@Autowired
	public McpAccessTokenService(final McpAccessTokenRepository repository) {
		this(repository, Clock.systemUTC());
	}

	McpAccessTokenService(final McpAccessTokenRepository repository, final Clock clock) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public List<McpAccessToken> tokens() {
		return repository.findAll();
	}

	public CreatedToken create(final String name, final McpAccessLevel accessLevel, final Instant expiresAt) {
		final Instant now = clock.instant();
		validateExpiry(expiresAt, now);
		final McpAccessToken metadata = new McpAccessToken(UUID.randomUUID().toString(), name, accessLevel, now, null,
				expiresAt, null);
		final byte[] entropy = new byte[32];
		RANDOM.nextBytes(entropy);
		final String credential = "tt_mcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
		repository.insert(metadata, hash(credential));
		return new CreatedToken(metadata, credential);
	}

	public void update(final String id, final String name, final McpAccessLevel accessLevel, final Instant expiresAt) {
		final McpAccessToken expected = repository.findById(Objects.requireNonNull(id, "id"))
				.orElseThrow(McpAccessTokenConflictException::new);
		update(expected, name, accessLevel, expiresAt);
	}

	public void update(final McpAccessToken expected, final String name, final McpAccessLevel accessLevel,
			final Instant expiresAt) {
		Objects.requireNonNull(expected, "expected");
		final String normalizedName = McpAccessToken.validatedName(name);
		Objects.requireNonNull(accessLevel, "accessLevel");
		validateExpiry(expiresAt, clock.instant());
		if (!repository.update(expected, normalizedName, accessLevel, expiresAt)) {
			throw new McpAccessTokenConflictException();
		}
	}

	public void revoke(final String id) {
		repository.revoke(Objects.requireNonNull(id, "id"), clock.instant());
	}

	public Optional<McpAccessToken> authenticate(final String credential) {
		if (!validCredential(credential)) {
			return Optional.empty();
		}
		final byte[] presentedHash = hash(credential);
		final var stored = repository.findCredential(presentedHash);
		if (stored.isEmpty() || !MessageDigest.isEqual(presentedHash, stored.get().hash())) {
			return Optional.empty();
		}
		final Instant now = clock.instant();
		final McpAccessToken token = stored.get().metadata();
		if (!active(token, now) || !repository.recordUseIfActive(token.id(), now)) {
			return Optional.empty();
		}
		return repository.findById(token.id()).filter(latest -> active(latest, now));
	}

	public boolean legacyImportComplete() {
		return repository.legacyImportComplete();
	}

	/**
	 * Imports the previous file credential once. The durable marker survives
	 * revocation and dataset initialization, so a file cannot resurrect access.
	 */
	@Transactional
	public void importLegacyToken(final String credential) {
		if (repository.legacyImportCompletedForUpdate()) {
			return;
		}
		if (credential != null && !credential.isEmpty()) {
			if (!validCredential(credential) || credential.length() < 32) {
				throw new IllegalArgumentException("The legacy MCP credential is invalid.");
			}
			final byte[] credentialHash = hash(credential);
			if (repository.findCredential(credentialHash).isEmpty()) {
				repository.insert(new McpAccessToken(UUID.randomUUID().toString(), "Bisheriger MCP-Zugriff",
						McpAccessLevel.FULL, clock.instant(), null, null, null), credentialHash);
			}
		}
		repository.completeLegacyImport();
	}

	private static boolean active(final McpAccessToken token, final Instant now) {
		return token.revokedAt() == null && (token.expiresAt() == null || token.expiresAt().isAfter(now));
	}

	private static boolean validCredential(final String credential) {
		return credential != null && !credential.isEmpty() && credential.length() <= MAXIMUM_CREDENTIAL_LENGTH
				&& credential.equals(credential.strip())
				&& credential.codePoints().noneMatch(Character::isISOControl);
	}

	private static void validateExpiry(final Instant expiresAt, final Instant now) {
		if (expiresAt != null && !expiresAt.isAfter(now)) {
			throw new IllegalArgumentException("expiresAt must be in the future");
		}
	}

	private static byte[] hash(final String credential) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8));
		} catch (final NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable.", failure);
		}
	}

	public record CreatedToken(McpAccessToken metadata, String credential) {
		@Override
		public String toString() {
			return "CreatedToken[metadata=" + metadata + ", credential=[redacted]]";
		}
	}
}
