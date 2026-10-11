package de.westarps.topteacher.backend.authoring;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serializes design edits and result entry on the same persisted exam row.
 * Revisions include UI changes and result-derived edit permissions.
 */
@Service
public class ExamDesignGuard {

	private final NamedParameterJdbcTemplate jdbc;
	private final TransactionTemplate transactions;

	public ExamDesignGuard(final NamedParameterJdbcTemplate jdbc, final PlatformTransactionManager transactionManager) {
		this.jdbc = Objects.requireNonNull(jdbc);
		transactions = new TransactionTemplate(transactionManager);
	}

	public long revision(final int examId) {
		return jdbc.queryForList("select design_revision from exam where id = :id", Map.of("id", examId), Long.class)
				.stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
	}

	public <T> T read(final int examId, final Supplier<T> action) {
		return transactions.execute(status -> {
			lock(examId);
			return action.get();
		});
	}

	public <T> T write(final int examId, final Long expectedRevision, final Supplier<T> action) {
		return transactions.execute(status -> {
			lock(examId);
			assertActive(examId);
			if (expectedRevision != null && expectedRevision.longValue() != revision(examId)) {
				throw new ExamDesignConflictException(expectedRevision, revision(examId));
			}
			return action.get();
		});
	}

	public void lock(final int examId) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Exam design locking requires a transaction.");
		}
		if (jdbc.queryForList("select id from exam where id = :id for update", Map.of("id", examId), Integer.class)
				.isEmpty()) {
			throw new IllegalArgumentException("Exam does not exist: " + examId);
		}
	}

	public void lockAndAdvance(final int examId) {
		lock(examId);
		assertActive(examId);
		jdbc.update("update exam set design_revision = design_revision + 1 where id = :id", Map.of("id", examId));
	}

	private void assertActive(final int examId) {
		final String lifecycle = jdbc.queryForObject("""
			select course.lifecycle from exam join course on course.id = exam.course_id where exam.id = :id
			""", Map.of("id", examId), String.class);
		if (!"ACTIVE".equals(lifecycle)) {
			throw new IllegalStateException("Archivierte Klausuren können nicht geändert werden.");
		}
	}
}
