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
		setIconLabel(button, label);
		return button;
	}

	public static void setIconLabel(final Button button, final String label) {
		button.setAriaLabel(label);
		button.setTooltipText(label);
		if (button.hasClassName("tt-toolbar-action")) {
			button.setText(label);
		}
	}

	public static Button dialogOpener(final String label, final VaadinIcon icon) {
		return command(dialogLabel(label), icon);
	}

	public static Button dialogOpener(final String label) {
		return new Button(dialogLabel(label));
	}

	public static Button dialogOpener(final String label, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		return command(dialogLabel(label), icon, listener);
	}

	public static Button dialogIcon(final String label, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		return icon(dialogLabel(label), icon, listener);
	}

	private static String dialogLabel(final String label) {
		return label.endsWith("...") || label.endsWith("…") ? label : label + "...";
	}

	public static boolean isAdd(final Button button) {
		return button.hasClassName("tt-toolbar-add-action");
	}

	public static Button add(final String text, final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = command(text, VaadinIcon.PLUS, listener);
		button.addClassName("tt-toolbar-add-action");
		return button;
	}

	public static void configureToolbarAction(final Button button) {
		button.addClassName("tt-toolbar-action");
		button.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY,
				ButtonVariant.LUMO_TERTIARY_INLINE);
		if (isAdd(button)) {
			button.removeThemeVariants(ButtonVariant.LUMO_ICON);
			return;
		}
		if (!button.getText().isBlank()) {
			button.setAriaLabel(button.getText());
			button.setTooltipText(button.getText());
		} else {
			button.setText(button.getAriaLabel().orElse(""));
		}
		/* Keep the label available: the shared toolbar theme reveals it for a lone action. */
		button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
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

	public static Button save() {
		return primary("Speichern", VaadinIcon.CHECK);
	}

	public static Button save(final ComponentEventListener<ClickEvent<Button>> listener) {
		return primary("Speichern", VaadinIcon.CHECK, listener);
	}

	public static Button newItem() {
		return command("Neu", VaadinIcon.PLUS);
	}

	public static Button createOrSave() {
		return primary("Anlegen", VaadinIcon.PLUS);
	}

	public static Button archive() {
		return dialogOpener("Archivieren", VaadinIcon.ARCHIVE);
	}

	public static Button duplicateOpener() {
		return dialogOpener("Duplizieren", VaadinIcon.COPY);
	}

	public static Button duplicate(final ComponentEventListener<ClickEvent<Button>> listener) {
		return primary("Duplizieren", VaadinIcon.COPY, listener);
	}

	public static Button deleteIcon(final String label, final String confirmationHeader, final Runnable confirmAction) {
		return dialogIcon(label, VaadinIcon.TRASH,
				event -> TopTeacherDialogs.openDeleteConfirmation(confirmationHeader, confirmAction));
	}

	public static Button reset(final String text, final ComponentEventListener<ClickEvent<Button>> listener) {
		return command(text, VaadinIcon.ROTATE_LEFT, listener);
	}

	public static Button resetOpener(final ComponentEventListener<ClickEvent<Button>> listener) {
		return dialogOpener("Datenbank zurücksetzen", VaadinIcon.ROTATE_LEFT, listener);
	}

	public static Button resetConfirmationOpener() {
		return danger(dialogLabel("Zurücksetzen"), VaadinIcon.ROTATE_LEFT);
	}

	public static Button reset() {
		return danger("Zurücksetzen", VaadinIcon.ROTATE_LEFT);
	}

	public static void setCreateOrSaveMode(final Button button, final boolean editMode) {
		button.setText(editMode ? "Speichern" : "Anlegen");
		button.setIcon((editMode ? VaadinIcon.CHECK : VaadinIcon.PLUS).create());
		if (button.getClassNames().contains("tt-toolbar-action")) {
			button.setAriaLabel(button.getText());
			button.setTooltipText(button.getText());
			configureToolbarAction(button);
		}
	}
}
