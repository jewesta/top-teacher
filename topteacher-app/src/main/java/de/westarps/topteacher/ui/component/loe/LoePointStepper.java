package de.westarps.topteacher.ui.component.loe;

import java.util.function.IntConsumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

import de.westarps.topteacher.model.loe.LoePointUnits;

class LoePointStepper extends ResultsPointsCell {

	private final Button decrease = new Button("−");
	private final Button increase = new Button("+");
	private int pointUnits;
	private int maximumPointUnits;
	private IntConsumer changeHandler = ignored -> {
	};

	LoePointStepper(final String label) {
		addClassName("tt-results-point-stepper");
		decrease.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		increase.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		decrease.setAriaLabel(label + " verringern");
		increase.setAriaLabel(label + " erhöhen");
		value().getElement().setAttribute("aria-label", label);
		decrease.addClickListener(event -> changeHandler.accept(pointUnits - 1));
		increase.addClickListener(event -> changeHandler.accept(pointUnits + 1));
		addLeading(decrease);
		addTrailing(increase);
		refresh();
	}

	void setChangeHandler(final IntConsumer changeHandler) {
		this.changeHandler = changeHandler == null ? ignored -> {
		} : changeHandler;
	}

	void setPointUnits(final int pointUnits) {
		this.pointUnits = pointUnits;
		refresh();
	}

	void setMaximumPointUnits(final int maximumPointUnits) {
		this.maximumPointUnits = maximumPointUnits;
		refresh();
	}

	int getPointUnits() {
		return pointUnits;
	}

	int getMaximumPointUnits() {
		return maximumPointUnits;
	}

	private void refresh() {
		setValueText(LoePointUnits.formatGerman(pointUnits));
		decrease.setEnabled(pointUnits > 0);
		increase.setEnabled(pointUnits < maximumPointUnits);
	}
}
