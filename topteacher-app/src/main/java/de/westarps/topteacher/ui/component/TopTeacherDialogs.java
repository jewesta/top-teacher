package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public final class TopTeacherDialogs {

	private TopTeacherDialogs() {
	}

	public static Dialog create(final String title, final Component content, final Component buttonBar) {
		final Dialog dialog = new Dialog();
		dialog.addClassName("tt-dialog");
		final HorizontalLayout header = new HorizontalLayout();
		header.addClassName("tt-dialog-header");
		header.setAlignItems(Alignment.CENTER);
		header.setPadding(false);
		header.setSpacing(false);

		final Span headerText = new Span(title);
		headerText.addClassName("tt-dialog-header-text");
		header.add(headerText);

		dialog.add(header, content, buttonBar);
		dialog.setDraggable(true);
		return dialog;
	}

	public static HorizontalLayout buttonBar(final Button cancelButton, final Button... actionButtons) {
		final HorizontalLayout buttonBar = new HorizontalLayout();
		buttonBar.addClassName("tt-dialog-buttonbar");
		buttonBar.setJustifyContentMode(JustifyContentMode.END);
		buttonBar.setPadding(false);
		buttonBar.setSpacing(true);

		if (cancelButton != null) {
			cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
			cancelButton.getStyle().set("margin-inline-end", "auto");
			buttonBar.add(cancelButton);
		}
		buttonBar.add(actionButtons);
		return buttonBar;
	}

	public static Button primaryButton(final String text) {
		final Button button = new Button(text);
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		return button;
	}

	public static Button dangerButton(final String text) {
		final Button button = primaryButton(text);
		button.addThemeVariants(ButtonVariant.LUMO_ERROR);
		return button;
	}

	public static ConfirmDialog deleteConfirmation(final String header, final Runnable confirmAction) {
		final ConfirmDialog dialog = new ConfirmDialog();
		configureDeleteConfirmation(dialog, header, confirmAction);
		return dialog;
	}

	public static ConfirmDialog archiveConfirmation(final String header, final String text,
			final Runnable confirmAction) {
		final ConfirmDialog dialog = new ConfirmDialog();
		configureArchiveConfirmation(dialog, header, text, confirmAction);
		return dialog;
	}

	public static ConfirmDialog archiveConfirmation(final String header, final String firstParagraph,
			final String secondParagraph, final Runnable confirmAction) {
		final ConfirmDialog dialog = new ConfirmDialog();
		configureArchiveConfirmation(dialog, header, firstParagraph, secondParagraph, confirmAction);
		return dialog;
	}

	public static void configureDeleteConfirmation(final ConfirmDialog dialog, final String header,
			final Runnable confirmAction) {
		dialog.setHeader(header);
		dialog.setText("Diese Aktion kann nicht rückgängig gemacht werden.");
		dialog.setCancelable(true);
		dialog.setCancelText("Abbrechen");
		dialog.setConfirmText("Löschen");
		dialog.setConfirmButtonTheme("error primary");
		dialog.addConfirmListener(event -> confirmAction.run());
	}

	public static void configureArchiveConfirmation(final ConfirmDialog dialog, final String header, final String text,
			final Runnable confirmAction) {
		dialog.setHeader(header);
		dialog.removeAll();
		dialog.setText(text);
		configureArchiveConfirmationActions(dialog, confirmAction);
	}

	public static void configureArchiveConfirmation(final ConfirmDialog dialog, final String header,
			final String firstParagraph, final String secondParagraph, final Runnable confirmAction) {
		dialog.setHeader(header);
		dialog.removeAll();
		dialog.setText(paragraphs(firstParagraph, secondParagraph));
		configureArchiveConfirmationActions(dialog, confirmAction);
	}

	private static void configureArchiveConfirmationActions(final ConfirmDialog dialog, final Runnable confirmAction) {
		dialog.setCancelable(true);
		dialog.setCancelText("Abbrechen");
		dialog.setConfirmText("Archivieren");
		dialog.setConfirmButtonTheme("primary");
		dialog.addConfirmListener(event -> confirmAction.run());
	}

	private static Component paragraphs(final String firstParagraph, final String secondParagraph) {
		final Paragraph first = new Paragraph(firstParagraph);
		first.getStyle().set("margin", "0 0 var(--lumo-space-m) 0");
		final Paragraph second = new Paragraph(secondParagraph);
		second.getStyle().set("margin", "0");
		final Div text = new Div(first, second);
		return text;
	}

	public static void openDeleteConfirmation(final String header, final Runnable confirmAction) {
		final ConfirmDialog dialog = deleteConfirmation(header, confirmAction);
		final UI ui = UI.getCurrent();
		if (ui != null) {
			ui.add(dialog);
		}
		dialog.open();
	}

	public static void openArchiveConfirmation(final String header, final String text, final Runnable confirmAction) {
		final ConfirmDialog dialog = archiveConfirmation(header, text, confirmAction);
		final UI ui = UI.getCurrent();
		if (ui != null) {
			ui.add(dialog);
		}
		dialog.open();
	}

	public static void openArchiveConfirmation(final String header, final String firstParagraph,
			final String secondParagraph, final Runnable confirmAction) {
		final ConfirmDialog dialog = archiveConfirmation(header, firstParagraph, secondParagraph, confirmAction);
		final UI ui = UI.getCurrent();
		if (ui != null) {
			ui.add(dialog);
		}
		dialog.open();
	}
}
