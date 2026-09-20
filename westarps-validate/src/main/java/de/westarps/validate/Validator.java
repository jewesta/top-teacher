package de.westarps.validate;

import java.util.Collection;
import java.util.function.Function;

public interface Validator<T> extends Function<T, TestResults> {

	ValidationResult<T> validate(T target);

	@SuppressWarnings("unchecked")
	ValidationResults<T> validate(T... targets);

	ValidationResults<T> validate(Collection<? extends T> targets);

	void assertValid(T target);

	@SuppressWarnings("unchecked")
	void assertValid(T... targets);

	void assertValid(Collection<? extends T> targets);

	boolean isFailFast();

	@Override
	default TestResults apply(final T target) {
		return validate(target).toTestResults();
	}

	static <T> ValidatorBuilder<T> builder() {
		return new ValidatorBuilder<>();
	}

	static <T> Validator<T> pass() {
		return Validator.<T>builder().build();
	}
}
