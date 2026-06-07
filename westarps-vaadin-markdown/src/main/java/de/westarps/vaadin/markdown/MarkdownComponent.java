package de.westarps.vaadin.markdown;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.react.ReactAdapterComponent;
import com.vaadin.flow.function.SerializableConsumer;

@SuppressWarnings("serial")
@CssImport("./styles/ws-markdown-editor-styles.css")
@NpmPackage(value = "@uiw/react-md-editor", version = "4.0.4")
@NpmPackage(value = "rehype-sanitize", version = "6.0.0")
abstract class MarkdownComponent extends ReactAdapterComponent implements HasSize {

	private static final String CANVAS_COLOR_PROPERTY = "--ws-markdown-canvas-color";

	private String content;
	private MarkdownTag tag;

	protected MarkdownComponent() {
		this("");
	}

	protected MarkdownComponent(final String content) {
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

	public String getCanvasColor() {
		return getElement().getStyle().get(CANVAS_COLOR_PROPERTY);
	}

	public void setCanvasColor(final String canvasColor) {
		if (canvasColor == null || canvasColor.isBlank()) {
			getElement().getStyle().remove(CANVAS_COLOR_PROPERTY);
			return;
		}
		getElement().getStyle().set(CANVAS_COLOR_PROPERTY, canvasColor);
	}

	MarkdownTag getTag() {
		return tag;
	}

	void setTag(final MarkdownTag tag) {
		this.tag = tag;
		setState("tagNamespace", tag == null ? "" : tag.namespace());
		setState("tagToolbarLabel", tag == null ? "" : tag.toolbarLabel());
		setState("tagIdGenerator", tag == null ? "" : tag.idGenerator().name());
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
