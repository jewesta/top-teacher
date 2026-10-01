package de.westarps.topteacher.ui.component.loe;

final class ResultsRequirementPointCell extends ResultsPointsCell {

	ResultsRequirementPointCell() {
		addClassName("tt-results-requirement-total");
	}

	void setPoints(final int points) {
		setValueText(String.valueOf(points));
	}
}
