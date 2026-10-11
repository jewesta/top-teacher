package de.westarps.topteacher.ui.component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;

/**
 * Single-selection field for a small set of mutually exclusive options.
 * Options are rendered as adjacent segments in insertion order.
 */
public class SegmentedControl<T> extends CustomField<T> {

	private static final String FIELD_CLASS = "tt-segmented-control-field";
	private static final String CONTROL_CLASS = "tt-segmented-control";
	private static final String INACTIVE_CLASS = "tt-segmented-control-inactive";
	private static final String BUTTON_CLASS = "tt-segmented-control-button";
	private static final String SELECTED_BUTTON_CLASS = "tt-segmented-control-button-selected";

	private final Div content = new Div();
	private final Map<T, Button> buttonsByValue = new LinkedHashMap<>();
	private T selectedValue;

	public SegmentedControl(final String label, final Collection<T> values,
			final Function<T, String> labelGenerator) {
		this(label);
		Objects.requireNonNull(values, "values must not be null");
		Objects.requireNonNull(labelGenerator, "labelGenerator must not be null");
		values.forEach(value -> {
			final String optionLabel = labelGenerator.apply(value);
			addOption(value, new Button(optionLabel), optionLabel);
		});
	}

	public static <T> SegmentedControl<T> withComponents(final String label, final Collection<T> values,
			final Function<T, Component> componentGenerator, final Function<T, String> labelGenerator) {
		final SegmentedControl<T> control = new SegmentedControl<>(label);
		Objects.requireNonNull(values, "values must not be null");
		Objects.requireNonNull(componentGenerator, "componentGenerator must not be null");
		Objects.requireNonNull(labelGenerator, "labelGenerator must not be null");
		values.forEach(value -> control.addOption(value, new Button(componentGenerator.apply(value)),
				labelGenerator.apply(value)));
		return control;
	}

	private SegmentedControl(final String label) {
		if (label != null && !label.isBlank()) {
			setLabel(label);
		}
		addClassName(FIELD_CLASS);
		getElement().getThemeList().add("whitespace");
		content.addClassName(CONTROL_CLASS);
		add(content);
	}

	@Override
	public void setReadOnly(final boolean readOnly) {
		super.setReadOnly(readOnly);
		updateButtonAvailability();
	}

	@Override
	public void setEnabled(final boolean enabled) {
		super.setEnabled(enabled);
		updateButtonAvailability();
	}

	@Override
	protected T generateModelValue() {
		return selectedValue;
	}

	@Override
	protected void setPresentationValue(final T newValue) {
		selectedValue = newValue;
		buttonsByValue.forEach((value, button) -> {
			final boolean selected = Objects.equals(value, newValue);
			button.setClassName(SELECTED_BUTTON_CLASS, selected);
			button.getElement().setAttribute("aria-pressed", String.valueOf(selected));
		});
	}

	private void addOption(final T value, final Button button, final String label) {
		button.addClickListener(event -> {
			setPresentationValue(value);
			updateValue();
		});
		button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		button.addClassName(BUTTON_CLASS);
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.getElement().setAttribute("aria-pressed", "false");
		buttonsByValue.put(value, button);
		content.add(button);
	}

	private void updateButtonAvailability() {
		final boolean interactive = isEnabled() && !isReadOnly();
		buttonsByValue.values().forEach(button -> button.setEnabled(interactive));
		content.setClassName(INACTIVE_CLASS, !interactive);
	}
}
