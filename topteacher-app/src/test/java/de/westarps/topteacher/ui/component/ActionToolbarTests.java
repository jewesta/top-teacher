package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

class ActionToolbarTests {

	@Test
	void keepsAddActionsLabeledAndFirstWhileOtherActionsBecomeAccessibleIcons() {
		final TextField filter = new TextField("Filter");
		final AtomicInteger saves = new AtomicInteger();
		final Button save = Buttons.save(event -> saves.incrementAndGet());
		final Button add = Buttons.newItem();
		final ActionToolbar toolbar = new ActionToolbar(filter, save, add);

		assertThat(toolbar.getChildren()).containsExactly(add, filter, save);
		assertThat(add.getText()).isEqualTo("Neu");
		assertThat(save.getText()).isEmpty();
		assertThat(save.getElement().getAttribute("aria-label")).isEqualTo("Speichern");
		assertThat(save.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName())
				.doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());

		save.click();

		assertThat(saves).hasValue(1);
	}

	@Test
	void appliesTheSameRuleToActionsAddedLaterAndKeepsAddOrder() {
		final Button add = Buttons.newItem();
		final Button secondAdd = Buttons.createOrSave();
		final ActionToolbar toolbar = new ActionToolbar(new TextField(), add);
		final Button archive = Buttons.archive();

		toolbar.add(archive, secondAdd);

		assertThat(toolbar.getChildren().toList().subList(0, 2)).containsExactly(add, secondAdd);
		assertThat(secondAdd.getText()).isEqualTo("Anlegen");
		assertThat(secondAdd.getThemeNames()).doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());
		assertThat(archive.getText()).isEmpty();
		assertThat(archive.getElement().getAttribute("aria-label")).isEqualTo("Archivieren...");
	}

	@Test
	void updatesCreateOrSavePresentationWhenToolbarModeChanges() {
		final Button action = Buttons.createOrSave();
		new ActionToolbar(action);

		Buttons.setCreateOrSaveMode(action, true);

		assertThat(action.getText()).isEmpty();
		assertThat(action.getElement().getAttribute("aria-label")).isEqualTo("Speichern");
		assertThat(action.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName());
		assertThat(action.getThemeNames()).doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());

		Buttons.setCreateOrSaveMode(action, false);

		assertThat(action.getText()).isEqualTo("Anlegen");
		assertThat(action.getElement().getAttribute("aria-label")).isEqualTo("Anlegen");
		assertThat(action.getThemeNames()).doesNotContain(ButtonVariant.LUMO_ICON.getVariantName());
		assertThat(action.getThemeNames()).doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());
	}

	@Test
	void normalizesPrimaryAndTransparentActionsWithoutChangingButtonsOutsideTheToolbar() {
		final Button save = Buttons.save();
		final Button discard = Buttons.reset("Verwerfen", event -> { });
		final Button fullscreen = Buttons.icon("Vollbild", VaadinIcon.EXPAND, event -> { });
		fullscreen.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		final Button reload = Buttons.icon("Neu laden", VaadinIcon.REFRESH, event -> { });
		reload.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		final Button formSave = Buttons.save();

		new ActionToolbar(save, discard, fullscreen, reload);

		for (final Button action : new Button[] { save, discard, fullscreen, reload }) {
			assertThat(action.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName())
					.doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName(),
							ButtonVariant.LUMO_TERTIARY.getVariantName(),
							ButtonVariant.LUMO_TERTIARY_INLINE.getVariantName());
		}
		assertThat(fullscreen.getThemeNames()).contains(ButtonVariant.LUMO_SMALL.getVariantName());
		assertThat(formSave.getThemeNames()).contains(ButtonVariant.LUMO_PRIMARY.getVariantName());
		assertThat(formSave.getText()).isEqualTo("Speichern");
	}
}
