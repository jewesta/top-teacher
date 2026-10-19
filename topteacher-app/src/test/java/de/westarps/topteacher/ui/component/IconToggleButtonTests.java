package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;

import de.westarps.topteacher.ui.component.IconToggleButton.State;

class IconToggleButtonTests {

	@ParameterizedTest
	@CsvSource({ "true,true", "true,false", "false,true", "false,false" })
	void bothIconKindsAndInitialModesShareLabelStateAndUnhighlightedToolbarStyling(
			final boolean svg, final boolean initialValue) {
		final Component selectedIcon = svg ? new SvgIcon("/icons/page-break.svg") : VaadinIcon.EYE.create();
		final Component unselectedIcon = svg ? new SvgIcon("/icons/page-flow.svg") : VaadinIcon.EYE_SLASH.create();
		final List<Boolean> changes = new ArrayList<>();
		final IconToggleButton button = new IconToggleButton(initialValue,
				new State(selectedIcon, "Ausschalten"), new State(unselectedIcon, "Einschalten"), changes::add);
		final ActionToolbar toolbar = new ActionToolbar(button, Buttons.save());
		assertThat(changes).isEmpty();

		for (int click = 0; click < 4; click++) {
			final boolean value = click % 2 == 0 ? initialValue : !initialValue;
			final String label = value ? "Ausschalten" : "Einschalten";
			assertThat(button.getValue()).isEqualTo(value);
			assertThat(button.getElement().getAttribute("aria-pressed")).isEqualTo(Boolean.toString(value));
			assertThat(button.getIcon()).isSameAs(value ? selectedIcon : unselectedIcon);
			assertThat(button.getText()).isEqualTo(label);
			assertThat(button.getAriaLabel()).contains(label);
			assertThat(button.getTooltip().getText()).isEqualTo(label);
			assertThat(button.getThemeNames()).contains("icon", "tertiary").doesNotContain("primary");
			assertThat(button.getStyle().get("background-color")).isNull();
			assertThat(button.getStyle().get("background")).isNull();
			assertThat(button.getParent()).contains(toolbar);
			button.click();
		}
		assertThat(changes).containsExactly(!initialValue, initialValue, !initialValue, initialValue);
	}

	@Test
	void unchangedValuesAndDisabledClicksDoNotNotifyOrChangeState() {
		final List<Boolean> changes = new ArrayList<>();
		final IconToggleButton button = new IconToggleButton(false,
				new State(VaadinIcon.EYE.create(), "Ausblenden"),
				new State(VaadinIcon.EYE_SLASH.create(), "Anzeigen"), changes::add);
		button.setValue(false);
		button.setEnabled(false);
		button.click();
		assertThat(button.getValue()).isFalse();
		assertThat(changes).isEmpty();
		button.setEnabled(true);
		button.click();
		assertThat(button.getValue()).isTrue();
		assertThat(changes).containsExactly(true);
	}
}
