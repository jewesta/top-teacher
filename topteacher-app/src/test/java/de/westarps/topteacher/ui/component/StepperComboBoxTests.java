package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;

class StepperComboBoxTests {

	@Test
	void stepsThroughItemsAndUpdatesButtonState() {
		final StepperComboBox<String> comboBox = new StepperComboBox<>();
		final List<String> selectedItems = new ArrayList<>();
		comboBox.setItems(List.of("Anna", "Bernd", "Clara"));
		comboBox.addValueChangeListener(event -> selectedItems.add(event.getValue()));

		comboBox.setValue("Bernd");

		final List<Button> buttons = components(comboBox, Button.class);
		final Button previous = buttons.getFirst();
		final Button next = buttons.getLast();

		assertThat(previous.isEnabled()).isTrue();
		assertThat(next.isEnabled()).isTrue();

		previous.click();

		assertThat(comboBox.getValue()).isEqualTo("Anna");
		assertThat(previous.isEnabled()).isFalse();
		assertThat(next.isEnabled()).isTrue();

		next.click();
		next.click();

		assertThat(comboBox.getValue()).isEqualTo("Clara");
		assertThat(previous.isEnabled()).isTrue();
		assertThat(next.isEnabled()).isFalse();
		assertThat(selectedItems).containsExactly("Bernd", "Anna", "Bernd", "Clara");
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
