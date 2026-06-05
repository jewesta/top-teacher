package de.topteacher.ui.component;

import java.util.function.Supplier;

final class EhRequirementPointBadge extends EhBadge {

	private final Supplier<String> pointsLabelSupplier;

	EhRequirementPointBadge(final Supplier<String> pointsLabelSupplier) {
		super("tt-eh-points");
		this.pointsLabelSupplier = pointsLabelSupplier;
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		setBadgeText(pointsLabelSupplier.get());
	}
}
