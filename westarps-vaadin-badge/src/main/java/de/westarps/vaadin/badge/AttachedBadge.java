package de.westarps.vaadin.badge;

import java.util.Objects;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.shared.Tooltip;

import de.westarps.vaadin.badge.Badge.BadgeVariant;

/**
 * Positions a badge so that it protrudes from its containing component.
 */
@SuppressWarnings("serial")
public class AttachedBadge extends Div {

	public enum Position {
		TOP_RIGHT,
		RIGHT
	}

	private static final String MOVE_OUT_BY = "-0.5em";

	private final Badge badge = new Badge();
	private final Tooltip tooltip = Tooltip.forComponent(this);

	public AttachedBadge(final Position position) {
		badge.setVariant(BadgeVariant.NORMAL);
		badge.setPill(true);
		badge.setPrimary(true);
		badge.setSmall(true);
		badge.setPadding(true);

		switch (Objects.requireNonNull(position)) {
		case RIGHT:
			getStyle().set("position", "absolute").set("top", "50%").set("right", MOVE_OUT_BY)
					.set("transform", "translateY(-50%)");
			break;
		case TOP_RIGHT:
			getStyle().set("position", "absolute").setTop(MOVE_OUT_BY).setRight(MOVE_OUT_BY);
			break;
		}
		add(badge);
	}

	public AttachedBadge(final Position position, final String text) {
		this(position);
		badge.setText(text);
	}

	@Override
	public void setText(final String text) {
		badge.setText(text);
	}

	public void setTooltipText(final String text) {
		tooltip.setText(text);
	}

	public void setVariant(final BadgeVariant variant) {
		badge.setVariant(variant);
	}

	public void setIcon(final Icon icon) {
		badge.setIcon(icon);
	}

	public void setIcon(final Icon icon, final boolean first) {
		badge.setIcon(icon, first);
	}

	Badge getBadgeComponent() {
		return badge;
	}
}
