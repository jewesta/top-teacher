package de.westarps.topteacher.backend.authoring;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retry-safe exam shell creation; receipts contain only IDs, hashes and revisions. */
@Service
public class ExamCreationExecutor {

	private final NamedParameterJdbcTemplate jdbc;
	private final ExamDesignGuard guard;

	public ExamCreationExecutor(final NamedParameterJdbcTemplate jdbc, final ExamDesignGuard guard) {
		this.jdbc = jdbc;
		this.guard = guard;
	}

	@Transactional
	public Creation execute(final int courseId, final String requestId, final String operation,
			final String fingerprint, final Supplier<Integer> create) {
		if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,100}")) {
			throw new IllegalArgumentException("requestId must contain 1 to 100 letters, digits, dots, underscores, colons or hyphens");
		}
		if (!List.of("CREATE_EXAM", "DUPLICATE_EXAM").contains(operation)
				|| fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Invalid exam creation operation or fingerprint");
		}
		final List<String> courses = jdbc.query("select lifecycle from course where id = :id for update",
				Map.of("id", courseId), (row, index) -> row.getString("lifecycle"));
		if (courses.isEmpty() || !"ACTIVE".equals(courses.getFirst())) {
			throw new IllegalArgumentException("Exam creation requires an active course");
		}
		final List<Receipt> receipts = jdbc.query("""
			select operation, request_hash, exam_id, resulting_revision
			from exam_creation_mutation where course_id = :courseId and request_id = :requestId
			""", Map.of("courseId", courseId, "requestId", requestId), (row, index) -> new Receipt(
					row.getString("operation"), row.getString("request_hash"), row.getInt("exam_id"),
					row.getLong("resulting_revision")));
		if (!receipts.isEmpty()) {
			final Receipt receipt = receipts.getFirst();
			if (!receipt.operation().equals(operation) || !receipt.fingerprint().equals(fingerprint)) {
				throw new IllegalArgumentException("requestId was already used for a different exam creation");
			}
			return new Creation(receipt.examId(), receipt.revision(), true);
		}
		final int examId = create.get();
		final long revision = guard.revision(examId);
		jdbc.update("""
			insert into exam_creation_mutation
			    (course_id, request_id, operation, request_hash, exam_id, resulting_revision)
			values (:courseId, :requestId, :operation, :hash, :examId, :revision)
			""", Map.of("courseId", courseId, "requestId", requestId, "operation", operation,
					"hash", fingerprint, "examId", examId, "revision", revision));
		return new Creation(examId, revision, false);
	}

	public record Creation(int examId, long mutationRevision, boolean replayed) {
	}

	private record Receipt(String operation, String fingerprint, int examId, long revision) {
	}
}
