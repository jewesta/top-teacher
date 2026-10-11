package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;

class SegmentedControlTests {

	@Test
	void selectsExactlyOneComponentSegmentAndExposesAccessibleLabels() {
		final SegmentedControl<String> control = SegmentedControl.withComponents("Ausrichtung",
				List.of("left", "center", "right"), Span::new, value -> "Choose " + value);
		final List<Button> buttons = control.getChildren().flatMap(component -> component.getChildren())
				.filter(Button.class::isInstance).map(Button.class::cast).toList();
		final AtomicReference<String> changedTo = new AtomicReference<>();
		control.addValueChangeListener(event -> changedTo.set(event.getValue()));

		control.setValue("center");

		assertThat(control.getLabel()).isEqualTo("Ausrichtung");
		assertThat(buttons).extracting(button -> button.getElement().getAttribute("aria-label"))
				.containsExactly("Choose left", "Choose center", "Choose right");
		assertThat(buttons).extracting(button -> button.getElement().getAttribute("aria-pressed"))
				.containsExactly("false", "true", "false");

		buttons.getLast().click();

		assertThat(control.getValue()).isEqualTo("right");
		assertThat(changedTo).hasValue("right");
		assertThat(buttons).extracting(button -> button.getElement().getAttribute("aria-pressed"))
				.containsExactly("false", "false", "true");
	}

	@Test
	void disablesEverySegmentWhenReadOnly() {
		final SegmentedControl<String> control = new SegmentedControl<>(null, List.of("one", "two"), value -> value);

		control.setReadOnly(true);

		assertThat(control.getChildren().flatMap(component -> component.getChildren())
				.filter(Button.class::isInstance).map(Button.class::cast)).allMatch(button -> !button.isEnabled());
		assertThat(control.getChildren().findFirst().orElseThrow().hasClassName("tt-segmented-control-inactive"))
				.isTrue();
	}
}
