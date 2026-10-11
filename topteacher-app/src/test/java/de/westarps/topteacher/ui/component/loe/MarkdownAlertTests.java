package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.westarps.vaadin.markdown.MarkdownAlertType;
import de.westarps.vaadin.markdown.MarkdownEditor;

class MarkdownAlertTests {

	@Test
	void insertsSeparatedMultilineAlertAtCursor() {
		final MarkdownEditor editor = new MarkdownEditor("Vorher\n\nNachher");
		editor.insertAlert(new MarkdownEditor.BlockSelection(8, 8), MarkdownAlertType.TIP,
				"Erste Zeile\n\n**Zweite Zeile**");
		assertThat(editor.getValue()).isEqualTo("Vorher\n\n> [!TIP]\n> Erste Zeile\n> \n> **Zweite Zeile**\n\nNachher");
	}

	@Test
	void wrapsSelectedWholeLinesAndKeepsAdjacentText() {
		final MarkdownEditor editor = new MarkdownEditor("Vorher\nErste Zeile\nZweite Zeile\nNachher");
		final MarkdownEditor.BlockSelection selection = new MarkdownEditor.BlockSelection(10, 30);
		assertThat(editor.selectedBlockText(selection)).isEqualTo("Erste Zeile\nZweite Zeile");
		editor.insertAlert(selection, MarkdownAlertType.IMPORTANT, editor.selectedBlockText(selection));
		assertThat(editor.getValue()).isEqualTo("Vorher\n\n> [!IMPORTANT]\n> Erste Zeile\n> Zweite Zeile\n\nNachher");
	}
}
