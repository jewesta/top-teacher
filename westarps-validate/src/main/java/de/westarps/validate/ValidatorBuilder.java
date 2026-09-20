package de.westarps.validate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ValidatorBuilder<T> {

	private final List<Function<? super T, TestResults>> tests = new ArrayList<>();

	private boolean failFast;

	private Function<ValidationResults<T>, RuntimeException> exceptionFactory;

	public ValidatorBuilder<T> test(final Function<? super T, TestResults> test) {
		return testLast(test);
	}

	public ValidatorBuilder<T> testFirst(final Function<? super T, TestResults> test) {
		tests.add(0, Objects.requireNonNull(test, "test must not be null"));
		return this;
	}

	public ValidatorBuilder<T> testLast(final Function<? super T, TestResults> test) {
		tests.add(Objects.requireNonNull(test, "test must not be null"));
		return this;
	}

	public ValidatorBuilder<T> failFast() {
		return failFast(true);
	}

	public ValidatorBuilder<T> failFast(final boolean failFast) {
		this.failFast = failFast;
		return this;
	}

	public ValidatorBuilder<T> throwOnFailedAssert(
			final Function<? super ValidationResults<T>, ? extends RuntimeException> exceptionFactory) {
		Objects.requireNonNull(exceptionFactory, "exceptionFactory must not be null");
		this.exceptionFactory = results -> Objects.requireNonNull(exceptionFactory.apply(results),
				"created exception must not be null");
		return this;
	}

	public Validator<T> build() {
		return new DefaultValidator<>(tests, failFast, exceptionFactory);
	}
}
