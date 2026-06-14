package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;

class ResetSettingsTabTests {

	@Test
	void opensDatabaseInitializationDialogFromSettings() {
		final DatabaseInitializationDialogFactory dialogFactory = mock(DatabaseInitializationDialogFactory.class);
		final ResetSettingsTab tab = new ResetSettingsTab(dialogFactory);

		assertThat(tab.label()).isEqualTo("Zurücksetzen");

		button(tab, "Datenbank zurücksetzen...").click();

		verify(dialogFactory).openResetDialog();
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
