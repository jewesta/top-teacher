package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextArea;

import de.westarps.vaadin.markdown.MarkdownAlertType;
import de.westarps.vaadin.markdown.MarkdownEditor;

class MarkdownAlertDialogTests {
	private UI ui;

	@BeforeEach
	void setup() {
		ui = new UI();
		UI.setCurrent(ui);
	}

	@AfterEach
	void cleanup() {
		UI.setCurrent(null);
	}

	@Test
	void opensExistingAlertPrefilledAndAppliesOneReplacementAfterConfirmation() {
		final var editor = new MarkdownEditor("Vorher\n\n> [!WARNING]\n> **Achtung**\n> \n> Zweiter Absatz\n\nNachher");
		final String original = editor.getValue();
		final int cursor = original.indexOf("Achtung");
		MarkdownAlertDialog.forSelection(editor, new MarkdownEditor.BlockSelection(cursor, cursor)).open();
		final var dialog = components(ui, Dialog.class).findFirst().orElseThrow();
		final var type = components(dialog, ComboBox.class).findFirst().orElseThrow();
		final var text = components(dialog, TextArea.class).findFirst().orElseThrow();
		assertThat(type.getValue()).isEqualTo(MarkdownAlertType.WARNING);
		assertThat(text.getValue()).isEqualTo("**Achtung**\n\nZweiter Absatz");
		assertThat(editor.getValue()).isEqualTo(original);
		type.setValue(MarkdownAlertType.TIP);
		text.setValue("Geänderter Hinweis");
		button(dialog, "Übernehmen").click();
		assertThat(editor.getValue()).isEqualTo("Vorher\n\n> [!TIP]\n> Geänderter Hinweis\n\nNachher");
		assertThat(dialog.isOpened()).isFalse();
	}

	@Test
	void cancellingExistingAlertDoesNotChangeSource() {
		final var editor = new MarkdownEditor("> [!TIP]\n> Alter Hinweis");
		MarkdownAlertDialog.forSelection(editor, new MarkdownEditor.BlockSelection(15, 15)).open();
		final var dialog = components(ui, Dialog.class).findFirst().orElseThrow();
		components(dialog, TextArea.class).findFirst().orElseThrow().setValue("Noch nicht übernommen");
		button(dialog, "Abbrechen").click();
		assertThat(editor.getValue()).isEqualTo("> [!TIP]\n> Alter Hinweis");
	}

	@Test
	void ordinarySelectionStillOpensInsertionDialogWithNoteAsDefault() {
		final var editor = new MarkdownEditor("Text markieren");
		MarkdownAlertDialog.forSelection(editor, new MarkdownEditor.BlockSelection(0, 4)).open();
		final var dialog = components(ui, Dialog.class).findFirst().orElseThrow();
		assertThat(components(dialog, ComboBox.class).findFirst().orElseThrow().getValue()).isEqualTo(MarkdownAlertType.NOTE);
		assertThat(components(dialog, TextArea.class).findFirst().orElseThrow().getValue()).isEqualTo("Text markieren");
		button(dialog, "Einfügen").click();
		assertThat(editor.getValue()).isEqualTo("> [!NOTE]\n> Text markieren");
	}

	private static Button button(final Component root, final String label) {
		return components(root, Button.class).filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
	}

	private static <T extends Component> Stream<T> components(final Component root, final Class<T> type) {
		return Stream.concat(type.isInstance(root) ? Stream.of(type.cast(root)) : Stream.empty(),
				root.getChildren().flatMap(child -> components(child, type)));
	}
}
