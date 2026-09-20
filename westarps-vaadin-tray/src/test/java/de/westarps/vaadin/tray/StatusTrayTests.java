package de.westarps.vaadin.tray;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.html.Div;

import de.westarps.vaadin.badge.AttachedBadge;
import de.westarps.vaadin.badge.Badge;
import de.westarps.vaadin.badge.BadgedComponent;
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
		assertThat(attachedBadge(tray).getClassNames()).contains("ws-badge-fade-hidden");
		assertThat(attachedBadge(tray).getStyle().get("top")).isEqualTo("-0.5em");
		assertThat(attachedBadge(tray).getStyle().get("right")).isEqualTo("-0.5em");
	}

	@Test
	void rendersEveryTestResultInValidationOrderAndKeepsTheTrayPeeking() {
		final TestResults results = TestResults.builder().info("Information").warning("Warning").error("Error")
				.build();
		final StatusTray tray = new StatusTray("Status", results);

		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(tray.getResults().getTestResults()).containsExactlyElementsOf(results.getTestResults());
		assertThat(entries(tray)).extracting(entry -> entry.getElement().getAttribute("data-severity"))
				.containsExactly("info", "warning", "error");
		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Information", "Warning",
				"Error");
		assertThat(entries(tray)).allSatisfy(entry -> assertThat(entry.getElement().getAttribute("role"))
				.isEqualTo("listitem"));
		assertThat(badge(tray).getElement().getThemeList()).contains("error");
		assertThat(badgeText(tray)).isEqualTo("3");
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

		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Newest error",
				"Existing warning");
		assertThat(badge(tray).getElement().getThemeList()).contains("error");
		assertThat(badgeText(tray)).isEqualTo("2");
	}

	@Test
	void changingResultsUpdatesTheBadgeWithoutChangingTrayState() {
		final StatusTray tray = new StatusTray(TestResults.warning("Initial warning"));
		tray.show();

		tray.setResults(TestResults.error("Replacement error"));

		assertThat(entries(tray)).extracting(StatusTrayTests::message).containsExactly("Replacement error");
		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);
		assertThat(badge(tray).getElement().getThemeList()).contains("error");

		tray.setResults(TestResults.pass());

		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);
		assertThat(tray.getContentLayout().getChildren()).isEmpty();
		assertThat(attachedBadge(tray).getClassNames()).contains("ws-badge-fade-hidden");
	}

	@Test
	void badgeUsesTheMostSevereResultColor() {
		final StatusTray tray = new StatusTray(TestResults.builder().info("Info").warning("Warning").build());

		assertThat(badge(tray).getElement().getThemeList()).contains("warning").doesNotContain("error", "contrast");

		tray.setResults(TestResults.info("Info"));

		assertThat(badge(tray).getElement().getThemeList()).contains("contrast").doesNotContain("warning", "error",
				"success");
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
	void removingTheFinalResultHidesTheBadge() {
		final TestResult warning = TestResult.warning("Temporary warning");
		final StatusTray tray = new StatusTray(TestResults.of(warning));

		tray.removeItem(warning);

		assertThat(tray.getResults()).isSameAs(TestResults.PASS);
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(attachedBadge(tray).getClassNames()).contains("ws-badge-fade-hidden");
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

	private static AttachedBadge attachedBadge(final StatusTray tray) {
		return ((BadgedComponent<?>) tray.getHeader()).getBadge();
	}

	private static Badge badge(final StatusTray tray) {
		return attachedBadge(tray).getChildren().filter(Badge.class::isInstance).map(Badge.class::cast).findFirst()
				.orElseThrow();
	}

	private static String badgeText(final StatusTray tray) {
		return badge(tray).getElement().getChild(0).getText();
	}
}
