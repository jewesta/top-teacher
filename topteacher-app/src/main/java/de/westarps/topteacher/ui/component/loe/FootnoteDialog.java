package de.westarps.topteacher.ui.component.loe;

import java.util.Objects;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.ui.component.TopTeacherDialogs;

final class FootnoteDialog {

	private final Dialog dialog;
	private final Binder<FootnoteDetails> binder = new Binder<>(FootnoteDetails.class);
	private final FootnoteDetails details = new FootnoteDetails();
	private final TextArea text = new TextArea("Fußnotentext");
	private final Button insert = TopTeacherDialogs.primaryButton("Einfügen");

	FootnoteDialog(final Consumer<String> resultHandler) {
		Objects.requireNonNull(resultHandler, "resultHandler must not be null");
		text.setRequiredIndicatorVisible(true);
		text.setMinRows(3);
		text.setMaxRows(8);
		text.setValueChangeMode(ValueChangeMode.EAGER);
		text.setWidthFull();

		binder.forField(text).asRequired("Fußnotentext ist erforderlich.")
				.withValidator(value -> !value.isBlank(), "Fußnotentext ist erforderlich.")
				.bind(FootnoteDetails::getText, FootnoteDetails::setText);
		binder.readBean(details);
		binder.addStatusChangeListener(event -> insert.setEnabled(event.getBinder().isValid()));

		final VerticalLayout content = new VerticalLayout(text);
		content.addClassName("tt-dialog-content");
		content.setPadding(false);
		content.setWidthFull();
		final Button cancel = new Button("Abbrechen");
		dialog = TopTeacherDialogs.create("Fußnote einfügen", content,
				TopTeacherDialogs.buttonBar(cancel, insert));
		dialog.setCloseOnEsc(true);
		dialog.setCloseOnOutsideClick(false);
		dialog.setWidth("36rem");
		cancel.addClickListener(event -> dialog.close());
		insert.addClickListener(event -> {
			if (!binder.writeBeanIfValid(details)) {
				return;
			}
			resultHandler.accept(details.getText());
			dialog.close();
		});
		dialog.addOpenedChangeListener(event -> {
			if (event.isOpened()) {
				text.focus();
			}
		});
		insert.setEnabled(false);
	}

	void open() {
		dialog.open();
	}

	public static final class FootnoteDetails {

		private String text = "";

		public String getText() {
			return text;
		}

		public void setText(final String text) {
			this.text = text;
		}
	}
}
