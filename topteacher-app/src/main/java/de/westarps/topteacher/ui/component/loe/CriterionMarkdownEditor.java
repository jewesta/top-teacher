package de.westarps.topteacher.ui.component.loe;

import java.util.List;

import com.vaadin.flow.component.dependency.JsModule;

import de.westarps.vaadin.markdown.MarkdownEditor;

@SuppressWarnings("serial")
@JsModule("./tt-criterion-markdown.tsx")
final class CriterionMarkdownEditor extends MarkdownEditor {

	static final String EXTENSION_ID = "tt-criterion";

	CriterionMarkdownEditor(final String value) {
		super(value);
		setExtensionIds(List.of(EXTENSION_ID));
	}
}
