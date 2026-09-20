package de.westarps.validate;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public interface ValidationSummary {

	List<TestResult> getTestResults();

	default Set<ValidationSeverity> getSeverities() {
		final EnumSet<ValidationSeverity> severities = EnumSet.noneOf(ValidationSeverity.class);
		getTestResults().stream().map(TestResult::severity).forEach(severities::add);
		return Collections.unmodifiableSet(severities);
	}

	default boolean hasFailed() {
		return hasAnyOf(ValidationSeverity.ERROR);
	}

	default boolean hasPassed() {
		return !hasFailed();
	}

	default boolean hasWarnings() {
		return hasAnyOf(ValidationSeverity.WARNING);
	}

	default boolean hasInfos() {
		return hasAnyOf(ValidationSeverity.INFO);
	}

	default boolean hasAny() {
		return !getTestResults().isEmpty();
	}

	default boolean hasAnyOf(final ValidationSeverity... severities) {
		Objects.requireNonNull(severities, "severities must not be null");
		if (severities.length == 0 || getTestResults().isEmpty()) {
			return false;
		}
		final Set<ValidationSeverity> presentSeverities = getSeverities();
		for (final ValidationSeverity severity : severities) {
			if (presentSeverities.contains(Objects.requireNonNull(severity, "severity must not be null"))) {
				return true;
			}
		}
		return false;
	}

	default Optional<ValidationSeverity> worstSeverity() {
		if (hasFailed()) {
			return Optional.of(ValidationSeverity.ERROR);
		}
		if (hasWarnings()) {
			return Optional.of(ValidationSeverity.WARNING);
		}
		if (hasInfos()) {
			return Optional.of(ValidationSeverity.INFO);
		}
		return Optional.empty();
	}

	default List<String> messages(final ValidationSeverity severity) {
		Objects.requireNonNull(severity, "severity must not be null");
		return getTestResults().stream().filter(result -> result.severity() == severity).map(TestResult::message)
				.toList();
	}

	default Map<ValidationSeverity, List<String>> messagesBySeverity() {
		return messagesBySeverity(ValidationSeverity.values());
	}

	default Map<ValidationSeverity, List<String>> messagesBySeverity(final ValidationSeverity... severities) {
		Objects.requireNonNull(severities, "severities must not be null");
		if (severities.length == 0) {
			throw new IllegalArgumentException("at least one severity is required");
		}
		final Map<ValidationSeverity, List<String>> messages = new EnumMap<>(ValidationSeverity.class);
		for (final ValidationSeverity severity : EnumSet.copyOf(List.of(severities))) {
			final List<String> severityMessages = messages(severity);
			if (!severityMessages.isEmpty()) {
				messages.put(severity, severityMessages);
			}
		}
		return Collections.unmodifiableMap(messages);
	}

	default void throwOnFailed(final Supplier<? extends RuntimeException> exceptionSupplier) {
		Objects.requireNonNull(exceptionSupplier, "exceptionSupplier must not be null");
		if (hasFailed()) {
			throw Objects.requireNonNull(exceptionSupplier.get(), "supplied exception must not be null");
		}
	}
}
