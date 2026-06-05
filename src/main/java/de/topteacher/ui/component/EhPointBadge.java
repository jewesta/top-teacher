package de.topteacher.ui.component;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class EhPointBadge extends Span implements EhRefreshable {

	private final String label;
	private final Supplier<EhPoints> pointsSupplier;

	EhPointBadge(final String label, final Supplier<EhPoints> pointsSupplier) {
		this.label = label;
		this.pointsSupplier = pointsSupplier;
		addClassName("tt-eh-points");
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		setText(label + ": " + pointsSupplier.get().label());
	}
}
