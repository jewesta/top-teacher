package de.westarps.topteacher.markdown;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@SuppressWarnings("serial")
@JsModule("./de/westarps/topteacher/markdown/tt-markdown-editor.tsx")
@Tag("tt-markdown-editor")
final class TopTeacherMarkdownEditorComponent extends TopTeacherMarkdownComponent {

	private static final int UNLIMITED = -1;

	TopTeacherMarkdownEditorComponent() {
		super("");
	}

	String getPlaceholder() {
		return getState("placeholder", String.class);
	}

	void setPlaceholder(final String placeholder) {
		setState("placeholder", placeholder);
	}

	int getMaxLength() {
		final Integer maxLength = getState("maxLength", Integer.class);
		return maxLength == null ? UNLIMITED : maxLength;
	}

	void setMaxLength(final int maxLength) {
		setState("maxLength", maxLength);
	}
}
