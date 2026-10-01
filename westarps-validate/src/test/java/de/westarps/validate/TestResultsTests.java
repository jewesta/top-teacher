package de.westarps.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TestResultsTests {

	@Test
	void buildsImmutableResultsAndKeepsEarlierSnapshotsUnchanged() {
		final TestResults.Builder builder = TestResults.builder().warning("First warning");
		final TestResults firstSnapshot = builder.build();

		builder.error("Later error");

		assertThat(firstSnapshot.getResults()).containsExactly(TestResult.warning("First warning"));
		assertThatThrownBy(() -> firstSnapshot.getResults().add(TestResult.error("Mutation")))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void requireAddsOnlyResultsForUnmetConditions() {
		final TestResults results = TestResults.builder()
				.require(true, () -> TestResult.error("Must not be added"))
				.require(false, () -> TestResult.error("Too many points"))
				.require("criterion", value -> value.length() > 20,
						() -> TestResult.warning("Criterion is too short"))
				.require(2, 3, Integer::equals, () -> TestResult.info("Values differ"))
				.build();

		assertThat(results.getResults()).containsExactly(TestResult.error("Too many points"),
				TestResult.warning("Criterion is too short"), TestResult.info("Values differ"));
	}

	@Test
	void summarizesSeveritiesAndMessages() {
		final TestResults results = TestResults.builder().info("Information").warning("Warning").error("Error")
				.build();

		assertThat(results.hasFailed()).isTrue();
		assertThat(results.hasPassed()).isFalse();
		assertThat(results.hasWarnings()).isTrue();
		assertThat(results.hasInfos()).isTrue();
		assertThat(results.worstSeverity()).contains(ValidationSeverity.ERROR);
		assertThat(results.messagesBySeverity()).isEqualTo(Map.of(ValidationSeverity.INFO,
				java.util.List.of("Information"), ValidationSeverity.WARNING, java.util.List.of("Warning"),
				ValidationSeverity.ERROR, java.util.List.of("Error")));
	}

	@Test
	void emptyResultsAreTheSharedImmutablePassResult() {
		assertThat(TestResults.builder().build()).isSameAs(TestResults.PASS);
		assertThat(TestResults.pass().hasPassed()).isTrue();
		assertThat(TestResults.pass().hasAny()).isFalse();
		assertThat(TestResults.pass().worstSeverity()).isEmpty();
	}

	@Test
	void testResultRequiresASeverityAndMessage() {
		assertThatThrownBy(() -> new TestResult(null, "Message")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> TestResult.error(" ")).isInstanceOf(IllegalArgumentException.class);
	}
}
