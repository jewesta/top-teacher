package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/** A settings form row whose actions stay beside the input controls as validation text grows. */
public class SettingsLineItem extends HorizontalLayout {

	private final HorizontalLayout fields = new HorizontalLayout();
	private final HorizontalLayout actions = new HorizontalLayout();

	public SettingsLineItem() {
		addClassName("tt-settings-line-item");
		setPadding(false);
		setSpacing(true);
		setAlignItems(Alignment.BASELINE);
		setWidthFull();

		fields.addClassName("tt-settings-line-item-fields");
		fields.setPadding(false);
		fields.setSpacing(true);
		fields.setAlignItems(Alignment.BASELINE);
		fields.setWidthFull();
		actions.addClassName("tt-settings-line-item-actions");
		actions.setPadding(false);
		actions.setSpacing(true);
		actions.setAlignItems(Alignment.BASELINE);
		add(fields, actions);
		setFlexGrow(1, fields);
	}

	public void addFields(final Component... components) {
		fields.add(components);
	}

	public void addActions(final Component... components) {
		actions.add(components);
	}
}
