package de.westarps.topteacher.ui.component.loe;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

class LoeNumberStepper extends ResultsPointsCell {

	private final Button decrease = new Button("−");
	private final Button increase = new Button("+");
	private final int step;
	private final IntFunction<String> formatter;
	private int value;
	private int maximumValue = Integer.MAX_VALUE;
	private boolean readOnly;
	private IntConsumer changeHandler = ignored -> {
	};

	LoeNumberStepper(final String label, final int step, final IntFunction<String> formatter) {
		if (step <= 0) {
			throw new IllegalArgumentException("step must be greater than zero");
		}
		this.step = step;
		this.formatter = formatter;
		addClassName("tt-loe-point-stepper");
		decrease.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		increase.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		decrease.setAriaLabel(label + " verringern");
		increase.setAriaLabel(label + " erhöhen");
		value().getElement().setAttribute("aria-label", label);
		decrease.addClickListener(event -> changeHandler.accept(value - step));
		increase.addClickListener(event -> changeHandler.accept(value + step));
		addLeading(decrease);
		addTrailing(increase);
		refresh();
	}

	void setChangeHandler(final IntConsumer changeHandler) {
		this.changeHandler = changeHandler == null ? ignored -> {
		} : changeHandler;
	}

	void setValue(final int value) {
		this.value = value;
		refresh();
	}

	void setMaximumValue(final int maximumValue) {
		this.maximumValue = maximumValue;
		refresh();
	}

	void setReadOnly(final boolean readOnly) {
		this.readOnly = readOnly;
		refresh();
	}

	int getValue() {
		return value;
	}

	int getMaximumValue() {
		return maximumValue;
	}

	private void refresh() {
		setValueText(formatter.apply(value));
		decrease.setEnabled(!readOnly && value >= step);
		increase.setEnabled(!readOnly && value <= maximumValue - step);
	}
}
