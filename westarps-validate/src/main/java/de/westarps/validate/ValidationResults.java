package de.westarps.validate;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ValidationResults<T> implements ValidationSummary {

	public static final class Builder<T> {

		private final java.util.ArrayList<ValidationResult<T>> results = new java.util.ArrayList<>();

		public Builder<T> add(final ValidationResult<T> result) {
			results.add(Objects.requireNonNull(result, "result must not be null"));
			return this;
		}

		public Builder<T> addAll(final Collection<? extends ValidationResult<T>> validationResults) {
			Objects.requireNonNull(validationResults, "validationResults must not be null");
			validationResults.forEach(this::add);
			return this;
		}

		public ValidationResults<T> build() {
			return new ValidationResults<>(results);
		}
	}

	private final List<ValidationResult<T>> results;

	private ValidationResults(final Collection<? extends ValidationResult<T>> results) {
		this.results = List.copyOf(results);
	}

	public List<ValidationResult<T>> getResults() {
		return results;
	}

	@Override
	public List<TestResult> getTestResults() {
		return results.stream().flatMap(result -> result.getTestResults().stream()).toList();
	}

	public List<T> getTargets() {
		return results.stream().map(ValidationResult::getTarget).toList();
	}

	public List<ValidationResult<T>> passed() {
		return results.stream().filter(ValidationResult::hasPassed).toList();
	}

	public List<ValidationResult<T>> failed() {
		return results.stream().filter(ValidationResult::hasFailed).toList();
	}

	public void forEach(final Consumer<? super ValidationResult<T>> consumer) {
		results.forEach(Objects.requireNonNull(consumer, "consumer must not be null"));
	}

	public int size() {
		return results.size();
	}

	public static <T> Builder<T> builder() {
		return new Builder<>();
	}

	public static <T> ValidationResults<T> pass() {
		return ValidationResults.<T>builder().build();
	}
}
