package de.westarps.topteacher.ui.component.loe;

import java.util.function.IntConsumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import de.westarps.topteacher.model.loe.LoePointUnits;

final class LoePointStepper extends HorizontalLayout {

	private final Button decrease = new Button("−");
	private final Button increase = new Button("+");
	private final Span value = new Span();
	private int pointUnits;
	private int maximumPointUnits;
	private IntConsumer changeHandler = ignored -> {
	};

	LoePointStepper(final String label) {
		addClassName("tt-results-point-stepper");
		setPadding(false);
		setSpacing(false);
		setAlignItems(Alignment.CENTER);
		decrease.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		increase.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		decrease.setAriaLabel(label + " verringern");
		increase.setAriaLabel(label + " erhöhen");
		value.addClassName("tt-results-point-stepper-value");
		value.getElement().setAttribute("aria-label", label);
		decrease.addClickListener(event -> changeHandler.accept(pointUnits - 1));
		increase.addClickListener(event -> changeHandler.accept(pointUnits + 1));
		add(decrease, value, increase);
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
		value.setText(LoePointUnits.formatGerman(pointUnits));
		decrease.setEnabled(pointUnits > 0);
		increase.setEnabled(pointUnits < maximumPointUnits);
	}
}
