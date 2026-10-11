package de.westarps.topteacher.backend.authoring;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes an exam mutation and persists a retry receipt containing neutral
 * metadata only. A retry is checked before the original optimistic revision.
 */
@Service
public class ExamMutationExecutor {

	private static final int MAX_RECEIPT_CHARACTERS = 1_000_000;

	private final ExamDesignGuard guard;
	private final NamedParameterJdbcTemplate jdbc;

	public ExamMutationExecutor(final ExamDesignGuard guard, final NamedParameterJdbcTemplate jdbc) {
		this.guard = Objects.requireNonNull(guard, "guard");
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Transactional
	public <T> MutationExecution<T> execute(final int examId, final long expectedRevision, final String requestId,
			final String operation, final String fingerprint, final Supplier<T> action,
			final Function<T, String> encodeMetadata, final Function<String, T> decodeMetadata) {
		if (expectedRevision < 0) {
			throw new IllegalArgumentException("expectedRevision must not be negative");
		}
		if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,100}")) {
			throw new IllegalArgumentException("requestId must contain 1 to 100 letters, digits, dots, underscores, colons or hyphens");
		}
		if (operation == null || operation.isBlank() || operation.length() > 40
				|| fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("A valid operation and SHA-256 request fingerprint are required");
		}
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(encodeMetadata, "encodeMetadata");
		Objects.requireNonNull(decodeMetadata, "decodeMetadata");
		return guard.write(examId, null, () -> {
			final List<Receipt> receipts = jdbc.query("""
				select operation, request_hash, result_metadata, resulting_revision
				from exam_design_mutation
				where exam_id = :examId and request_id = :requestId
				""", Map.of("examId", examId, "requestId", requestId),
					(row, index) -> new Receipt(row.getString("operation"), row.getString("request_hash"),
							row.getString("result_metadata"), row.getLong("resulting_revision")));
			if (!receipts.isEmpty()) {
				final Receipt receipt = receipts.getFirst();
				if (!receipt.operation().equals(operation) || !receipt.fingerprint().equals(fingerprint)) {
					throw new IllegalArgumentException("requestId was already used for a different mutation");
				}
				return new MutationExecution<>(receipt.revision(), true, decodeMetadata.apply(receipt.metadata()));
			}
			/* The outer guard retains the row lock while the expected revision is checked. */
			return guard.write(examId, expectedRevision, () -> {
				final T metadata = action.get();
				final long resultingRevision = guard.revision(examId);
				final String encodedMetadata = encodeMetadata.apply(metadata);
				if (encodedMetadata == null || encodedMetadata.length() > MAX_RECEIPT_CHARACTERS) {
					throw new IllegalArgumentException("Mutation receipt exceeds the metadata size limit");
				}
				jdbc.update("""
					insert into exam_design_mutation
					    (exam_id, request_id, operation, request_hash, result_metadata, resulting_revision)
					values (:examId, :requestId, :operation, :fingerprint, :metadata, :revision)
					""", Map.of("examId", examId, "requestId", requestId, "operation", operation,
							"fingerprint", fingerprint, "metadata", encodedMetadata,
							"revision", resultingRevision));
				return new MutationExecution<>(resultingRevision, false, metadata);
			});
		});
	}

	/** Hashes typed, length-delimited values without persisting the request content. */
	public static String fingerprint(final Object... values) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
				output.writeInt(values.length);
				for (final Object value : values) {
					writeValue(output, value);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (final IOException | NoSuchAlgorithmException | ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not fingerprint the exam mutation", exception);
		}
	}

	private static void writeValue(final DataOutputStream output, final Object value)
			throws IOException, IllegalAccessException, InvocationTargetException {
		if (value == null) {
			output.writeByte(0);
		} else if (value instanceof String text) {
			output.writeByte(1);
			writeText(output, text);
		} else if (value instanceof Integer number) {
			output.writeByte(2);
			output.writeInt(number);
		} else if (value instanceof Long number) {
			output.writeByte(3);
			output.writeLong(number);
		} else if (value instanceof Boolean bool) {
			output.writeByte(4);
			output.writeBoolean(bool);
		} else if (value instanceof Enum<?> enumeration) {
			output.writeByte(5);
			writeText(output, enumeration.getDeclaringClass().getName());
			writeText(output, enumeration.name());
		} else if (value instanceof List<?> list) {
			output.writeByte(6);
			output.writeInt(list.size());
			for (final Object item : list) {
				writeValue(output, item);
			}
		} else if (value.getClass().isRecord()) {
			output.writeByte(7);
			writeText(output, value.getClass().getName());
			final var components = value.getClass().getRecordComponents();
			output.writeInt(components.length);
			for (final var component : components) {
				writeText(output, component.getName());
				writeValue(output, component.getAccessor().invoke(value));
			}
		} else {
			throw new IllegalArgumentException("Unsupported fingerprint value: " + value.getClass().getSimpleName());
		}
	}

	private static void writeText(final DataOutputStream output, final String value) throws IOException {
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	public record MutationExecution<T>(long revision, boolean replayed, T metadata) {
	}

	private record Receipt(String operation, String fingerprint, String metadata, long revision) {
	}
}
