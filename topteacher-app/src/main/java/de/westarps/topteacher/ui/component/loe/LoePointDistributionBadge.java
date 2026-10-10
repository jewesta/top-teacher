package de.westarps.topteacher.ui.component.loe;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class LoePointDistributionBadge extends LoeBadge {

	private final int taskMaxPoints;
	private final Supplier<LoePoints> distributedPointsSupplier;

	LoePointDistributionBadge(final int taskMaxPoints, final Supplier<LoePoints> distributedPointsSupplier) {
		super("tt-loe-points-cell");
		getContent().addClassNames("tt-loe-aggregate-points", "tt-loe-distribution-points");
		this.taskMaxPoints = taskMaxPoints;
		this.distributedPointsSupplier = distributedPointsSupplier;
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		final LoePoints distributed = distributedPointsSupplier.get();

		final Span caption = text("Verteilt: ");
		caption.addClassName("tt-loe-aggregate-points-label");
		final Span values = new Span();
		values.addClassName("tt-loe-aggregate-points-values");
		values.add(number(distributed.regular(), "tt-loe-distribution-regular"), text(" / "),
				number(taskMaxPoints, "tt-loe-distribution-regular"), text(" (+"),
				number(distributed.bonus(), "tt-loe-distribution-bonus"), text(")"));
		final String accessibleText = "Verteilt: " + distributed.regular() + " / " + taskMaxPoints + " (+"
				+ distributed.bonus() + ")";
		setBadgeComponents(accessibleText, caption, values);
	}

	private static Span text(final String text) {
		return new Span(text);
	}

	private static Span number(final int value, final String widthClassName) {
		final Span number = new Span(String.valueOf(value));
		number.addClassNames("tt-loe-point-number", widthClassName);
		return number;
	}
}

