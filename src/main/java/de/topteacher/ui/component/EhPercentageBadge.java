package de.topteacher.ui.component;

import java.util.function.Supplier;

final class EhPercentageBadge extends EhBadge {

	private final Supplier<Integer> percentageSupplier;

	EhPercentageBadge(final Supplier<Integer> percentageSupplier) {
		super("tt-eh-percentage");
		this.percentageSupplier = percentageSupplier;
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		setBadgeText(percentageSupplier.get() + " %");
	}
}
