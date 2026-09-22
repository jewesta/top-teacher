package de.westarps.topteacher.ui.component.loe;

import com.vaadin.flow.component.checkbox.Checkbox;

final class LoeCriterionPointStepper extends LoePointStepper {

	LoeCriterionPointStepper(final String label, final Checkbox checkbox) {
		super(label);
		addClassName("tt-results-criterion-checkbox-row");
		addLeadingFirst(checkbox);
	}
}
