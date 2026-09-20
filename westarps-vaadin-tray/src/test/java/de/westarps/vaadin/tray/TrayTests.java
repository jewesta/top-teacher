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
	void startsPeekingWithConfiguredLabel() {
		final Tray tray = new Tray("Prüfung");

		assertThat(tray.getLabel()).isEqualTo("Prüfung");
		assertThat(tray).isInstanceOf(Card.class);
		assertThat(tray.getThemeNames()).contains(CardVariant.LUMO_ELEVATED.getVariantName());
		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getElement().getAttribute("data-state")).isEqualTo("peek");
		assertThat(notch(tray).getElement().getAttribute("aria-expanded")).isEqualTo("false");
		assertThat(tray.getContentLayout().getElement().getAttribute("aria-hidden")).isEqualTo("true");
		assertThat(tray.getContentLayout().getElement().hasAttribute("inert")).isTrue();
	}

	@Test
	void changesStateProgrammaticallyAndNotifiesOnlyAboutChanges() {
		final Tray tray = new Tray();
		final List<TrayState> changes = new ArrayList<>();
		tray.addStateChangeListener(event -> changes.add(event.getState()));

		tray.show();
		tray.show();

		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);
		assertThat(tray.getElement().getAttribute("data-state")).isEqualTo("show");
		assertThat(notch(tray).getElement().getAttribute("aria-expanded")).isEqualTo("true");
		assertThat(tray.getContentLayout().getElement().getAttribute("aria-hidden")).isEqualTo("false");
		assertThat(tray.getContentLayout().getElement().hasAttribute("inert")).isFalse();
		assertThat(changes).containsExactly(TrayState.SHOW);

		tray.peek();

		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(changes).containsExactly(TrayState.SHOW, TrayState.PEEK);

		tray.hide();

		assertThat(tray.getState()).isEqualTo(TrayState.HIDE);
		assertThat(tray.isVisible()).isFalse();
		assertThat(changes).containsExactly(TrayState.SHOW, TrayState.PEEK, TrayState.HIDE);
	}

	@Test
	void clickingTheNotchTogglesBetweenPeekAndShow() {
		final Tray tray = new Tray();

		notch(tray).click();

		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);

		notch(tray).click();

		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
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
