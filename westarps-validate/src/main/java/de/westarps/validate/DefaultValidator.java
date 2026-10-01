package de.westarps.validate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class DefaultValidator<T> implements Validator<T> {

	private final List<Function<? super T, TestResults>> tests;

	private final boolean failFast;

	private final Function<ValidationResults<T>, RuntimeException> exceptionFactory;

	DefaultValidator(final Collection<? extends Function<? super T, TestResults>> tests, final boolean failFast,
			final Function<ValidationResults<T>, RuntimeException> exceptionFactory) {
		this.tests = List.copyOf(tests);
		this.failFast = failFast;
		this.exceptionFactory = exceptionFactory;
	}

	@Override
	public ValidationResult<T> validate(final T target) {
		Objects.requireNonNull(target, "target must not be null");
		final ValidationResult.Builder<T> result = ValidationResult.builder(target);
		for (final Function<? super T, TestResults> test : tests) {
			final TestResults testResults = Objects.requireNonNull(test.apply(target), "test result must not be null");
			result.add(testResults);
			if (failFast && testResults.hasFailed()) {
				break;
			}
		}
		return result.build();
	}

	@Override
	@SafeVarargs
	public final ValidationResults<T> validate(final T... targets) {
		Objects.requireNonNull(targets, "targets must not be null");
		return validate(Arrays.asList(targets));
	}

	@Override
	public ValidationResults<T> validate(final Collection<? extends T> targets) {
		Objects.requireNonNull(targets, "targets must not be null");
		final ValidationResults.Builder<T> results = ValidationResults.builder();
		for (final T target : targets) {
			final ValidationResult<T> result = validate(target);
			results.add(result);
			if (failFast && result.hasFailed()) {
				break;
			}
		}
		return results.build();
	}

	@Override
	public void assertValid(final T target) {
		assertValidResults(ValidationResults.<T>builder().add(validate(target)).build());
	}

	@Override
	@SafeVarargs
	public final void assertValid(final T... targets) {
		assertValidResults(validate(targets));
	}

	@Override
	public void assertValid(final Collection<? extends T> targets) {
		assertValidResults(validate(targets));
	}

	@Override
	public boolean isFailFast() {
		return failFast;
	}

	private void assertValidResults(final ValidationResults<T> results) {
		if (results.hasPassed()) {
			return;
		}
		if (exceptionFactory != null) {
			throw exceptionFactory.apply(results);
		}
		throw new ValidationException(results);
	}
}
