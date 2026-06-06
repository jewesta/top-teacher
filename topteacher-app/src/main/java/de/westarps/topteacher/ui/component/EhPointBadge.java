package de.westarps.topteacher.ui.component;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class EhPointBadge extends EhBadge {

	private final String label;
	private final Supplier<EhPoints> pointsSupplier;

	EhPointBadge(final String label, final Supplier<EhPoints> pointsSupplier) {
		super("tt-eh-points");
		this.label = label;
		this.pointsSupplier = pointsSupplier;
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		setPointBadge(pointsSupplier.get());
	}

	private void setPointBadge(final EhPoints points) {
		setBadgeComponents(label + ": " + points.regular() + " (+" + points.bonus() + ")", text(label + ": "),
				number(String.valueOf(points.regular()), "tt-eh-point-regular"), text("\u00a0"), text("(+"),
				number(String.valueOf(points.bonus()), "tt-eh-point-bonus"), text(")"));
		getContent().addClassName("tt-eh-point-badge");
	}

	private static Span text(final String text) {
		return new Span(text);
	}

	private static Span number(final String text, final String widthClassName) {
		final Span span = new Span(text);
		span.addClassNames("tt-eh-point-number", widthClassName);
		return span;
	}
}
