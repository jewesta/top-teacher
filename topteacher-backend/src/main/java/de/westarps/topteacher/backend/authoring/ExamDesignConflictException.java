package de.westarps.topteacher.backend.authoring;

/**
 * A design changed after an editor obtained its snapshot.
 */
public class ExamDesignConflictException extends IllegalStateException {

	public ExamDesignConflictException(final long expectedRevision, final long currentRevision) {
		super("Die Klausur wurde inzwischen geändert. Lade sie neu, bevor Du Deine Änderungen speicherst. "
				+ "Expected revision " + expectedRevision + ", current revision " + currentRevision + ".");
	}
}
