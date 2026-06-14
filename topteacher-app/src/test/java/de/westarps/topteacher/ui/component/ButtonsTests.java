package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

class ButtonsTests {

	@Test
	void createsSaveButtonWithCheckIconAndPrimaryTheme() {
		final Button button = Buttons.save();

		assertThat(button.getText()).isEqualTo("Speichern");
		assertThat(icon(button)).isEqualTo("vaadin:check");
		assertThat(button.getThemeNames()).contains(ButtonVariant.LUMO_PRIMARY.getVariantName());
	}

	@Test
	void updatesCreateOrSaveButtonTextAndIconTogether() {
		final Button button = Buttons.createOrSave();

		assertThat(button.getText()).isEqualTo("Anlegen");
		assertThat(icon(button)).isEqualTo("vaadin:plus");

		Buttons.setCreateOrSaveMode(button, true);

		assertThat(button.getText()).isEqualTo("Speichern");
		assertThat(icon(button)).isEqualTo("vaadin:check");
	}

	@Test
	void createsNewButtonWithStarIcon() {
		final Button button = Buttons.newItem();

		assertThat(button.getText()).isEqualTo("Neu");
		assertThat(icon(button)).isEqualTo("vaadin:star");
	}

	@Test
	void createsArchiveButtonAsSecondaryConfirmationOpenerWithArchiveIcon() {
		final Button button = Buttons.archive();

		assertThat(button.getText()).isEqualTo("Archivieren...");
		assertThat(icon(button)).isEqualTo("vaadin:archive");
		assertThat(button.getThemeNames()).doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName(),
				ButtonVariant.LUMO_ERROR.getVariantName());
	}

	@Test
	void createsDuplicateButtonsWithCopyIcon() {
		final Button opener = Buttons.duplicateOpener();
		final Button duplicate = Buttons.duplicate(event -> {
		});

		assertThat(opener.getText()).isEqualTo("Duplizieren...");
		assertThat(icon(opener)).isEqualTo("vaadin:copy");
		assertThat(duplicate.getText()).isEqualTo("Duplizieren");
		assertThat(icon(duplicate)).isEqualTo("vaadin:copy");
		assertThat(duplicate.getThemeNames()).contains(ButtonVariant.LUMO_PRIMARY.getVariantName());
	}

	@Test
	void createsResetButtonsWithResetIcon() {
		final Button opener = Buttons.resetOpener();
		final Button confirmationOpener = Buttons.resetConfirmationOpener();
		final Button reset = Buttons.reset();
		final Button customReset = Buttons.reset("Verwerfen");

		assertThat(opener.getText()).isEqualTo("Datenbank zurücksetzen...");
		assertThat(icon(opener)).isEqualTo("vaadin:rotate-left");
		assertThat(confirmationOpener.getText()).isEqualTo("Zurücksetzen...");
		assertThat(icon(confirmationOpener)).isEqualTo("vaadin:rotate-left");
		assertThat(confirmationOpener.getThemeNames()).contains(ButtonVariant.LUMO_PRIMARY.getVariantName(),
				ButtonVariant.LUMO_ERROR.getVariantName());
		assertThat(reset.getText()).isEqualTo("Zurücksetzen");
		assertThat(icon(reset)).isEqualTo("vaadin:rotate-left");
		assertThat(reset.getThemeNames()).contains(ButtonVariant.LUMO_PRIMARY.getVariantName(),
				ButtonVariant.LUMO_ERROR.getVariantName());
		assertThat(customReset.getText()).isEqualTo("Verwerfen");
		assertThat(icon(customReset)).isEqualTo("vaadin:rotate-left");
		assertThat(customReset.getThemeNames()).doesNotContain(ButtonVariant.LUMO_ERROR.getVariantName());
	}

	private static String icon(final Button button) {
		return button.getIcon().getElement().getAttribute("icon");
	}
}
