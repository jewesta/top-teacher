package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
	@ParameterizedTest
	@EnumSource(MarkdownAlertType.class)
	void cursorInsideExistingAlertLoadsItsTypeAndUnquotedBodyAndReplacesOnlyThatBlock(final MarkdownAlertType type) {
		final String body = "**Erste Zeile**\n\n- Zweite Zeile\n- [Link](https://example.org)";
		final String quote = "> [!" + type + "]\n> " + body.replace("\n", "\n> ");
		final String before = "Vorher\n\n";
		final String after = "\n\n> [!CAUTION]\n> Nachbar\n\nNachher";
		for (final int cursor : new int[] { before.length() + 3, before.length() + quote.indexOf("Erste"), before.length() + quote.length() }) {
			final var editor = new MarkdownEditor(before + quote + after);
			final var selection = new MarkdownEditor.BlockSelection(cursor, cursor);
			final var alert = editor.findAlert(selection).orElseThrow();
			assertThat(alert.type()).isEqualTo(type);
			assertThat(alert.text()).isEqualTo(body);
			editor.insertAlert(selection, MarkdownAlertType.TIP, "Neuer **Text**\n\nMit zweitem Absatz");
			assertThat(editor.getValue()).isEqualTo(before + "> [!TIP]\n> Neuer **Text**\n> \n> Mit zweitem Absatz" + after);
		}
	}

	@Test
	void selectionWithinAlertEditsTheWholeBlockAndPreservesNestedQuotesAndCodeIndentation() {
		final var editor = new MarkdownEditor("Anfang\n\n> [!IMPORTANT]\n>     code\n>\n> > inneres Zitat\n\nEnde");
		final int cursor = editor.getValue().indexOf("code");
		final var selection = new MarkdownEditor.BlockSelection(cursor, cursor + 4);
		final var alert = editor.findAlert(selection).orElseThrow();
		assertThat(alert.text()).isEqualTo("    code\n\n> inneres Zitat");
		editor.insertAlert(selection, alert.type(), alert.text());
		assertThat(editor.getValue()).isEqualTo("Anfang\n\n> [!IMPORTANT]\n>     code\n> \n> > inneres Zitat\n\nEnde");
	}

	@Test
	void keepsOuterListAndQuoteContainersWhenEditingNestedAlert() {
		for (final String prefix : new String[] { "- ", "> " }) {
			final String continuation = prefix.equals("- ") ? "  " : "> ";
			final var editor = new MarkdownEditor(prefix + "> [!WARNING]\n" + continuation + "> Alter Text\n\nNachher");
			final int cursor = editor.getValue().indexOf("Alter");
			final var selection = new MarkdownEditor.BlockSelection(cursor, cursor);
			editor.insertAlert(selection, MarkdownAlertType.NOTE, "Neu\nMehr");
			assertThat(editor.getValue()).isEqualTo(prefix + "> [!NOTE]\n" + continuation + "> Neu\n" + continuation + "> Mehr\n\nNachher");
		}
	}

	@Test
	void recognizesLazyContinuationAndKeepsCrLfOutsideAndInsideTheReplacedAlert() {
		final var editor = new MarkdownEditor("Vorher\r\n\r\n> [!TIP]\r\n> Zeile\r\nlazy continuation\r\n\r\nNachher");
		final int cursor = editor.getValue().indexOf("continuation");
		final var selection = new MarkdownEditor.BlockSelection(cursor, cursor);
		assertThat(editor.findAlert(selection).orElseThrow().text()).isEqualTo("Zeile\nlazy continuation");
		editor.insertAlert(selection, MarkdownAlertType.NOTE, "Neu\nWeiter");
		assertThat(editor.getValue()).isEqualTo("Vorher\r\n\r\n> [!NOTE]\r\n> Neu\r\n> Weiter\r\n\r\nNachher");
	}

	@Test
	void doesNotTreatCodeExamplesOrdinaryQuotesUnknownTypesOrAdjacentTextAsExistingAlerts() {
		for (final String source : new String[] { "```md\n> [!NOTE]\n> Beispiel\n```", "> [!NUMBERED]\n> > [!NOTE]\n> > Beispiel", "> Gewöhnliches Zitat", "> [!OTHER]\n> Beispiel", "> [!NOTE]\n> Inhalt\n\nNachher" }) {
			final var editor = new MarkdownEditor(source);
			final int cursor = source.contains("Beispiel") ? source.indexOf("Beispiel") : source.length();
			assertThat(editor.findAlert(new MarkdownEditor.BlockSelection(cursor, cursor))).isEmpty();
		}
	}

}
