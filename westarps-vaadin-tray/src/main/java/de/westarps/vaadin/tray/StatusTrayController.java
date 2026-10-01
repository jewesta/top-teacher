package de.westarps.vaadin.tray;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Predicate;

import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableFunction;

import de.westarps.validate.TestResult;
import de.westarps.validate.TestResults;
import de.westarps.validate.ValidationResults;
import de.westarps.validate.ValidationSummary;

public interface StatusTrayController extends TrayController<TestResult> {

	default TestResults getResults() {
		return TestResults.builder().addAll(getItems()).build();
	}

	default void setResults(final ValidationSummary results) {
		Objects.requireNonNull(results, "results must not be null");
		setItems(results.getTestResults());
	}

	<T extends Serializable> void setResults(ValidationResults<T> results, Predicate<? super T> linkedTarget,
			SerializableFunction<? super T, String> href, SerializableConsumer<? super T> action);
}
