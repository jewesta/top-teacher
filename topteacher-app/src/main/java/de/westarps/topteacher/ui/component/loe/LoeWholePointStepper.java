package de.westarps.topteacher.ui.component.loe;

final class LoeWholePointStepper extends LoeNumberStepper {

	LoeWholePointStepper(final String label) {
		super(label, 1, String::valueOf);
		addClassName("tt-loe-whole-point-stepper");
	}
}
