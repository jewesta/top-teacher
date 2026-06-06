package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public abstract class AbstractDesigner extends VerticalLayout {

	private final HorizontalLayout toolbar = new HorizontalLayout();
	private final VerticalLayout content = new VerticalLayout();

	protected AbstractDesigner(final String className) {
		addClassNames("tt-designer", className);
		setPadding(false);
		setSpacing(false);
		setSizeFull();

		toolbar.addClassName("tt-designer-toolbar");
		toolbar.setAlignItems(Alignment.CENTER);
		toolbar.setPadding(false);
		toolbar.setSpacing(false);
		toolbar.setWidthFull();

		content.addClassName("tt-designer-content");
		content.setPadding(false);
		content.setSpacing(false);
		content.setWidthFull();
	}

	protected HorizontalLayout toolbar() {
		return toolbar;
	}

	protected VerticalLayout content() {
		return content;
	}

	protected void resetDesigner() {
		removeAll();
		toolbar.removeAll();
		content.removeAll();
	}

	protected void showDesigner() {
		removeAll();
		add(toolbar, content);
		expand(content);
	}

	protected void showDesignerMessage(final Component message) {
		resetDesigner();
		add(message);
	}
}
