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

	public static final class StateChangeEvent extends ComponentEvent<Tray> {

		private final TrayState previousState;
		private final TrayState state;

		private StateChangeEvent(final Tray source, final boolean fromClient, final TrayState previousState,
				final TrayState state) {
			super(source, fromClient);
			this.previousState = previousState;
			this.state = state;
		}

		public TrayState getPreviousState() {
			return previousState;
		}

		public TrayState getState() {
			return state;
		}
	}

	private final Icon toggleIcon = VaadinIcon.ANGLE_UP.create();

	private final Button notch = new Button(toggleIcon);

	private final VerticalLayout contentLayout = new VerticalLayout();

	private TrayState state = TrayState.PEEK;

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
		notch.addClickListener(event -> togglePeekShow(event.isFromClient()));

		contentLayout.addClassName("ws-tray-content");
		contentLayout.setPadding(false);
		contentLayout.setSpacing(true);
		contentLayout.setWidthFull();

		setHeader(notch);
		super.add(List.of(contentLayout));
		setLabel(label);
		updateState();
	}

	public String getLabel() {
		return notch.getText();
	}

	public void setLabel(final String label) {
		notch.setText(label == null ? "" : label);
	}

	public TrayState getState() {
		return state;
	}

	public void setState(final TrayState state) {
		setState(state, false);
	}

	public void hide() {
		setState(TrayState.HIDE);
	}

	public void peek() {
		setState(TrayState.PEEK);
	}

	public void show() {
		setState(TrayState.SHOW);
	}

	public Registration addStateChangeListener(final ComponentEventListener<StateChangeEvent> listener) {
		return addListener(StateChangeEvent.class, Objects.requireNonNull(listener, "listener must not be null"));
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

	private void togglePeekShow(final boolean fromClient) {
		if (state == TrayState.HIDE) {
			return;
		}
		setState(state == TrayState.PEEK ? TrayState.SHOW : TrayState.PEEK, fromClient);
	}

	private void setState(final TrayState state, final boolean fromClient) {
		final TrayState nextState = Objects.requireNonNull(state, "state must not be null");
		if (this.state == nextState) {
			return;
		}
		final TrayState previousState = this.state;
		this.state = nextState;
		updateState();
		fireEvent(new StateChangeEvent(this, fromClient, previousState, nextState));
	}

	private void updateState() {
		final boolean shown = state == TrayState.SHOW;
		super.setVisible(state != TrayState.HIDE);
		getElement().setAttribute("data-state", state.name().toLowerCase(java.util.Locale.ROOT));
		notch.getElement().setAttribute("aria-expanded", String.valueOf(shown));
		contentLayout.getElement().setAttribute("aria-hidden", String.valueOf(!shown));
		if (shown) {
			contentLayout.getElement().removeAttribute("inert");
		} else {
			contentLayout.getElement().setAttribute("inert", "");
		}
	}
}
