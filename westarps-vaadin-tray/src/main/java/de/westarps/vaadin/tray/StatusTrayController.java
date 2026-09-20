package de.westarps.vaadin.tray;

import java.util.Objects;

import de.westarps.validate.TestResult;
import de.westarps.validate.TestResults;
import de.westarps.validate.ValidationSummary;

public interface StatusTrayController extends TrayController<TestResult> {

	default TestResults getResults() {
		return TestResults.builder().addAll(getItems()).build();
	}

	default void setResults(final ValidationSummary results) {
		Objects.requireNonNull(results, "results must not be null");
		setItems(results.getTestResults());
	}
}
