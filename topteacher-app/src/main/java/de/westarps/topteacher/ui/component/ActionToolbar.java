package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public final class ActionToolbar extends HorizontalLayout {

	public ActionToolbar(final Component... components) {
		addClassName("tt-action-toolbar");
		setSpacing(false);
		add(components);
	}

	@Override
	public void add(final Component... components) {
		for (final Component component : components) {
			/* Attached dialog hosts are not visual toolbar elements. */
			if (component instanceof Dialog || component instanceof ConfirmDialog) {
				super.add(component);
				continue;
			}
			component.addClassName("tt-toolbar-element");
			component.getElement().getThemeList().remove("small");
			if (component instanceof MenuBar menu) {
				configureMenu(menu);
			}
			if (component instanceof StepperComboBox<?> selector) {
				selector.configureToolbar();
			}
			if (component instanceof QuickFilterField) {
				addComponentAtIndex(0, component);
				continue;
			}
			if (component instanceof Button button) {
				Buttons.configureToolbarAction(button);
				if (Buttons.isAdd(button)) {
					final int addButtons = (int) getChildren().filter(Button.class::isInstance).map(Button.class::cast)
							.filter(Buttons::isAdd).count();
					final int quickFilters = (int) getChildren().filter(QuickFilterField.class::isInstance).count();
					addComponentAtIndex(quickFilters + addButtons, button);
					continue;
				}
			}
			super.add(component);
		}
	}

	private static void configureMenu(final MenuBar menu) {
		menu.removeThemeVariants(MenuBarVariant.LUMO_PRIMARY, MenuBarVariant.LUMO_TERTIARY_INLINE);
		menu.addThemeVariants(MenuBarVariant.LUMO_ICON, MenuBarVariant.LUMO_TERTIARY);
		for (final MenuItem item : menu.getItems()) {
			if (item.getText().isBlank() && item.getAriaLabel().isPresent()
					&& item.getChildren().noneMatch(child -> child.hasClassName("tt-toolbar-menu-label"))) {
				final Span label = new Span(item.getAriaLabel().orElseThrow());
				label.addClassName("tt-toolbar-menu-label");
				item.add(label);
			}
		}
	}
}
