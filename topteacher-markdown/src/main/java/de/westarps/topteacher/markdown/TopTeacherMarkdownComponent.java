package de.westarps.topteacher.markdown;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.react.ReactAdapterComponent;
import com.vaadin.flow.function.SerializableConsumer;

@SuppressWarnings("serial")
@CssImport("./styles/tt-markdown-editor-styles.css")
@NpmPackage(value = "@uiw/react-md-editor", version = "4.0.4")
@NpmPackage(value = "rehype-sanitize", version = "6.0.0")
abstract class TopTeacherMarkdownComponent extends ReactAdapterComponent implements HasSize {

	private String content;

	protected TopTeacherMarkdownComponent() {
		this("");
	}

	protected TopTeacherMarkdownComponent(final String content) {
		setContent(content);
		addContentChangeListener(newContent -> this.content = normalized(newContent));
	}

	public String getContent() {
		return content;
	}

	public void setContent(final String content) {
		this.content = normalized(content);
		setState("content", this.content);
	}

	public void addContentChangeListener(final SerializableConsumer<String> listener) {
		addStateChangeListener("content", String.class, listener);
	}

	@Override
	protected void onAttach(final AttachEvent attachEvent) {
		super.onAttach(attachEvent);
		getElement().setAttribute("data-color-mode", "light");
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}
}
