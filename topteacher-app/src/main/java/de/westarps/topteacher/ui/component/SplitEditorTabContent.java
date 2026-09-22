package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;

public class SplitEditorTabContent extends ContextTabContent {

	private final Div editorHost = new Div();

	public SplitEditorTabContent() {
		addClassName("tt-split-editor-tab-content");

		editorHost.addClassName("tt-editor-host");
		editorHost.setSizeFull();

		add(editorHost);
		expand(editorHost);
	}

	public void setEditor(final Component editor) {
		editorHost.removeAll();
		editorHost.add(editor);
	}

	public boolean hasEditor() {
		return editorHost.getChildren().findAny().isPresent();
	}
}
