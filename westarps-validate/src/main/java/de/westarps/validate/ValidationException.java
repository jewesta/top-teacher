package de.westarps.validate;

import java.util.Objects;

@SuppressWarnings("serial")
public class ValidationException extends RuntimeException {

	public ValidationException(final String message) {
		super(message);
	}

	public ValidationException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public ValidationException(final Throwable cause) {
		super(cause);
	}

	public ValidationException(final ValidationSummary validationSummary) {
		super(firstErrorMessage(validationSummary));
	}

	private static String firstErrorMessage(final ValidationSummary validationSummary) {
		Objects.requireNonNull(validationSummary, "validationSummary must not be null");
		return validationSummary.messages(ValidationSeverity.ERROR).stream().findFirst().orElse("Validation failed.");
	}
}
