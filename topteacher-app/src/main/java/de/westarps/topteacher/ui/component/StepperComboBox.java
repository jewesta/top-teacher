package de.westarps.topteacher.ui.component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.shared.Registration;

public class StepperComboBox<T> extends Composite<HorizontalLayout> implements HasEnabled, HasSize {

	private final Button previousButton = stepButton("Vorheriger Eintrag", VaadinIcon.ANGLE_LEFT);
	private final ComboBox<T> comboBox = new ComboBox<>();
	private final Button nextButton = stepButton("Nächster Eintrag", VaadinIcon.ANGLE_RIGHT);
	private List<T> items = List.of();

	public StepperComboBox() {
		comboBox.addClassName("tt-stepper-combo-box-field");
		comboBox.setWidthFull();
		comboBox.addValueChangeListener(event -> updateButtons());

		final HorizontalLayout layout = getContent();
		layout.addClassName("tt-stepper-combo-box");
		layout.setAlignItems(FlexComponent.Alignment.CENTER);
		layout.setFlexGrow(1, comboBox);
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setWidthFull();
		layout.add(previousButton, comboBox, nextButton);

		previousButton.addClickListener(event -> step(-1));
		nextButton.addClickListener(event -> step(1));
		updateButtons();
	}

	public void setItems(final Collection<T> items) {
		this.items = List.copyOf(items);
		comboBox.setItems(this.items);
		updateButtons();
	}

	public void setItemLabelGenerator(final ItemLabelGenerator<T> itemLabelGenerator) {
		comboBox.setItemLabelGenerator(itemLabelGenerator);
	}

	public Registration addValueChangeListener(
			final HasValue.ValueChangeListener<? super AbstractField.ComponentValueChangeEvent<ComboBox<T>, T>> listener) {
		return comboBox.addValueChangeListener(listener);
	}

	public void setValue(final T value) {
		comboBox.setValue(value);
	}

	public T getValue() {
		return comboBox.getValue();
	}

	public void setAriaLabel(final String ariaLabel) {
		comboBox.getElement().setAttribute("aria-label", ariaLabel);
	}

	@Override
	public void setEnabled(final boolean enabled) {
		HasEnabled.super.setEnabled(enabled);
		comboBox.setEnabled(enabled);
		updateButtons();
	}

	private void step(final int offset) {
		final int nextIndex = nextIndex(offset);
		if (nextIndex >= 0 && nextIndex < items.size()) {
			comboBox.setValue(items.get(nextIndex));
		}
	}

	private void updateButtons() {
		final boolean enabled = isEnabled() && !items.isEmpty();
		previousButton.setEnabled(enabled && nextIndex(-1) >= 0);
		nextButton.setEnabled(enabled && nextIndex(1) < items.size());
	}

	private int nextIndex(final int offset) {
		final int currentIndex = currentIndex();
		if (currentIndex < 0 && offset > 0 && !items.isEmpty()) {
			return 0;
		}
		return currentIndex + offset;
	}

	private int currentIndex() {
		final T value = comboBox.getValue();
		for (int index = 0; index < items.size(); index++) {
			if (Objects.equals(value, items.get(index))) {
				return index;
			}
		}
		return -1;
	}

	private static Button stepButton(final String label, final VaadinIcon icon) {
		final Button button = new Button(icon.create());
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		return button;
	}
}
