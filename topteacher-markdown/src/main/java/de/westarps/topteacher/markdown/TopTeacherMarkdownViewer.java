package de.westarps.topteacher.markdown;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

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
