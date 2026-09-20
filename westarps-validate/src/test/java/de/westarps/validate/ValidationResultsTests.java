package de.westarps.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ValidationResultsTests {

	@Test
	void aggregatesTargetedResults() {
		final ValidationResult<String> first = ValidationResult.warning("requirement-1", "Still incomplete");
		final ValidationResult<String> second = ValidationResult.error("requirement-2", "Too many points");
		final ValidationResult<String> third = ValidationResult.pass("requirement-3");

		final ValidationResults<String> results = ValidationResults.<String>builder().add(first).add(second).add(third)
				.build();

		assertThat(results.getTargets()).containsExactly("requirement-1", "requirement-2", "requirement-3");
		assertThat(results.getTestResults()).containsExactly(TestResult.warning("Still incomplete"),
				TestResult.error("Too many points"));
		assertThat(results.failed()).containsExactly(second);
		assertThat(results.passed()).containsExactly(first, third);
		assertThat(results.size()).isEqualTo(3);
	}

	@Test
	void buildersCreateImmutableSnapshots() {
		final ValidationResults.Builder<String> builder = ValidationResults.builder();
		builder.add(ValidationResult.pass("first"));
		final ValidationResults<String> snapshot = builder.build();

		builder.add(ValidationResult.error("second", "Error"));

		assertThat(snapshot.getTargets()).containsExactly("first");
		assertThatThrownBy(() -> snapshot.getResults().add(ValidationResult.pass("mutation")))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void validationResultsRequireTargets() {
		assertThatThrownBy(() -> ValidationResult.pass(null)).isInstanceOf(NullPointerException.class);
	}
}
