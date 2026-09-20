package de.westarps.vaadin.tray;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.Span;

class TrayTests {

	@Test
	void startsPeekingWithConfiguredLabel() {
		final StringTray tray = new StringTray("Prüfung");

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
		final StringTray tray = new StringTray();
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
		final StringTray tray = new StringTray();

		notch(tray).click();

		assertThat(tray.getState()).isEqualTo(TrayState.SHOW);

		notch(tray).click();

		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
	}

	@Test
	void outsideClickReturnsOnlyAShownTrayToPeek() {
		final StringTray tray = new StringTray();
		final List<Boolean> fromClient = new ArrayList<>();
		tray.addStateChangeListener(event -> fromClient.add(event.isFromClient()));

		tray.show();
		tray.handleOutsideClick();
		tray.handleOutsideClick();

		assertThat(tray.getState()).isEqualTo(TrayState.PEEK);
		assertThat(fromClient).containsExactly(false, true);
	}

	@Test
	void replacesItemsInTheirSuppliedOrder() {
		final StringTray tray = new StringTray();

		tray.setItems(List.of("First", "Second"));

		assertThat(tray.getItems()).containsExactly("First", "Second");
		assertThat(tray.renderedText()).containsExactly("First", "Second");
		assertThat(tray.changes).containsExactly(List.of("First", "Second"));
	}

	@Test
	void addsIndividualItemsAndBatchesAtTheTopWithoutReversingThem() {
		final StringTray tray = new StringTray();
		tray.setItems(List.of("Existing"));

		tray.addItem("Newest");
		tray.addItems(List.of("Batch first", "Batch second"));

		assertThat(tray.getItems()).containsExactly("Batch first", "Batch second", "Newest", "Existing");
		assertThat(tray.renderedText()).containsExactly("Batch first", "Batch second", "Newest", "Existing");
	}

	@Test
	void removesTheFirstMatchingItemAndCanClearTheList() {
		final StringTray tray = new StringTray();
		tray.setItems(List.of("Duplicate", "Middle", "Duplicate"));

		assertThat(tray.removeItem("Duplicate")).isTrue();
		assertThat(tray.removeItem("Missing")).isFalse();
		assertThat(tray.getItems()).containsExactly("Middle", "Duplicate");
		assertThat(tray.renderedText()).containsExactly("Middle", "Duplicate");

		tray.clearItems();

		assertThat(tray.getItems()).isEmpty();
		assertThat(tray.renderedText()).isEmpty();
	}

	@Test
	void ignoresAnUnchangedReplacement() {
		final StringTray tray = new StringTray();
		tray.setItems(List.of("Unchanged"));
		final int renderedItems = tray.renderedItems;
		final int changes = tray.changes.size();

		tray.setItems(List.of("Unchanged"));

		assertThat(tray.renderedItems).isEqualTo(renderedItems);
		assertThat(tray.changes).hasSize(changes);
	}

	private static Button notch(final Tray<?> tray) {
		return (Button) tray.getHeader();
	}

	private static final class StringTray extends Tray<String> {

		private final List<List<String>> changes = new ArrayList<>();
		private int renderedItems;

		private StringTray() {
			this("");
		}

		private StringTray(final String label) {
			super(label);
		}

		@Override
		protected Component renderItem(final String item) {
			renderedItems++;
			return new Span(item);
		}

		@Override
		protected void onItemsChanged(final List<String> previousItems, final List<String> currentItems) {
			changes.add(currentItems);
		}

		private List<String> renderedText() {
			return getContentLayout().getChildren().map(Component::getElement).map(element -> element.getText()).toList();
		}
	}
}
