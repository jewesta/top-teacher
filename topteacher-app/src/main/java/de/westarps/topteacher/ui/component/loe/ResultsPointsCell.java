package de.westarps.topteacher.ui.component.loe;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

abstract class ResultsPointsCell extends Div {

	private final Div leading = new Div();
	private final Span value = new Span();
	private final Div trailing = new Div();

	protected ResultsPointsCell() {
		addClassName("tt-results-points-cell");
		leading.addClassName("tt-results-points-cell-leading");
		value.addClassName("tt-results-points-cell-value");
		trailing.addClassName("tt-results-points-cell-trailing");
		add(leading, value, trailing);
	}

	protected void addLeading(final Component component) {
		leading.add(component);
	}

	protected void addLeadingFirst(final Component component) {
		leading.addComponentAsFirst(component);
	}

	protected void addTrailing(final Component component) {
		trailing.add(component);
	}

	protected void setValueText(final String text) {
		value.setText(text);
	}

	protected Span value() {
		return value;
	}
}
