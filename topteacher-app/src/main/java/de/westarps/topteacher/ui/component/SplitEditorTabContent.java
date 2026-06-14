package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class SplitEditorTabContent extends VerticalLayout {

	private final Div editorHost = new Div();

	public SplitEditorTabContent() {
		addClassName("tt-split-editor-tab-content");
		setPadding(false);
		setSpacing(false);
		setSizeFull();

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
