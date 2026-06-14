package de.westarps.topteacher.ui.view;

import org.springframework.core.annotation.Order;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.ui.component.Buttons;

@Order(100)
@UIScope
@SpringComponent
public class ResetSettingsTab extends VerticalLayout implements SettingsTab {

	private final DatabaseInitializationDialogFactory dialogFactory;

	public ResetSettingsTab(final DatabaseInitializationDialogFactory dialogFactory) {
		this.dialogFactory = dialogFactory;
		configureContent();
	}

	@Override
	public String label() {
		return "Zurücksetzen";
	}

	@Override
	public Component content() {
		return this;
	}

	private void configureContent() {
		addClassName("tt-settings-content");
		setPadding(false);
		setSpacing(true);
		setWidthFull();

		final Span description = new Span("Setzt die Datenbank zurück. Dabei werden alle vorhandenen Daten gelöscht.");
		description.addClassName("tt-settings-description");

		final Button initializeButton = Buttons.resetOpener(event -> dialogFactory.openResetDialog());

		add(description, initializeButton);
	}
}
