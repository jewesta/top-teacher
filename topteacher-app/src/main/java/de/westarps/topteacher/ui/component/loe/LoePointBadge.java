package de.westarps.topteacher.ui.component.loe;

import java.util.function.Supplier;

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
		setBadgeText(String.valueOf(points.total()));
		final String description = label + ": " + points.total() + " Punkte";
		getContent().getElement().setAttribute("aria-label", description);
		getContent().getElement().setAttribute("title", description);
	}
}
