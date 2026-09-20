package de.westarps.validate;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ValidationResult<T> implements ValidationSummary {

	public static final class Builder<T> {

		private final T target;

		private final TestResults.Builder results = TestResults.builder();

		private Builder(final T target) {
			this.target = Objects.requireNonNull(target, "target must not be null");
		}

		public Builder<T> add(final TestResult result) {
			results.add(result);
			return this;
		}

		public Builder<T> add(final TestResults testResults) {
			results.add(testResults);
			return this;
		}

		public Builder<T> info(final String message) {
			results.info(message);
			return this;
		}

		public Builder<T> warning(final String message) {
			results.warning(message);
			return this;
		}

		public Builder<T> error(final String message) {
			results.error(message);
			return this;
		}

		public Builder<T> require(final boolean condition, final Supplier<? extends TestResult> resultIfInvalid) {
			results.require(condition, resultIfInvalid);
			return this;
		}

		public ValidationResult<T> build() {
			return new ValidationResult<>(target, results.build());
		}
	}

	private final T target;

	private final TestResults testResults;

	private ValidationResult(final T target, final TestResults testResults) {
		this.target = Objects.requireNonNull(target, "target must not be null");
		this.testResults = Objects.requireNonNull(testResults, "testResults must not be null");
	}

	public T getTarget() {
		return target;
	}

	public TestResults toTestResults() {
		return testResults;
	}

	public List<TestResult> getResults() {
		return testResults.getResults();
	}

	@Override
	public List<TestResult> getTestResults() {
		return testResults.getTestResults();
	}

	public static <T> Builder<T> builder(final T target) {
		return new Builder<>(target);
	}

	public static <T> ValidationResult<T> pass(final T target) {
		return builder(target).build();
	}

	public static <T> ValidationResult<T> info(final T target, final String message) {
		return ValidationResult.<T>builder(target).info(message).build();
	}

	public static <T> ValidationResult<T> warning(final T target, final String message) {
		return ValidationResult.<T>builder(target).warning(message).build();
	}

	public static <T> ValidationResult<T> error(final T target, final String message) {
		return ValidationResult.<T>builder(target).error(message).build();
	}
}
