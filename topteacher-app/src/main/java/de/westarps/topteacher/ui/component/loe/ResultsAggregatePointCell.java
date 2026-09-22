package de.westarps.topteacher.ui.component.loe;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class ResultsAggregatePointCell extends ResultsPointsCell {

	private final String label;
	private final Supplier<LoePoints> pointsSupplier;
	private String text;

	ResultsAggregatePointCell(final String label, final String symbol, final Supplier<LoePoints> pointsSupplier) {
		this.label = label;
		this.pointsSupplier = pointsSupplier;
		addClassName("tt-results-aggregate-points");
		final Span caption = new Span(symbol);
		caption.getElement().setAttribute("aria-hidden", "true");
		addLeading(caption);
		refresh();
	}

	void refresh() {
		final LoePoints points = pointsSupplier.get();
		text = label + ": " + points.regular() + " (+" + points.bonus() + ")";
		setValueText(points.regular() + " (+ " + points.bonus() + ")");
		getElement().setAttribute("aria-label", text);
	}

	String getBadgeText() {
		return text;
	}
}
