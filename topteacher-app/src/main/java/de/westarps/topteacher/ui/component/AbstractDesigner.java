package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import de.westarps.validate.ValidationSummary;
import de.westarps.vaadin.tray.StatusTray;

public abstract class AbstractDesigner extends VerticalLayout {

	private final HorizontalLayout toolbar = new HorizontalLayout();
	private final HorizontalLayout toolbarSummary = new HorizontalLayout();
	private final VerticalLayout content = new VerticalLayout();
	private final StatusTray statusTray = new StatusTray("Status");

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

		toolbarSummary.addClassName("tt-designer-toolbar-summary");
		toolbarSummary.setAlignItems(Alignment.CENTER);
		toolbarSummary.setJustifyContentMode(JustifyContentMode.END);
		toolbarSummary.setPadding(false);
		toolbarSummary.setSpacing(false);
		toolbarSummary.setWidthFull();

		content.addClassName("tt-designer-content");
		content.setPadding(false);
		content.setSpacing(false);
		content.setWidthFull();
	}

	protected HorizontalLayout toolbar() {
		return toolbar;
	}

	protected HorizontalLayout toolbarSummary() {
		return toolbarSummary;
	}

	protected VerticalLayout content() {
		return content;
	}

	protected final StatusTray statusTray() {
		return statusTray;
	}

	protected final void setValidationResults(final ValidationSummary results) {
		statusTray.setResults(results);
	}

	protected void resetDesigner() {
		removeAll();
		toolbar.removeAll();
		toolbarSummary.removeAll();
		content.removeAll();
	}

	protected void showDesigner() {
		removeAll();
		if (hasChildren(toolbar)) {
			add(toolbar);
		}
		if (hasChildren(toolbarSummary)) {
			add(toolbarSummary);
		}
		add(content);
		expand(content);
		add(statusTray);
	}

	protected void showDesignerMessage(final Component message) {
		resetDesigner();
		add(message, statusTray);
	}

	private static boolean hasChildren(final Component component) {
		return component.getChildren().findAny().isPresent();
	}
}
