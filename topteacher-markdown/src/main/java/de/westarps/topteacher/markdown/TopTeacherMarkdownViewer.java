package de.westarps.topteacher.markdown;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@SuppressWarnings("serial")
@JsModule("./de/westarps/topteacher/markdown/tt-markdown-viewer.tsx")
@Tag("tt-markdown-viewer")
public class TopTeacherMarkdownViewer extends TopTeacherMarkdownComponent {

	public TopTeacherMarkdownViewer() {
		super("");
	}

	public TopTeacherMarkdownViewer(final String content) {
		super(content);
	}
}
