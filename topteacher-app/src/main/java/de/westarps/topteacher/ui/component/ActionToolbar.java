package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public final class ActionToolbar extends HorizontalLayout {

	public ActionToolbar(final Component... components) {
		add(components);
	}

	@Override
	public void add(final Component... components) {
		for (final Component component : components) {
			if (component instanceof Button button) {
				Buttons.configureToolbarAction(button);
				if (Buttons.isAdd(button)) {
					final int addButtons = (int) getChildren().filter(Button.class::isInstance).map(Button.class::cast)
							.filter(Buttons::isAdd).count();
					addComponentAtIndex(addButtons, button);
					continue;
				}
			}
			super.add(component);
		}
	}
}
