package de.westarps.validate;

import java.util.Objects;

public record TestResult(ValidationSeverity severity, String message) {

	public TestResult {
		Objects.requireNonNull(severity, "severity must not be null");
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("message must not be blank");
		}
	}

	public boolean hasFailed() {
		return severity == ValidationSeverity.ERROR;
	}

	public static TestResult info(final String message) {
		return new TestResult(ValidationSeverity.INFO, message);
	}

	public static TestResult warning(final String message) {
		return new TestResult(ValidationSeverity.WARNING, message);
	}

	public static TestResult error(final String message) {
		return new TestResult(ValidationSeverity.ERROR, message);
	}
}
