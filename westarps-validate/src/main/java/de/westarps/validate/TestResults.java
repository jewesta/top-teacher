package de.westarps.validate;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class TestResults implements ValidationSummary {

	public static final TestResults PASS = new TestResults(List.of());

	public static final class Builder {

		private final java.util.ArrayList<TestResult> results = new java.util.ArrayList<>();

		public Builder add(final TestResult result) {
			results.add(Objects.requireNonNull(result, "result must not be null"));
			return this;
		}

		public Builder add(final TestResults testResults) {
			Objects.requireNonNull(testResults, "testResults must not be null");
			return addAll(testResults.getResults());
		}

		public Builder addAll(final Collection<? extends TestResult> testResults) {
			Objects.requireNonNull(testResults, "testResults must not be null");
			testResults.forEach(this::add);
			return this;
		}

		public Builder info(final String message) {
			return add(TestResult.info(message));
		}

		public Builder warning(final String message) {
			return add(TestResult.warning(message));
		}

		public Builder error(final String message) {
			return add(TestResult.error(message));
		}

		public Builder require(final boolean condition, final Supplier<? extends TestResult> resultIfInvalid) {
			Objects.requireNonNull(resultIfInvalid, "resultIfInvalid must not be null");
			if (!condition) {
				add(resultIfInvalid.get());
			}
			return this;
		}

		public Builder require(final BooleanSupplier condition, final Supplier<? extends TestResult> resultIfInvalid) {
			Objects.requireNonNull(condition, "condition must not be null");
			return require(condition.getAsBoolean(), resultIfInvalid);
		}

		public <T> Builder require(final T target, final Predicate<? super T> condition,
				final Supplier<? extends TestResult> resultIfInvalid) {
			Objects.requireNonNull(condition, "condition must not be null");
			return require(condition.test(target), resultIfInvalid);
		}

		public <T, U> Builder require(final T firstTarget, final U secondTarget,
				final BiPredicate<? super T, ? super U> condition,
				final Supplier<? extends TestResult> resultIfInvalid) {
			Objects.requireNonNull(condition, "condition must not be null");
			return require(condition.test(firstTarget, secondTarget), resultIfInvalid);
		}

		public TestResults build() {
			return results.isEmpty() ? PASS : new TestResults(results);
		}
	}

	private final List<TestResult> results;

	private TestResults(final Collection<? extends TestResult> results) {
		this.results = List.copyOf(results);
	}

	@Override
	public List<TestResult> getTestResults() {
		return results;
	}

	public List<TestResult> getResults() {
		return results;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static TestResults of(final TestResult... results) {
		Objects.requireNonNull(results, "results must not be null");
		return new Builder().addAll(List.of(results)).build();
	}

	public static TestResults info(final String message) {
		return of(TestResult.info(message));
	}

	public static TestResults warning(final String message) {
		return of(TestResult.warning(message));
	}

	public static TestResults error(final String message) {
		return of(TestResult.error(message));
	}

	public static TestResults pass() {
		return PASS;
	}
}
