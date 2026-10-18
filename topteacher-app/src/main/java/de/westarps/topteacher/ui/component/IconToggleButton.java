package de.westarps.topteacher.ui.component;

import java.io.Serializable;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.function.SerializableConsumer;

/** Icon-only state switch with shared toolbar styling and accessible action labels. */
public final class IconToggleButton extends Button {

	private final State selectedState;
	private final State unselectedState;
	private final SerializableConsumer<Boolean> valueChangeListener;
	private boolean value;

	public IconToggleButton(final boolean value, final State selectedState, final State unselectedState,
			final SerializableConsumer<Boolean> valueChangeListener) {
		this.value = value;
		this.selectedState = Objects.requireNonNull(selectedState);
		this.unselectedState = Objects.requireNonNull(unselectedState);
		this.valueChangeListener = Objects.requireNonNull(valueChangeListener);
		addClassName("tt-icon-toggle");
		addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		updatePresentation();
		addClickListener(event -> {
			if (isEnabled()) {
				setValue(!getValue());
			}
		});
	}

	public boolean getValue() {
		return value;
	}

	public void setValue(final boolean value) {
		if (this.value == value) {
			return;
		}
		this.value = value;
		updatePresentation();
		valueChangeListener.accept(value);
	}

	private void updatePresentation() {
		final State state = value ? selectedState : unselectedState;
		setIcon(state.icon());
		setText(state.actionLabel());
		/* Vaadin resets the icon theme when the icon or text changes. Keep both states centered. */
		addThemeVariants(ButtonVariant.LUMO_ICON);
		setAriaLabel(state.actionLabel());
		setTooltipText(state.actionLabel());
		getElement().setAttribute("aria-pressed", Boolean.toString(value));
	}

	public record State(Component icon, String actionLabel) implements Serializable {
		public State {
			Objects.requireNonNull(icon);
			Objects.requireNonNull(actionLabel);
		}
	}
}
