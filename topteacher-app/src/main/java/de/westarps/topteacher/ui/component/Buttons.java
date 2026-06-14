package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

public final class Buttons {

	private Buttons() {
	}

	public static Button command(final String text, final VaadinIcon icon) {
		return new Button(text, icon.create());
	}

	public static Button command(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		return new Button(text, icon.create(), listener);
	}

	public static Button icon(final String label, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = new Button(icon.create(), listener);
		button.setAriaLabel(label);
		button.setTooltipText(label);
		return button;
	}

	public static Button primary(final String text, final VaadinIcon icon) {
		final Button button = command(text, icon);
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		return button;
	}

	public static Button primary(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = command(text, icon, listener);
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		return button;
	}

	public static Button danger(final String text, final VaadinIcon icon) {
		final Button button = primary(text, icon);
		button.addThemeVariants(ButtonVariant.LUMO_ERROR);
		return button;
	}

	public static Button danger(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = primary(text, icon, listener);
		button.addThemeVariants(ButtonVariant.LUMO_ERROR);
		return button;
	}

	public static Button save() {
		return primary("Speichern", VaadinIcon.CHECK);
	}

	public static Button save(final ComponentEventListener<ClickEvent<Button>> listener) {
		return primary("Speichern", VaadinIcon.CHECK, listener);
	}

	public static Button newItem() {
		return command("Neu", VaadinIcon.STAR);
	}

	public static Button newItem(final ComponentEventListener<ClickEvent<Button>> listener) {
		return command("Neu", VaadinIcon.STAR, listener);
	}

	public static Button createOrSave() {
		return primary("Anlegen", VaadinIcon.PLUS);
	}

	public static Button archive() {
		return command("Archivieren...", VaadinIcon.ARCHIVE);
	}

	public static Button archive(final ComponentEventListener<ClickEvent<Button>> listener) {
		return command("Archivieren...", VaadinIcon.ARCHIVE, listener);
	}

	public static Button duplicateOpener() {
		return command("Duplizieren...", VaadinIcon.COPY);
	}

	public static Button duplicate(final ComponentEventListener<ClickEvent<Button>> listener) {
		return primary("Duplizieren", VaadinIcon.COPY, listener);
	}

	public static Button deleteIcon(final String label, final String confirmationHeader, final Runnable confirmAction) {
		return icon(label, VaadinIcon.TRASH,
				event -> TopTeacherDialogs.openDeleteConfirmation(confirmationHeader, confirmAction));
	}

	public static Button reset(final String text) {
		return command(text, VaadinIcon.ROTATE_LEFT);
	}

	public static Button reset(final String text, final ComponentEventListener<ClickEvent<Button>> listener) {
		return command(text, VaadinIcon.ROTATE_LEFT, listener);
	}

	public static Button resetOpener() {
		return reset("Datenbank zurücksetzen...");
	}

	public static Button resetOpener(final ComponentEventListener<ClickEvent<Button>> listener) {
		return reset("Datenbank zurücksetzen...", listener);
	}

	public static Button resetConfirmationOpener() {
		return danger("Zurücksetzen...", VaadinIcon.ROTATE_LEFT);
	}

	public static Button reset() {
		return danger("Zurücksetzen", VaadinIcon.ROTATE_LEFT);
	}

	public static Button reset(final ComponentEventListener<ClickEvent<Button>> listener) {
		return danger("Zurücksetzen", VaadinIcon.ROTATE_LEFT, listener);
	}

	public static void setCreateOrSaveMode(final Button button, final boolean editMode) {
		button.setText(editMode ? "Speichern" : "Anlegen");
		button.setIcon((editMode ? VaadinIcon.CHECK : VaadinIcon.PLUS).create());
	}
}
