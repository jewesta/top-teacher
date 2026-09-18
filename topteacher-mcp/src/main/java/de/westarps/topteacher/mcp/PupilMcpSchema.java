package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Objects;

/**
 * Shared MCP input and output types for pupil operations.
 */
public final class PupilMcpSchema {

	static final int MAXIMUM_KEY_LENGTH = 100;
	static final int MAXIMUM_NAME_LENGTH = 100;

	private PupilMcpSchema() {
	}

	public enum PupilConflictAction {
		REUSE,
		CREATE,
		SKIP
	}

	public enum PupilListScope {
		ACTIVE,
		ARCHIVED,
		ALL
	}

	public enum PupilCreateStatus {
		CREATED,
		NEEDS_RESOLUTION,
		CANCELLED
	}

	public enum PupilUpdateDuplicateAction {
		UPDATE_ANYWAY,
		CANCEL
	}

	public enum PupilUpdateStatus {
		UPDATED,
		NEEDS_CONFIRMATION,
		CANCELLED
	}

	public record PupilDraft(String entryKey, String name, String surname) {

		public PupilDraft {
			entryKey = boundedText(entryKey, "entryKey", MAXIMUM_KEY_LENGTH);
			name = boundedText(name, "name", MAXIMUM_NAME_LENGTH);
			surname = boundedText(surname, "surname", MAXIMUM_NAME_LENGTH);
		}
	}

	public record PupilConflictResolution(String entryKey, PupilConflictAction action, Integer pupilId) {

		public PupilConflictResolution {
			entryKey = boundedText(entryKey, "resolution entryKey", MAXIMUM_KEY_LENGTH);
			Objects.requireNonNull(action, "resolution action must not be null");
		}
	}

	public record PupilConflictView(String entryKey, String name, String surname, boolean exactNameExists,
			boolean repeatedInRequest, List<ExistingPupilView> existingPupils, List<String> allowedActions) {

		public PupilConflictView {
			existingPupils = List.copyOf(Objects.requireNonNull(existingPupils, "existingPupils"));
			allowedActions = List.copyOf(Objects.requireNonNull(allowedActions, "allowedActions"));
		}
	}

	public record ExistingPupilView(int id, String name, String surname, String lifecycle, String latestSchoolClass,
			String latestSchoolClassDisplayName) {
	}

	public record PupilView(int id, String name, String surname, String lifecycle, String latestSchoolClass,
			String latestSchoolClassDisplayName) {
	}

	public record PupilListResult(List<PupilView> pupils) {

		public PupilListResult {
			pupils = List.copyOf(Objects.requireNonNull(pupils, "pupils"));
		}
	}

	public record PupilCreateResult(PupilCreateStatus status, String message, List<PupilView> createdPupils,
			List<PupilConflictView> conflicts, List<String> skippedEntryKeys) {

		public PupilCreateResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			createdPupils = List.copyOf(Objects.requireNonNull(createdPupils, "createdPupils"));
			conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
			skippedEntryKeys = List.copyOf(Objects.requireNonNull(skippedEntryKeys, "skippedEntryKeys"));
		}
	}

	public record PupilUpdateConflictView(int pupilId, String currentName, String currentSurname, String requestedName,
			String requestedSurname, List<ExistingPupilView> existingPupils, List<String> allowedActions) {

		public PupilUpdateConflictView {
			existingPupils = List.copyOf(Objects.requireNonNull(existingPupils, "existingPupils"));
			allowedActions = List.copyOf(Objects.requireNonNull(allowedActions, "allowedActions"));
		}
	}

	public record PupilUpdateResult(PupilUpdateStatus status, String message, List<PupilView> updatedPupils,
			List<PupilUpdateConflictView> conflicts) {

		public PupilUpdateResult {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(message, "message");
			updatedPupils = List.copyOf(Objects.requireNonNull(updatedPupils, "updatedPupils"));
			conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
		}
	}

	static String boundedText(final String value, final String name, final int maximumLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		final String trimmed = value.trim();
		if (trimmed.length() > maximumLength) {
			throw new IllegalArgumentException(name + " must not be longer than " + maximumLength + " characters");
		}
		return trimmed;
	}
}
