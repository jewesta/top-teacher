package de.westarps.vaadin.tray;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import de.westarps.validate.TestResult;
import de.westarps.validate.TestResults;
import de.westarps.validate.ValidationResult;
import de.westarps.validate.ValidationResults;

class StatusTrayTests {

	@Test
	void startsPeekingWithoutResults() {
		final StatusTray tray = new StatusTray("Status");

		assertThat(tray.getLabel()).isEqualTo("Status");
		assertThat(tray.getResults()).isSameAs(TestResults.PASS);
		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(tray.getContentLayout().getChildren()).isEmpty();
	}

	@Test
	void rendersEveryTestResultInValidationOrder() {
		final TestResults results = TestResults.builder().info("Information").warning("Warning").error("Error")
				.build();
		final StatusTray tray = new StatusTray("Status", results);

		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getResults()).isSameAs(results);
		assertThat(entries(tray)).extracting(entry -> entry.getElement().getAttribute("data-severity"))
				.containsExactly("info", "warning", "error");
		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Information", "Warning",
				"Error");
		assertThat(entries(tray)).allSatisfy(entry -> {
			assertThat(entry.getElement().getAttribute("role")).isEqualTo("listitem");
			assertThat(entry.getChildren().filter(Icon.class::isInstance)).hasSize(1);
		});
	}

	@Test
	void acceptsTargetedValidationResultsThroughTheSummaryInterface() {
		final ValidationResults<String> results = ValidationResults.<String>builder()
				.add(ValidationResult.warning("requirement-1", "Incomplete"))
				.add(ValidationResult.error("requirement-2", "Over-allocated"))
				.build();

		final StatusTray tray = new StatusTray(results);

		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Incomplete", "Over-allocated");
	}

	@Test
	void replacesEntriesAndReturnsToPeekingForAnEmptySummary() {
		final StatusTray tray = new StatusTray(TestResults.warning("Initial warning"));
		tray.show();

		tray.setResults(TestResults.error("Replacement error"));

		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Replacement error");
		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);

		tray.setResults(TestResults.pass());

		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(tray.getContentLayout().getChildren()).isEmpty();
	}

	@Test
	void changingResultsDoesNotOptInAFullyHiddenTray() {
		final StatusTray tray = new StatusTray();
		tray.hide();

		tray.setResults(TestResults.warning("Hidden warning"));
		tray.setResults(TestResults.pass());

		assertThat(tray.getState()).isEqualTo(TrayState.HIDE);
		assertThat(tray.isVisible()).isFalse();
	}

	private static List<HorizontalLayout> entries(final StatusTray tray) {
		return tray.getContentLayout().getChildren().map(HorizontalLayout.class::cast).toList();
	}

	private static String message(final HorizontalLayout entry) {
		return entry.getChildren().filter(Span.class::isInstance).map(Span.class::cast).map(Component::getElement)
				.map(element -> element.getText()).findFirst().orElseThrow();
	}
}
