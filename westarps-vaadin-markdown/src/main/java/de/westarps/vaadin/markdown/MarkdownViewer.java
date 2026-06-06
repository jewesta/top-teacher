package de.westarps.vaadin.markdown;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

@SuppressWarnings("serial")
@CssImport("./styles/ws-markdown-editor-styles.css")
@JsModule("./de/westarps/vaadin/markdown/ws-markdown-viewer.tsx")
@NpmPackage(value = "@uiw/react-md-editor", version = "4.0.4")
@NpmPackage(value = "rehype-sanitize", version = "6.0.0")
@Tag("ws-markdown-viewer")
public class MarkdownViewer extends MarkdownComponent {

	public MarkdownViewer() {
		super("");
	}

	public MarkdownViewer(final String content) {
		super(content);
	}

	public MarkdownTag getTag() {
		return super.getTag();
	}

	public void setTag(final MarkdownTag tag) {
		super.setTag(tag);
	}

	public void clearTag() {
		setTag(null);
	}
}
