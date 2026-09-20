package de.westarps.vaadin.badge;

import java.time.Duration;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import de.westarps.vaadin.badge.Badge.BadgeVariant;

/**
 * Controls the badge belonging to one {@link BadgedComponent}.
 */
public class BadgeController {

	static final String FADE_CLASS_NAME = "ws-badge-fade";
	static final String HIDDEN_CLASS_NAME = "ws-badge-fade-hidden";

	private final BadgedComponent<?> badgedComponent;

	private Timer hideTimer;

	public BadgeController(final BadgedComponent<?> badgedComponent) {
		this.badgedComponent = Objects.requireNonNull(badgedComponent);
		badgedComponent.getBadge().addClassName(FADE_CLASS_NAME);
	}

	public Div getWrapper() {
		return badgedComponent;
	}

	public void setTooltipText(final String text) {
		badgedComponent.getBadge().setTooltipText(text);
	}

	public void show(final BadgeVariant variant, final Icon icon) {
		cancelDelayedHide();
		final AttachedBadge badge = badgedComponent.getBadge();
		badge.setVariant(Objects.requireNonNull(variant));
		badge.setIcon(Objects.requireNonNull(icon));
		badge.setText(null);
		badge.removeClassName(HIDDEN_CLASS_NAME);
	}

	public void show(final BadgeVariant variant, final VaadinIcon vaadinIcon) {
		final Icon icon = Objects.requireNonNull(vaadinIcon).create();
		icon.getStyle().set("padding", "var(--lumo-space-xs)");
		show(variant, icon);
	}

	public void show(final BadgeVariant variant, final String text) {
		cancelDelayedHide();
		final AttachedBadge badge = badgedComponent.getBadge();
		badge.setVariant(Objects.requireNonNull(variant));
		badge.setIcon(null);
		badge.setText(Objects.requireNonNull(text));
		badge.removeClassName(HIDDEN_CLASS_NAME);
	}

	public void hide() {
		cancelDelayedHide();
		final AttachedBadge badge = badgedComponent.getBadge();
		badge.setTooltipText(null);
		badge.addClassName(HIDDEN_CLASS_NAME);
	}

	public void hideDelayedBy(final Duration delay) {
		final Duration requiredDelay = Objects.requireNonNull(delay);
		if (requiredDelay.isNegative()) {
			throw new IllegalArgumentException("The badge hide delay must not be negative");
		}

		cancelDelayedHide();
		hideTimer = new Timer("westarps-vaadin-badge-hide", true);
		hideTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				badgedComponent.getUI().ifPresent(ui -> {
					if (ui.isAttached()) {
						ui.access(BadgeController.this::hide);
					}
				});
			}

		}, requiredDelay.toMillis());
	}

	private void cancelDelayedHide() {
		if (hideTimer != null) {
			hideTimer.cancel();
			hideTimer = null;
		}
	}
}
