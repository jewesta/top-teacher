package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;

class FullscreenButtonTests {

	@Test
	void configuresTargetAndTogglesButtonState() {
		final VerticalLayout target = new VerticalLayout();
		final FullscreenButton button = new FullscreenButton(target);

		assertThat(target.getClassNames()).contains("tt-fullscreen-target");
		assertThat(button.getElement().getAttribute("aria-label")).isEqualTo("Vollbild");

		button.setFullscreenActive(true);

		assertThat(button.getElement().getAttribute("aria-label")).isEqualTo("Vollbild verlassen");

		button.setFullscreenActive(false);

		assertThat(button.getElement().getAttribute("aria-label")).isEqualTo("Vollbild");
	}
}
