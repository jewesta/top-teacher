package de.westarps.vaadin.badge;

import com.vaadin.flow.component.Component;

import de.westarps.vaadin.badge.AttachedBadge.Position;

/**
 * Implemented by components that need access to an attached badge.
 *
 * @param <C>
 *            implementing component type
 */
public interface Badgeable<C extends Component & Badgeable<C>> {

	void onBadged(BadgeController badgeController);

	/**
	 * Wraps the component in a {@link BadgedComponent} if it implements
	 * {@link Badgeable}; otherwise returns the component unchanged.
	 *
	 * @param position
	 *            badge position
	 * @param component
	 *            component to inspect
	 * @return the wrapped or original component
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static Component badgeIfPossible(final Position position, final Component component) {
		if (component instanceof final Badgeable badgeable) {
			return new BadgedComponent<>(position, (Component & Badgeable) badgeable);
		}
		return component;
	}
}
