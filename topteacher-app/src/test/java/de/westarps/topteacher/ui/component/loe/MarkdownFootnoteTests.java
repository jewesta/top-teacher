package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownToolbarCommand;

class MarkdownFootnoteTests {

	@Test
	void insertsReferenceAtCursorAndDefinitionAtEnd() {
		final MarkdownEditor editor = new MarkdownEditor("Ein Satz.");

		editor.insertFootnote(new MarkdownEditor.InsertionPoint(8), "Eine Quelle");

		assertThat(editor.getValue()).isEqualTo("Ein Satz[^1].\n\n[^1]: Eine Quelle");
	}

	@Test
	void usesNextNumericIdentifierAndIndentsMultilineDefinitions() {
		final MarkdownEditor editor = new MarkdownEditor("Text[^3].\n\n[^3]: Bestehend");

		editor.insertFootnote(new MarkdownEditor.InsertionPoint(4), "Erste Zeile\r\nZweite Zeile");

		assertThat(editor.getValue()).isEqualTo("Text[^4][^3].\n\n[^3]: Bestehend\n\n"
				+ "[^4]: Erste Zeile\n    Zweite Zeile");
	}

	@Test
	void enablesFootnoteCommandOnlyForMaterialEditors() {
		final MarkdownEditor genericEditor = new MarkdownEditor();
		final MaterialMarkdownEditor materialEditor = new MaterialMarkdownEditor("", List.of());

		assertThat(genericEditor.getHiddenToolbarCommands()).contains(MarkdownToolbarCommand.FOOTNOTE);
		assertThat(materialEditor.getHiddenToolbarCommands()).doesNotContain(MarkdownToolbarCommand.FOOTNOTE);
	}
}
