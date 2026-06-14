package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
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
}
