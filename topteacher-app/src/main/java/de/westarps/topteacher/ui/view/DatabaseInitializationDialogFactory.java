package de.westarps.topteacher.ui.view;

import java.util.Objects;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.ApplicationVersion;
import de.westarps.topteacher.backend.database.DatabaseInitializationMode;
import de.westarps.topteacher.backend.database.DatabaseInitializationService;
import de.westarps.topteacher.model.HasDisplayName;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

@UIScope
@SpringComponent
public class DatabaseInitializationDialogFactory {

	private final DatabaseInitializationService initializationService;

	public DatabaseInitializationDialogFactory(final DatabaseInitializationService initializationService) {
		this.initializationService = initializationService;
	}

	public boolean openFirstStartDialogIfRequired() {
		if (!initializationService.shouldPromptForFirstStart()) {
			return false;
		}

		final Dialog dialog = createFirstStartDialog();
		dialog.open();
		return true;
	}

	public void openResetDialog() {
		createResetDialog().open();
	}

	Dialog createFirstStartDialog() {
		final ComboBox<DatabaseInitializationMode> mode = modeSelect();
		final Button initializeButton = TopTeacherDialogs.primaryButton("Starten");
		final Dialog dialog = TopTeacherDialogs.create("Willkommen bei " + ApplicationVersion.DISPLAY_APP_NAME,
				firstStartContent(mode), TopTeacherDialogs.buttonBar(null, initializeButton));
		dialog.setCloseOnEsc(false);
		dialog.setCloseOnOutsideClick(false);
		initializeButton.addClickListener(event -> initialize(dialog, mode.getValue()));
		return dialog;
	}

	Dialog createResetDialog() {
		final ComboBox<DatabaseInitializationMode> mode = modeSelect();
		final Button cancelButton = new Button("Abbrechen");
		final Button resetButton = Buttons.resetConfirmationOpener();
		final Dialog dialog = TopTeacherDialogs.create("Datenbank zurücksetzen", resetContent(mode),
				TopTeacherDialogs.buttonBar(cancelButton, resetButton));
		cancelButton.addClickListener(event -> dialog.close());
		resetButton.addClickListener(event -> {
			dialog.close();
			openResetConfirmationDialog(mode.getValue());
		});
		return dialog;
	}

	private void openResetConfirmationDialog(final DatabaseInitializationMode mode) {
		createResetConfirmationDialog(mode).open();
	}

	Dialog createResetConfirmationDialog(final DatabaseInitializationMode mode) {
		final Button cancelButton = new Button("Abbrechen");
		final Button resetButton = Buttons.reset();
		final Dialog dialog = TopTeacherDialogs.create("Datenbank endgültig zurücksetzen", confirmationContent(mode),
				TopTeacherDialogs.buttonBar(cancelButton, resetButton));
		cancelButton.addClickListener(event -> dialog.close());
		resetButton.addClickListener(event -> initialize(dialog, mode));
		return dialog;
	}

	private void initialize(final Dialog dialog, final DatabaseInitializationMode mode) {
		try {
			initializationService.initialize(Objects.requireNonNull(mode));
			dialog.close();
			UI.getCurrent().getPage().reload();
		} catch (final RuntimeException exception) {
			Notification.show(exception.getMessage());
		}
	}

	private static VerticalLayout firstStartContent(final ComboBox<DatabaseInitializationMode> mode) {
		final VerticalLayout content = dialogContent();
		content.add(description("Bitte wählen Sie, wie " + ApplicationVersion.DISPLAY_APP_NAME + " starten soll."),
				mode, description(
						"Sie können die Datenbank später auch in den Einstellungen zurücksetzen oder mit Demodaten initialisieren lassen."));
		return content;
	}

	private static VerticalLayout resetContent(final ComboBox<DatabaseInitializationMode> mode) {
		final VerticalLayout content = dialogContent();
		content.add(description(
				"Die Datenbank wird zurückgesetzt. Alle vorhandenen Schüler:innen, Kurse, Klausuren, Erwartungshorizonte, Fächer und Bewertungsskalen werden gelöscht."),
				mode);
		return content;
	}

	private static VerticalLayout confirmationContent(final DatabaseInitializationMode mode) {
		final VerticalLayout content = dialogContent();
		content.add(
				description(
						"Alle vorhandenen Daten werden gelöscht. Diese Aktion kann nicht rückgängig gemacht werden."),
				description("Auswahl: " + mode.getDisplayName()));
		return content;
	}

	private static VerticalLayout dialogContent() {
		final VerticalLayout content = new VerticalLayout();
		content.addClassName("tt-dialog-content");
		content.setPadding(false);
		content.setSpacing(true);
		content.setWidthFull();
		return content;
	}

	private static ComboBox<DatabaseInitializationMode> modeSelect() {
		final ComboBox<DatabaseInitializationMode> mode = new ComboBox<>("Startdaten");
		mode.setItems(DatabaseInitializationMode.values());
		mode.setItemLabelGenerator(HasDisplayName::getDisplayName);
		mode.setValue(DatabaseInitializationMode.DEMO);
		mode.setRequiredIndicatorVisible(true);
		mode.setWidthFull();
		return mode;
	}

	private static Span description(final String text) {
		final Span description = new Span(text);
		description.addClassName("tt-settings-description");
		return description;
	}
}
