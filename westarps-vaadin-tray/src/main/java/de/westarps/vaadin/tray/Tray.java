package de.westarps.vaadin.tray;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.shared.Registration;

@SuppressWarnings("serial")
@CssImport("./styles/ws-tray.css")
public class Tray extends Card {

	public static final class OpenedChangeEvent extends ComponentEvent<Tray> {

		private final boolean opened;

		private OpenedChangeEvent(final Tray source, final boolean fromClient, final boolean opened) {
			super(source, fromClient);
			this.opened = opened;
		}

		public boolean isOpened() {
			return opened;
		}
	}

	private final Icon toggleIcon = VaadinIcon.ANGLE_UP.create();

	private final Button notch = new Button(toggleIcon);

	private final VerticalLayout contentLayout = new VerticalLayout();

	private boolean opened;

	public Tray() {
		this("");
	}

	public Tray(final String label) {
		addClassName("ws-tray");
		addThemeVariants(CardVariant.LUMO_ELEVATED);
		toggleIcon.addClassName("ws-tray-toggle-icon");
		toggleIcon.getElement().setAttribute("aria-hidden", "true");
		notch.addClassName("ws-tray-notch");
		notch.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		notch.setWidthFull();
		notch.addClickListener(event -> setOpened(!opened, event.isFromClient()));

		contentLayout.addClassName("ws-tray-content");
		contentLayout.setPadding(false);
		contentLayout.setSpacing(true);
		contentLayout.setWidthFull();

		setHeader(notch);
		super.add(List.of(contentLayout));
		setLabel(label);
		updateOpenedState();
	}

	public String getLabel() {
		return notch.getText();
	}

	public void setLabel(final String label) {
		notch.setText(label == null ? "" : label);
	}

	public boolean isOpened() {
		return opened;
	}

	public void setOpened(final boolean opened) {
		setOpened(opened, false);
	}

	public void open() {
		setOpened(true);
	}

	public void close() {
		setOpened(false);
	}

	public void toggle() {
		setOpened(!opened);
	}

	public Registration addOpenedChangeListener(final ComponentEventListener<OpenedChangeEvent> listener) {
		return addListener(OpenedChangeEvent.class, Objects.requireNonNull(listener, "listener must not be null"));
	}

	public VerticalLayout getContentLayout() {
		return contentLayout;
	}

	@Override
	public void add(final Component... components) {
		contentLayout.add(components);
	}

	@Override
	public void add(final Collection<Component> components) {
		contentLayout.add(components);
	}

	@Override
	public void remove(final Component... components) {
		contentLayout.remove(components);
	}

	@Override
	public void remove(final Collection<Component> components) {
		contentLayout.remove(components);
	}

	@Override
	public void removeAll() {
		contentLayout.removeAll();
	}

	@Override
	public void addComponentAtIndex(final int index, final Component component) {
		contentLayout.addComponentAtIndex(index, component);
	}

	private void setOpened(final boolean opened, final boolean fromClient) {
		if (this.opened == opened) {
			return;
		}
		this.opened = opened;
		updateOpenedState();
		fireEvent(new OpenedChangeEvent(this, fromClient, opened));
	}

	private void updateOpenedState() {
		getElement().setAttribute("data-opened", String.valueOf(opened));
		notch.getElement().setAttribute("aria-expanded", String.valueOf(opened));
		contentLayout.getElement().setAttribute("aria-hidden", String.valueOf(!opened));
		if (opened) {
			contentLayout.getElement().removeAttribute("inert");
		} else {
			contentLayout.getElement().setAttribute("inert", "");
		}
	}
}
