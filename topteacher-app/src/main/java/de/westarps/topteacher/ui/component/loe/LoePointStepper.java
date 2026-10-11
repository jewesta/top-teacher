package de.westarps.topteacher.ui.component.loe;

import de.westarps.topteacher.model.loe.LoePointUnits;

class LoePointStepper extends LoeNumberStepper {

	LoePointStepper(final String label) {
		super(label, 1, LoePointUnits::formatGerman);
		addClassName("tt-results-point-stepper");
		setMaximumValue(0);
	}

	void setPointUnits(final int pointUnits) {
		setValue(pointUnits);
	}

	void setMaximumPointUnits(final int maximumPointUnits) {
		setMaximumValue(maximumPointUnits);
	}

	int getPointUnits() {
		return getValue();
	}

	int getMaximumPointUnits() {
		return getMaximumValue();
	}
}
