package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;

class ActionToolbarTests {

	@Test
	void ownsSharedSpacingWithoutVaadinDefaultSpacingForFormsOrFilters() {
		final ActionToolbar formActions = new ActionToolbar(Buttons.save(), Buttons.duplicateOpener());
		final ActionToolbar filteredActions = new ActionToolbar(new QuickFilterField(), Buttons.newItem());

		for (final ActionToolbar toolbar : new ActionToolbar[] { formActions, filteredActions }) {
			assertThat(toolbar.getClassNames()).contains("tt-action-toolbar");
			assertThat(toolbar.getElement().getThemeList()).doesNotContain("spacing");
		}
	}

	@Test
	void keepsAddActionsLabeledAndFirstWhilePreparingOtherActionsForAdaptivePresentation() {
		final TextField filter = new TextField("Filter");
		final AtomicInteger saves = new AtomicInteger();
		final Button save = Buttons.save(event -> saves.incrementAndGet());
		final Button add = Buttons.add("Klausurteil hinzufügen", event -> { });
		final ActionToolbar toolbar = new ActionToolbar(filter, save, add);

		assertThat(toolbar.getChildren()).containsExactly(add, filter, save);
		assertThat(add.getText()).isEqualTo("Klausurteil hinzufügen");
		assertThat(save.getText()).isEqualTo("Speichern");
		assertThat(save.getElement().getAttribute("aria-label")).isEqualTo("Speichern");
		assertThat(save.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName())
				.doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());

		save.click();

		assertThat(saves).hasValue(1);
	}

	@Test
	void appliesTheSameRuleToActionsAddedLaterAndKeepsAddOrder() {
		final Button add = Buttons.add("Klausurteil hinzufügen", event -> { });
		final Button secondAdd = Buttons.add("Material hinzufügen", event -> { });
		final ActionToolbar toolbar = new ActionToolbar(new TextField(), add);
		final Button archive = Buttons.archive();

		toolbar.add(archive, secondAdd);

		assertThat(toolbar.getChildren().toList().subList(0, 2)).containsExactly(add, secondAdd);
		assertThat(secondAdd.getText()).isEqualTo("Material hinzufügen");
		assertThat(secondAdd.getThemeNames()).doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());
		assertThat(archive.getText()).isEqualTo("Archivieren...");
		assertThat(archive.getElement().getAttribute("aria-label")).isEqualTo("Archivieren...");
	}

	@Test
	void updatesCreateOrSavePresentationWhenToolbarModeChanges() {
		final Button action = Buttons.createOrSave();
		new ActionToolbar(action);
		assertThat(action.getText()).isEqualTo("Anlegen");
		assertThat(action.getElement().getAttribute("aria-label")).isEqualTo("Anlegen");
		assertThat(action.getTooltip().getText()).isEqualTo("Anlegen");

		Buttons.setCreateOrSaveMode(action, true);

		assertThat(action.getText()).isEqualTo("Speichern");
		assertThat(action.getElement().getAttribute("aria-label")).isEqualTo("Speichern");
		assertThat(action.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName());
		assertThat(action.getThemeNames()).doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());

		Buttons.setCreateOrSaveMode(action, false);

		assertThat(action.getText()).isEqualTo("Anlegen");
		assertThat(action.getElement().getAttribute("aria-label")).isEqualTo("Anlegen");
		assertThat(action.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName());
		assertThat(action.getThemeNames()).doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName());
	}

	@Test
	void keepsQuickFilterBeforeLabeledAddActionsAndIconOnlyNewActionsEvenWhenAddedLater() {
		final Button save = Buttons.save();
		final Button add = Buttons.add("Klausurteil hinzufügen", event -> { });
		final Button newItem = Buttons.newItem();
		final QuickFilterField filter = new QuickFilterField();
		final ActionToolbar toolbar = new ActionToolbar(save, add, newItem);

		toolbar.add(filter);

		assertThat(toolbar.getChildren()).containsExactly(filter, add, save, newItem);
		assertThat(newItem.getText()).isEqualTo("Neu");
		assertThat(newItem.getElement().getAttribute("aria-label")).isEqualTo("Neu");
		assertThat(newItem.getTooltip().getText()).isEqualTo("Neu");
		final Button secondAdd = Buttons.add("Material hinzufügen", event -> { });
		toolbar.add(secondAdd);
		assertThat(toolbar.getChildren()).containsExactly(filter, add, secondAdd, save, newItem);
	}

	@Test
	void preparesTransparentIconActionsWithLabelsAvailableWithoutChangingButtonsOutsideTheToolbar() {
		final Button save = Buttons.save();
		final Button discard = Buttons.reset("Verwerfen", event -> { });
		final Button fullscreen = Buttons.icon("Vollbild", VaadinIcon.EXPAND, event -> { });
		fullscreen.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		final Button reload = Buttons.icon("Neu laden", VaadinIcon.REFRESH, event -> { });
		reload.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		final Button formSave = Buttons.save();

		new ActionToolbar(save, discard, fullscreen, reload);

		for (final Button action : new Button[] { save, discard, fullscreen, reload }) {
			assertThat(action.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName(),
					ButtonVariant.LUMO_TERTIARY.getVariantName())
					.doesNotContain(ButtonVariant.LUMO_PRIMARY.getVariantName(),
							ButtonVariant.LUMO_TERTIARY_INLINE.getVariantName());
			assertThat(action.getText()).isEqualTo(action.getAriaLabel().orElseThrow());
		}
		assertThat(fullscreen.getThemeNames()).doesNotContain(ButtonVariant.LUMO_SMALL.getVariantName());
		assertThat(formSave.getThemeNames()).contains(ButtonVariant.LUMO_PRIMARY.getVariantName());
		assertThat(formSave.getText()).isEqualTo("Speichern");
	}

	@Test
	void usesNormalSizingForAllToolbarControlsIncludingThoseAddedLater() {
		final Button add = Buttons.add("Klausurteil hinzufügen", event -> { });
		add.addThemeVariants(ButtonVariant.LUMO_SMALL);
		final ComboBox<String> selector = new ComboBox<>("Schüler:in");
		selector.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
		final MenuBar downloads = new MenuBar();
		downloads.addThemeVariants(MenuBarVariant.LUMO_SMALL);
		final ActionToolbar toolbar = new ActionToolbar(add, selector, downloads);
		final QuickFilterField filter = new QuickFilterField();
		filter.addThemeVariants(TextFieldVariant.LUMO_SMALL);
		final Button compactTreeAction = Buttons.icon("Abschnitt einklappen", VaadinIcon.ANGLE_DOWN, event -> { });
		compactTreeAction.addThemeVariants(ButtonVariant.LUMO_SMALL);

		toolbar.add(filter);

		assertThat(toolbar.getChildren()).containsExactly(filter, add, selector, downloads);
		assertThat(add.getThemeNames()).doesNotContain(ButtonVariant.LUMO_SMALL.getVariantName());
		assertThat(selector.getThemeNames()).doesNotContain(ComboBoxVariant.LUMO_SMALL.getVariantName());
		assertThat(downloads.getThemeNames()).doesNotContain(MenuBarVariant.LUMO_SMALL.getVariantName());
		assertThat(filter.getThemeNames()).doesNotContain(TextFieldVariant.LUMO_SMALL.getVariantName());
		assertThat(compactTreeAction.getThemeNames()).contains(ButtonVariant.LUMO_SMALL.getVariantName());
	}

	@Test
	void usesSquareIconMenuTriggersWithoutChangingDropdownEntries() {
		final ActionToolbar toolbar = new ActionToolbar(Buttons.save());
		final MenuBar downloads = new MenuBar();
		downloads.addThemeVariants(MenuBarVariant.LUMO_SMALL);
		final MenuItem trigger = downloads.addItem(VaadinIcon.DOWNLOAD.create());
		trigger.setAriaLabel("Dateien herunterladen");
		downloads.setTooltipText(trigger, "Dateien herunterladen");
		final MenuItem entry = trigger.getSubMenu().addItem("Punktetabelle");
		final MenuBar outsideToolbar = new MenuBar();

		toolbar.add(downloads);

		assertThat(downloads.getThemeNames()).contains(MenuBarVariant.LUMO_ICON.getVariantName(),
				MenuBarVariant.LUMO_TERTIARY.getVariantName())
				.doesNotContain(MenuBarVariant.LUMO_SMALL.getVariantName());
		assertThat(trigger.getText()).isEmpty();
		assertThat(trigger.getElement().getAttribute("aria-label")).isEqualTo("Dateien herunterladen");
		assertThat(trigger.getElement().getProperty("tooltip")).isEqualTo("Dateien herunterladen");
		assertThat(trigger.getSubMenu().getItems()).containsExactly(entry);
		assertThat(entry.getText()).isEqualTo("Punktetabelle");
		assertThat(outsideToolbar.getThemeNames()).doesNotContain(MenuBarVariant.LUMO_ICON.getVariantName());
	}

	@Test
	void keepsMenuLabelsAvailableForSingletonPresentationWithoutDuplicatingThemOnReuse() {
		final MenuBar downloads = new MenuBar();
		final MenuItem trigger = downloads.addItem(VaadinIcon.DOWNLOAD.create());
		trigger.setAriaLabel("Dateien herunterladen");
		final ActionToolbar first = new ActionToolbar(downloads);

		first.remove(downloads);
		new ActionToolbar(downloads);

		assertThat(trigger.getChildren().filter(child -> child.hasClassName("tt-toolbar-menu-label")))
				.hasSize(1).allSatisfy(label -> assertThat(label.getElement().getText()).isEqualTo("Dateien herunterladen"));
	}

	@Test
	void preservesLabelsAndStateWhileVisibleElementsChangeInPlace() {
		final Button save = Buttons.createOrSave();
		final Button archive = Buttons.archive();
		archive.setVisible(false);
		save.setEnabled(false);
		final ActionToolbar toolbar = new ActionToolbar(save, archive);
		final QuickFilterField filter = new QuickFilterField();
		final StepperComboBox<String> selector = new StepperComboBox<>();
		toolbar.add(filter, selector);

		Buttons.setCreateOrSaveMode(save, true);
		archive.setVisible(true);
		toolbar.remove(filter, selector, archive);

		assertThat(toolbar.getChildren()).containsExactly(save);
		assertThat(save.isEnabled()).isFalse();
		assertThat(save.getText()).isEqualTo("Speichern");
		assertThat(save.getTooltip().getText()).isEqualTo("Speichern");
	}

	@Test
	void countsFiltersSelectorsAndDisabledActionsButNotAttachedDialogHosts() {
		final Button save = Buttons.save();
		save.setEnabled(false);
		final QuickFilterField filter = new QuickFilterField();
		final StepperComboBox<String> selector = new StepperComboBox<>();
		final Button add = Buttons.add("Klausurteil hinzufügen", event -> { });
		final Dialog dialog = new Dialog();
		final ConfirmDialog confirmation = new ConfirmDialog();
		final ActionToolbar toolbar = new ActionToolbar(save, filter, selector, add, dialog, confirmation);

		assertThat(toolbar.getChildren().filter(child -> child.hasClassName("tt-toolbar-element")))
				.containsExactly(filter, add, save, selector);
		assertThat(dialog.hasClassName("tt-toolbar-element")).isFalse();
		assertThat(confirmation.hasClassName("tt-toolbar-element")).isFalse();
	}
}
