package de.westarps.vaadin.markdown;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

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

	public Set<MarkdownExtension> getExtensions() {
		return super.getExtensions();
	}

	public void setExtensions(final Collection<MarkdownExtension> extensions) {
		super.setExtensions(extensions);
	}

	public void setExtensions(final MarkdownExtension... extensions) {
		setExtensions(Arrays.asList(extensions));
	}

	public void enableExtension(final MarkdownExtension extension) {
		super.enableExtension(extension);
	}

	public void disableExtension(final MarkdownExtension extension) {
		super.disableExtension(extension);
	}
}
