package de.topteacher.ui.component;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class EhRequirementPointBadge extends Span implements EhRefreshable {

	private final Supplier<String> pointsLabelSupplier;

	EhRequirementPointBadge(final Supplier<String> pointsLabelSupplier) {
		this.pointsLabelSupplier = pointsLabelSupplier;
		addClassName("tt-eh-points");
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		setText(pointsLabelSupplier.get());
	}
}
