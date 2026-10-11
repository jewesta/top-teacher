package de.westarps.topteacher.ui.component.loe;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;

final class ResultsPointsRow extends Div {

	ResultsPointsRow(final Component points) {
		this(null, points);
	}

	ResultsPointsRow(final Component status, final Component points) {
		addClassName("tt-results-points-row");
		final Div statusColumn = new Div();
		statusColumn.addClassName("tt-results-points-status");
		if (status == null) {
			statusColumn.getElement().setAttribute("aria-hidden", "true");
		} else {
			statusColumn.add(status);
		}
		add(statusColumn, points);
	}
}
