package de.topteacher.ui.component;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class EhPercentageBadge extends Span implements EhRefreshable {

	private final Supplier<Integer> percentageSupplier;

	EhPercentageBadge(final Supplier<Integer> percentageSupplier) {
		this.percentageSupplier = percentageSupplier;
		addClassName("tt-eh-percentage");
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		setText(percentageSupplier.get() + " %");
	}
}
