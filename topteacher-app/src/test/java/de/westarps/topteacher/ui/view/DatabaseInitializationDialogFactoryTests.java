package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;

import de.westarps.topteacher.backend.database.DatabaseInitializationMode;
import de.westarps.topteacher.backend.database.DatabaseInitializationService;

@SuppressWarnings({ "rawtypes", "unchecked" })
class DatabaseInitializationDialogFactoryTests {

	private UI ui;
	private DatabaseInitializationService initializationService;
	private DatabaseInitializationDialogFactory dialogFactory;

	@BeforeEach
	void setUp() {
		ui = new UI();
		UI.setCurrent(ui);
		initializationService = mock(DatabaseInitializationService.class);
		dialogFactory = new DatabaseInitializationDialogFactory(initializationService);
	}

	@AfterEach
	void tearDown() {
		UI.setCurrent(null);
	}

	@Test
	void opensMandatoryFirstStartDialogAndInitializesSelectedMode() {
		when(initializationService.shouldPromptForFirstStart()).thenReturn(true);

		assertThat(dialogFactory.openFirstStartDialogIfRequired()).isTrue();

		final Dialog dialog = dialogFactory.createFirstStartDialog();
		assertThat(dialog.isCloseOnEsc()).isFalse();
		assertThat(dialog.isCloseOnOutsideClick()).isFalse();
		assertThat(buttons(dialog).stream().map(Button::getText)).containsExactly("Starten");

		modeSelect(dialog).setValue(DatabaseInitializationMode.DEMO);
		button(dialog, "Starten").click();

		verify(initializationService).initialize(DatabaseInitializationMode.DEMO);
	}

	@Test
	void skipsFirstStartDialogWhenPromptIsNotRequired() {
		when(initializationService.shouldPromptForFirstStart()).thenReturn(false);

		assertThat(dialogFactory.openFirstStartDialogIfRequired()).isFalse();

		assertThat(components(ui, Dialog.class)).isEmpty();
	}

	@Test
	void resetFlowRequiresSecondConfirmationBeforeInitializing() {
		final Dialog selectionDialog = dialogFactory.createResetDialog();
		modeSelect(selectionDialog).setValue(DatabaseInitializationMode.EMPTY);
		button(selectionDialog, "Zurücksetzen").click();

		verify(initializationService, never()).initialize(DatabaseInitializationMode.EMPTY);

		final Dialog confirmationDialog = dialogFactory.createResetConfirmationDialog(DatabaseInitializationMode.EMPTY);
		assertThat(buttons(confirmationDialog).stream().map(Button::getText)).contains("Abbrechen", "Zurücksetzen");

		button(confirmationDialog, "Zurücksetzen").click();

		verify(initializationService).initialize(DatabaseInitializationMode.EMPTY);
	}

	private static ComboBox<DatabaseInitializationMode> modeSelect(final Component root) {
		return components(root, ComboBox.class).getFirst();
	}

	private static Button button(final Component root, final String text) {
		return buttons(root).stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
	}

	private static List<Button> buttons(final Component root) {
		return components(root, Button.class);
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
