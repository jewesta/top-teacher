package de.westarps.vaadin.tray;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.html.Div;

import de.westarps.validate.TestResult;
import de.westarps.validate.TestResults;
import de.westarps.validate.ValidationResult;
import de.westarps.validate.ValidationResults;

class StatusTrayTests {

	@Test
	void startsPeekingWithoutResults() {
		final StatusTray tray = new StatusTray("Status");

		assertThat(tray).isInstanceOf(StatusTrayController.class);
		assertThat(tray.getLabel()).isEqualTo("Status");
		assertThat(tray.getResults()).isSameAs(TestResults.PASS);
		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(tray.getContentLayout().getChildren()).isEmpty();
	}

	@Test
	void rendersEveryTestResultInValidationOrderAndShowsTheTray() {
		final TestResults results = TestResults.builder().info("Information").warning("Warning").error("Error")
				.build();
		final StatusTray tray = new StatusTray("Status", results);

		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);
		assertThat(tray.getResults().getTestResults()).containsExactlyElementsOf(results.getTestResults());
		assertThat(entries(tray)).extracting(entry -> entry.getElement().getAttribute("data-severity"))
				.containsExactly("info", "warning", "error");
		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Information", "Warning",
				"Error");
		assertThat(entries(tray)).allSatisfy(entry -> assertThat(entry.getElement().getAttribute("role"))
				.isEqualTo("listitem"));
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
	void individualResultsAreAddedAtTheTop() {
		final StatusTray tray = new StatusTray(TestResults.warning("Existing warning"));
		tray.peek();

		tray.addItem(TestResult.error("Newest error"));

		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);
		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Newest error",
				"Existing warning");
	}

	@Test
	void replacesEntriesAndReturnsToPeekingForAnEmptySummary() {
		final StatusTray tray = new StatusTray(TestResults.warning("Initial warning"));

		tray.setResults(TestResults.error("Replacement error"));

		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Replacement error");
		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);

		tray.setResults(TestResults.pass());

		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(tray.getContentLayout().getChildren()).isEmpty();
	}

	@Test
	void anUnchangedSummaryDoesNotReopenTheTray() {
		final TestResults results = TestResults.warning("Unchanged warning");
		final StatusTray tray = new StatusTray(results);
		tray.peek();

		tray.setResults(results);

		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
	}

	@Test
	void removingTheFinalResultReturnsTheTrayToPeek() {
		final TestResult warning = TestResult.warning("Temporary warning");
		final StatusTray tray = new StatusTray(TestResults.of(warning));

		tray.removeItem(warning);

		assertThat(tray.getResults()).isSameAs(TestResults.PASS);
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
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

	private static List<Div> entries(final StatusTray tray) {
		return tray.getContentLayout().getChildren().map(Div.class::cast).toList();
	}

	private static String message(final Div entry) {
		return entry.getText();
	}
}
