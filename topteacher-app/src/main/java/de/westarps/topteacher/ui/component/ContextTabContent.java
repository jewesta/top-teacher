package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public abstract class ContextTabContent extends VerticalLayout {

	protected ContextTabContent() {
		addClassName("tt-context-tab-content");
		setPadding(false);
		setSpacing(false);
		setSizeFull();
	}
}
