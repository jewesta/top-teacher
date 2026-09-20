package de.westarps.vaadin.tray;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.Span;

class TrayTests {

	@Test
	void startsClosedWithConfiguredLabel() {
		final Tray tray = new Tray("Prüfung");

		assertThat(tray.getLabel()).isEqualTo("Prüfung");
		assertThat(tray).isInstanceOf(Card.class);
		assertThat(tray.getThemeNames()).contains(CardVariant.LUMO_ELEVATED.getVariantName());
		assertThat(tray.isOpened()).isFalse();
		assertThat(tray.getElement().getAttribute("data-opened")).isEqualTo("false");
		assertThat(notch(tray).getElement().getAttribute("aria-expanded")).isEqualTo("false");
		assertThat(tray.getContentLayout().getElement().getAttribute("aria-hidden")).isEqualTo("true");
		assertThat(tray.getContentLayout().getElement().hasAttribute("inert")).isTrue();
	}

	@Test
	void opensClosesAndNotifiesOnlyAboutChanges() {
		final Tray tray = new Tray();
		final List<Boolean> changes = new ArrayList<>();
		tray.addOpenedChangeListener(event -> changes.add(event.isOpened()));

		tray.open();
		tray.open();

		assertThat(tray.isOpened()).isTrue();
		assertThat(tray.getElement().getAttribute("data-opened")).isEqualTo("true");
		assertThat(notch(tray).getElement().getAttribute("aria-expanded")).isEqualTo("true");
		assertThat(tray.getContentLayout().getElement().getAttribute("aria-hidden")).isEqualTo("false");
		assertThat(tray.getContentLayout().getElement().hasAttribute("inert")).isFalse();
		assertThat(changes).containsExactly(true);

		tray.toggle();

		assertThat(tray.isOpened()).isFalse();
		assertThat(changes).containsExactly(true, false);
	}

	@Test
	void clickingTheNotchTogglesTheTray() {
		final Tray tray = new Tray();

		notch(tray).click();

		assertThat(tray.isOpened()).isTrue();
	}

	@Test
	void managesArbitraryComponentsInTheVerticalContentLayout() {
		final Tray tray = new Tray();
		final Span first = new Span("First");
		final Span second = new Span("Second");

		tray.add(first, second);

		assertThat(tray.getContentLayout().getChildren()).containsExactly(first, second);

		tray.remove(first);

		assertThat(tray.getContentLayout().getChildren()).containsExactly(second);

		tray.removeAll();

		assertThat(tray.getContentLayout().getChildren()).isEmpty();
	}

	private static Button notch(final Tray tray) {
		return (Button) tray.getHeader();
	}
}
