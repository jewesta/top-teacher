package de.westarps.topteacher.ui.component;

import java.util.Objects;
import java.util.function.BiConsumer;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.vaadin.markdown.MarkdownAlertType;
import de.westarps.vaadin.markdown.MarkdownEditor;

public final class MarkdownAlertDialog {

	private final Dialog dialog;
	private final Binder<AlertDetails> binder = new Binder<>(AlertDetails.class);
	private final AlertDetails details = new AlertDetails();
	private final ComboBox<MarkdownAlertType> type = new ComboBox<>("Art des Hinweises");
	private final TextArea text = new TextArea("Text");
	private final Button insert = TopTeacherDialogs.primaryButton("Einfügen");

	public static MarkdownAlertDialog forSelection(final MarkdownEditor editor, final MarkdownEditor.BlockSelection selection) {
		final var alert = editor.findAlert(selection);
		return new MarkdownAlertDialog(alert.map(MarkdownEditor.AlertBlock::type).orElse(MarkdownAlertType.NOTE),
				alert.map(MarkdownEditor.AlertBlock::text).orElseGet(() -> editor.selectedBlockText(selection)),
				alert.isPresent(), (type, text) -> editor.insertAlert(selection, type, text));
	}

	public MarkdownAlertDialog(final String initialText,
			final BiConsumer<MarkdownAlertType, String> resultHandler) {
		this(MarkdownAlertType.NOTE, initialText, false, resultHandler);
	}

	private MarkdownAlertDialog(final MarkdownAlertType initialType, final String initialText, final boolean editing,
			final BiConsumer<MarkdownAlertType, String> resultHandler) {
		Objects.requireNonNull(resultHandler, "resultHandler must not be null");
		details.setText(initialText == null ? "" : initialText);
		details.setType(initialType);
		insert.setText(editing ? "Übernehmen" : "Einfügen");
		type.setItems(MarkdownAlertType.values());
		type.setItemLabelGenerator(MarkdownAlertDialog::label);
		type.setAllowCustomValue(false);
		type.setRequiredIndicatorVisible(true);
		type.setWidthFull();
		text.setRequiredIndicatorVisible(true);
		text.setMinRows(3);
		text.setMaxRows(10);
		text.setValueChangeMode(ValueChangeMode.EAGER);
		text.setWidthFull();
		binder.forField(type).asRequired("Wähle die Art des Hinweises.")
				.bind(AlertDetails::getType, AlertDetails::setType);
		binder.forField(text).asRequired("Text ist erforderlich.")
				.withValidator(value -> !value.isBlank(), "Text ist erforderlich.")
				.bind(AlertDetails::getText, AlertDetails::setText);
		binder.readBean(details);
		binder.addStatusChangeListener(event -> insert.setEnabled(event.getBinder().isValid()));

		final VerticalLayout content = new VerticalLayout(type, text);
		content.addClassName("tt-dialog-content");
		content.setPadding(false);
		content.setWidthFull();
		final Button cancel = new Button("Abbrechen");
		dialog = TopTeacherDialogs.create(editing ? "Hinweisblock bearbeiten" : "Hinweisblock einfügen", content,
				TopTeacherDialogs.buttonBar(cancel, insert));
		dialog.setWidth("36rem");
		dialog.setCloseOnEsc(true);
		dialog.setCloseOnOutsideClick(false);
		cancel.addClickListener(event -> dialog.close());
		insert.addClickListener(event -> {
			if (binder.writeBeanIfValid(details)) {
				resultHandler.accept(details.getType(), details.getText());
				dialog.close();
			}
		});
		dialog.addOpenedChangeListener(event -> {
			if (event.isOpened()) {
				text.focus();
			}
		});
		dialog.addClosedListener(event -> dialog.getElement().removeFromParent());
		insert.setEnabled(!details.getText().isBlank());
	}

	public void open() {
		final var ui = UI.getCurrent();
		if (ui != null) {
			ui.add(dialog);
		}
		dialog.open();
	}

	private static String label(final MarkdownAlertType type) {
		return switch (type) {
			case NOTE -> "Hinweis";
			case TIP -> "Tipp";
			case IMPORTANT -> "Wichtig";
			case WARNING -> "Warnung";
			case CAUTION -> "Vorsicht";
		};
	}

	public static final class AlertDetails {

		private MarkdownAlertType type = MarkdownAlertType.NOTE;
		private String text = "";

		public MarkdownAlertType getType() {
			return type;
		}

		public void setType(final MarkdownAlertType type) {
			this.type = type;
		}

		public String getText() {
			return text;
		}

		public void setText(final String text) {
			this.text = text;
		}
	}
}
