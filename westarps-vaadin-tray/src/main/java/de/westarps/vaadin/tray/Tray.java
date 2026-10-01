package de.westarps.vaadin.tray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.ClientCallable;
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

import de.westarps.vaadin.animate.Animations;
import de.westarps.vaadin.animate.Effect;
import de.westarps.vaadin.animate.Speed;
import de.westarps.vaadin.badge.AttachedBadge.Position;
import de.westarps.vaadin.badge.BadgeController;
import de.westarps.vaadin.badge.Badgeable;
import de.westarps.vaadin.badge.BadgedComponent;

@SuppressWarnings("serial")
@CssImport(Animations.STYLESHEET)
@CssImport("./styles/ws-tray.css")
public abstract class Tray<I> extends Card implements TrayController<I> {

	public static final class StateChangeEvent extends ComponentEvent<Tray<?>> {

		private final TrayState previousState;
		private final TrayState state;

		private StateChangeEvent(final Tray<?> source, final boolean fromClient, final TrayState previousState,
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

	private record RenderedItem<I>(I item, Component component) {
	}

	private static final class TrayNotch extends Button implements Badgeable<TrayNotch> {

		private BadgeController badgeController;

		private TrayNotch(final Icon icon) {
			super(icon);
		}

		@Override
		public void onBadged(final BadgeController badgeController) {
			this.badgeController = badgeController;
		}

		private BadgeController getBadgeController() {
			return Objects.requireNonNull(badgeController, "notch has no attached badge");
		}
	}

	private final Icon toggleIcon = VaadinIcon.ANGLE_UP.create();

	private final TrayNotch notch = new TrayNotch(toggleIcon);

	private final VerticalLayout contentLayout = new VerticalLayout();
	private final List<RenderedItem<I>> renderedItems = new ArrayList<>();

	private TrayState state = TrayState.PEEK;

	protected Tray() {
		this("");
	}

	protected Tray(final String label) {
		this(label, null);
	}

	protected Tray(final String label, final Position badgePosition) {
		addClassName("ws-tray");
		addThemeVariants(CardVariant.LUMO_ELEVATED);
		toggleIcon.addClassName("ws-tray-toggle-icon");
		toggleIcon.getElement().setAttribute("aria-hidden", "true");
		notch.addClassName("ws-tray-notch");
		notch.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		notch.setWidthFull();
		notch.addClickListener(event -> togglePeekShow(event.isFromClient()));
		addAttachListener(event -> installOutsideClickListener());
		addDetachListener(event -> removeOutsideClickListener());

		contentLayout.addClassName("ws-tray-content");
		contentLayout.setPadding(false);
		contentLayout.setSpacing(true);
		contentLayout.setWidthFull();

		if (badgePosition == null) {
			setHeader(notch);
		} else {
			final BadgedComponent<TrayNotch> badgedNotch = new BadgedComponent<>(badgePosition, notch);
			badgedNotch.addClassName("ws-tray-badged-notch");
			badgedNotch.getBadge().addClassName("ws-tray-notch-badge");
			badgedNotch.getBadge().addClickListener(event -> togglePeekShow(event.isFromClient()));
			badgedNotch.setWidthFull();
			setHeader(badgedNotch);
		}
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

	@Override
	public TrayState getState() {
		return state;
	}

	public void setState(final TrayState state) {
		setState(state, false);
	}

	@Override
	public void hide() {
		setState(TrayState.HIDE);
	}

	@Override
	public void peek() {
		setState(TrayState.PEEK);
	}

	@Override
	public void show() {
		setState(TrayState.SHOW);
	}

	public Registration addStateChangeListener(final ComponentEventListener<StateChangeEvent> listener) {
		return addListener(StateChangeEvent.class, Objects.requireNonNull(listener, "listener must not be null"));
	}

	protected final VerticalLayout getContentLayout() {
		return contentLayout;
	}

	protected final List<Component> getRenderedComponents() {
		return renderedItems.stream().map(RenderedItem::component).toList();
	}

	protected final BadgeController getNotchBadgeController() {
		return notch.getBadgeController();
	}

	@Override
	public final List<I> getItems() {
		return renderedItems.stream().map(RenderedItem::item).toList();
	}

	@Override
	public final void setItems(final Collection<? extends I> items) {
		final List<I> nextItems = copyItems(items);
		final List<I> previousItems = getItems();
		if (previousItems.equals(nextItems)) {
			return;
		}

		final List<RenderedItem<I>> nextRenderedItems = renderItems(nextItems);
		renderedItems.clear();
		renderedItems.addAll(nextRenderedItems);
		contentLayout.removeAll();
		contentLayout.add(nextRenderedItems.stream().map(RenderedItem::component).toList());
		scrollToTop();
		onItemsChanged(previousItems, getItems());
	}

	@Override
	public final void addItem(final I item) {
		addItems(List.of(Objects.requireNonNull(item, "item must not be null")));
	}

	@Override
	public final void addItems(final Collection<? extends I> items) {
		final List<I> addedItems = copyItems(items);
		if (addedItems.isEmpty()) {
			return;
		}

		final List<I> previousItems = getItems();
		final List<RenderedItem<I>> addedRenderedItems = renderItems(addedItems);
		renderedItems.addAll(0, addedRenderedItems);
		for (int index = 0; index < addedRenderedItems.size(); index++) {
			contentLayout.addComponentAtIndex(index, addedRenderedItems.get(index).component());
		}
		scrollToTop();
		onItemsChanged(previousItems, getItems());
	}

	@Override
	public final boolean removeItem(final I item) {
		Objects.requireNonNull(item, "item must not be null");
		for (int index = 0; index < renderedItems.size(); index++) {
			if (Objects.equals(renderedItems.get(index).item(), item)) {
				final List<I> previousItems = getItems();
				final RenderedItem<I> removedItem = renderedItems.remove(index);
				contentLayout.remove(removedItem.component());
				onItemsChanged(previousItems, getItems());
				return true;
			}
		}
		return false;
	}

	@Override
	public final void clearItems() {
		if (renderedItems.isEmpty()) {
			return;
		}
		final List<I> previousItems = getItems();
		renderedItems.clear();
		contentLayout.removeAll();
		onItemsChanged(previousItems, List.of());
	}

	protected abstract Component renderItem(I item);

	protected void onItemsChanged(final List<I> previousItems, final List<I> currentItems) {
	}

	private void togglePeekShow(final boolean fromClient) {
		if (state == TrayState.HIDE) {
			return;
		}
		if (state == TrayState.PEEK && renderedItems.isEmpty()) {
			Animations.playOnce(this, Effect.HEAD_SHAKE, Speed.FASTER);
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

	@ClientCallable
	public void handleOutsideClick() {
		if (state == TrayState.SHOW) {
			setState(TrayState.PEEK, true);
		}
	}

	private void installOutsideClickListener() {
		getElement().executeJs("""
			const tray = this;
			if (tray.__wsTrayOutsideClickListener) {
				return;
			}
			tray.__wsTrayOutsideClickListener = event => {
				if (tray.dataset.state === 'show' && !event.composedPath().includes(tray)) {
					tray.$server.handleOutsideClick();
				}
			};
			document.addEventListener('click', tray.__wsTrayOutsideClickListener, true);
			""");
	}

	private void removeOutsideClickListener() {
		getElement().executeJs("""
			if (!this.__wsTrayOutsideClickListener) {
				return;
			}
			document.removeEventListener('click', this.__wsTrayOutsideClickListener, true);
			delete this.__wsTrayOutsideClickListener;
			""");
	}

	private List<I> copyItems(final Collection<? extends I> items) {
		Objects.requireNonNull(items, "items must not be null");
		final List<I> copy = new ArrayList<>(items.size());
		for (final I item : items) {
			copy.add(Objects.requireNonNull(item, "item must not be null"));
		}
		return List.copyOf(copy);
	}

	private List<RenderedItem<I>> renderItems(final List<I> items) {
		return items.stream().map(item -> new RenderedItem<>(item,
				Objects.requireNonNull(renderItem(item), "rendered item must not be null"))).toList();
	}

	private void scrollToTop() {
		contentLayout.getElement().executeJs("this.scrollTop = 0");
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
