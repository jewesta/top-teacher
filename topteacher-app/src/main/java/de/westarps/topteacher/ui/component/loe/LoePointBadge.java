package de.westarps.topteacher.ui.component.loe;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class LoePointBadge extends LoeBadge {

	private final String label;
	private final Supplier<LoePoints> pointsSupplier;

	LoePointBadge(final String label, final Supplier<LoePoints> pointsSupplier) {
		super("tt-loe-points-cell");
		getContent().addClassName("tt-loe-aggregate-points");
		this.label = label;
		this.pointsSupplier = pointsSupplier;
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		setPointBadge(pointsSupplier.get());
	}

	private void setPointBadge(final LoePoints points) {
		final Span caption = text(label + ": ");
		caption.addClassName("tt-loe-aggregate-points-label");
		final Span values = new Span();
		values.addClassName("tt-loe-aggregate-points-values");
		values.add(number(String.valueOf(points.regular()), "tt-loe-point-regular"), text("\u00a0"), text("(+"),
				number(String.valueOf(points.bonus()), "tt-loe-point-bonus"), text(")"));
		setBadgeComponents(label + ": " + points.regular() + " (+" + points.bonus() + ")", caption, values);
	}

	private static Span text(final String text) {
		return new Span(text);
	}

	private static Span number(final String text, final String widthClassName) {
		final Span span = new Span(text);
		span.addClassNames("tt-loe-point-number", widthClassName);
		return span;
	}
}
