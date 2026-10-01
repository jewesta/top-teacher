package de.westarps.vaadin.badge;

import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;

import de.westarps.vaadin.badge.AttachedBadge.Position;

/**
 * Relative-positioned wrapper containing a component and its protruding badge.
 *
 * @param <C>
 *            wrapped component type
 */
@CssImport("./styles/ws-badge.css")
@SuppressWarnings("serial")
public class BadgedComponent<C extends Component & Badgeable<C>> extends Div {

	private final C component;
	private final AttachedBadge badge;

	public BadgedComponent(final Position position, final C component) {
		badge = new AttachedBadge(position);
		getStyle().set("position", "relative");
		getStyle().set("display", "inline-block");

		this.component = Objects.requireNonNull(component);
		add(component);

		final BadgeController badgeController = new BadgeController(this);
		badgeController.hide();
		add(badge);
		component.onBadged(badgeController);
	}

	public C getComponent() {
		return component;
	}

	public AttachedBadge getBadge() {
		return badge;
	}
}
