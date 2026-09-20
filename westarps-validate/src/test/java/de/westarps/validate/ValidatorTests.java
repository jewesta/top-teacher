package de.westarps.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ValidatorTests {

	@Test
	void executesTestsInConfiguredOrder() {
		final Validator<String> validator = Validator.<String>builder()
				.testLast(value -> TestResults.info("last"))
				.testFirst(value -> TestResults.warning("first"))
				.build();

		final ValidationResult<String> result = validator.validate("target");

		assertThat(result.getTarget()).isEqualTo("target");
		assertThat(result.getResults()).containsExactly(TestResult.warning("first"), TestResult.info("last"));
		assertThat(validator.apply("target").getResults()).containsExactly(TestResult.warning("first"),
				TestResult.info("last"));
	}

	@Test
	void failFastStopsAfterTheFirstFailedTestAndTarget() {
		final List<String> calls = new ArrayList<>();
		final Validator<String> validator = Validator.<String>builder()
				.test(value -> {
					calls.add("first:" + value);
					return TestResults.error("Invalid " + value);
				})
				.test(value -> {
					calls.add("second:" + value);
					return TestResults.error("Must not run");
				})
				.failFast()
				.build();

		final ValidationResults<String> results = validator.validate("one", "two");

		assertThat(results.getTargets()).containsExactly("one");
		assertThat(calls).containsExactly("first:one");
		assertThat(validator.isFailFast()).isTrue();
	}

	@Test
	void nonFailFastValidatorCollectsAllTestsAndTargets() {
		final Validator<Integer> validator = Validator.<Integer>builder()
				.test(value -> value < 2 ? TestResults.pass() : TestResults.error("Too large: " + value))
				.test(value -> TestResults.info("Checked: " + value))
				.build();

		final ValidationResults<Integer> results = validator.validate(List.of(1, 2));

		assertThat(results.getTargets()).containsExactly(1, 2);
		assertThat(results.getTestResults()).containsExactly(TestResult.info("Checked: 1"),
				TestResult.error("Too large: 2"), TestResult.info("Checked: 2"));
	}

	@Test
	void assertValidUsesDefaultOrConfiguredException() {
		final Validator<String> defaultValidator = Validator.<String>builder()
				.test(value -> TestResults.error("Invalid value"))
				.build();

		assertThatThrownBy(() -> defaultValidator.assertValid("target")).isInstanceOf(ValidationException.class)
				.hasMessage("Invalid value");

		final Validator<String> customValidator = Validator.<String>builder()
				.test(value -> TestResults.error("Invalid value"))
				.throwOnFailedAssert(results -> new IllegalStateException("Invalid targets: " + results.getTargets()))
				.build();

		assertThatThrownBy(() -> customValidator.assertValid("target")).isInstanceOf(IllegalStateException.class)
				.hasMessage("Invalid targets: [target]");
	}

	@Test
	void passingValidatorAcceptsTargetsButRejectsNull() {
		final Validator<String> validator = Validator.pass();

		assertThat(validator.validate("target").hasPassed()).isTrue();
		assertThat(validator.validate(List.of()).hasPassed()).isTrue();
		assertThatThrownBy(() -> validator.validate((String) null)).isInstanceOf(NullPointerException.class);
	}
}
