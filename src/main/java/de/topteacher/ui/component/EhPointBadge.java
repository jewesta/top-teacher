package de.topteacher.ui.component;

import java.util.function.Supplier;

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
		setBadgeText(label + ": " + pointsSupplier.get().label());
	}
}
