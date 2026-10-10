package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

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
	void createsNewButtonWithPlusIcon() {
		final Button button = Buttons.newItem();

		assertThat(button.getText()).isEqualTo("Neu");
		assertThat(icon(button)).isEqualTo("vaadin:plus");
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
		final Button opener = Buttons.resetOpener(event -> {
		});
		final Button confirmationOpener = Buttons.resetConfirmationOpener();
		final Button reset = Buttons.reset();
		final Button customReset = Buttons.reset("Verwerfen", event -> {
		});

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

	@Test
	void dialogOpenersKeepOneEllipsisInLabelsTooltipsAndAccessibleNames() {
		final Button labeled = Buttons.dialogOpener("Verwerfen", VaadinIcon.ROTATE_LEFT, event -> { });
		final Button alreadyMarked = Buttons.dialogOpener("Aus Kurs...", VaadinIcon.COPY);
		final Button unicodeMarked = Buttons.dialogOpener("Aus Kurs…", VaadinIcon.COPY);
		final Button iconOnly = Buttons.dialogIcon("Ergebnisse löschen", VaadinIcon.TRASH, event -> { });
		final Button delete = Buttons.deleteIcon("Löschen", "Löschen?", () -> { });

		assertThat(labeled.getText()).isEqualTo("Verwerfen...");
		assertThat(alreadyMarked.getText()).isEqualTo("Aus Kurs...");
		assertThat(unicodeMarked.getText()).isEqualTo("Aus Kurs…");
		assertThat(iconOnly.getElement().getAttribute("aria-label")).isEqualTo("Ergebnisse löschen...");
		assertThat(iconOnly.getTooltip().getText()).isEqualTo("Ergebnisse löschen...");
		assertThat(delete.getTooltip().getText()).isEqualTo("Löschen...");

		new ActionToolbar(labeled, alreadyMarked, unicodeMarked, iconOnly, delete);

		assertThat(labeled.getText()).isEqualTo("Verwerfen...");
		assertThat(labeled.getTooltip().getText()).isEqualTo("Verwerfen...");
		assertThat(labeled.getElement().getAttribute("aria-label")).isEqualTo("Verwerfen...");
		assertThat(alreadyMarked.getTooltip().getText()).isEqualTo("Aus Kurs...");
		assertThat(unicodeMarked.getTooltip().getText()).isEqualTo("Aus Kurs…");
	}

	@Test
	void updatesAdaptiveLabelsWithoutAddingTextToStandaloneIconControls() {
		final Button action = Buttons.icon("Ausklappen", VaadinIcon.ANGLE_DOWN, event -> { });
		final Button inline = Buttons.icon("Ausklappen", VaadinIcon.ANGLE_DOWN, event -> { });
		new ActionToolbar(action);

		Buttons.setIconLabel(action, "Einklappen");
		Buttons.setIconLabel(inline, "Einklappen");

		assertThat(action.getText()).isEqualTo("Einklappen");
		assertThat(action.getTooltip().getText()).isEqualTo("Einklappen");
		assertThat(action.getAriaLabel()).contains("Einklappen");
		assertThat(inline.getText()).isEmpty();
		assertThat(inline.getTooltip().getText()).isEqualTo("Einklappen");
	}

	@Test
	void directActionsAndFinalConfirmationButtonsDoNotUseEllipses() {
		assertThat(Buttons.newItem().getText()).isEqualTo("Neu");
		assertThat(Buttons.save().getText()).isEqualTo("Speichern");
		assertThat(Buttons.createOrSave().getText()).isEqualTo("Anlegen");
		assertThat(Buttons.duplicate(event -> { }).getText()).isEqualTo("Duplizieren");
		assertThat(Buttons.reset().getText()).isEqualTo("Zurücksetzen");
	}

	private static String icon(final Button button) {
		return button.getIcon().getElement().getAttribute("icon");
	}
}
