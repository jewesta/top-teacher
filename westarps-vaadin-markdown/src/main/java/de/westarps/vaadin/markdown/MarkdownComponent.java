package de.westarps.vaadin.markdown;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

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

	private String content;
	private Set<MarkdownExtension> extensions = EnumSet.noneOf(MarkdownExtension.class);

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

	Set<MarkdownExtension> getExtensions() {
		return Set.copyOf(extensions);
	}

	void setExtensions(final Collection<MarkdownExtension> extensions) {
		this.extensions = enumSetOf(MarkdownExtension.class, extensions);
		setState("extensions", enumStateValue(this.extensions));
	}

	void enableExtension(final MarkdownExtension extension) {
		final Set<MarkdownExtension> updatedExtensions = enumSetOf(MarkdownExtension.class, extensions);
		updatedExtensions.add(extension);
		setExtensions(updatedExtensions);
	}

	void disableExtension(final MarkdownExtension extension) {
		final Set<MarkdownExtension> updatedExtensions = enumSetOf(MarkdownExtension.class, extensions);
		updatedExtensions.remove(extension);
		setExtensions(updatedExtensions);
	}

	@Override
	protected void onAttach(final AttachEvent attachEvent) {
		super.onAttach(attachEvent);
		getElement().setAttribute("data-color-mode", "light");
	}

	static <E extends Enum<E>> Set<E> enumSetOf(final Class<E> enumType, final Collection<E> values) {
		final Set<E> copy = EnumSet.noneOf(enumType);
		if (values != null) {
			copy.addAll(values);
		}
		return copy;
	}

	static String enumStateValue(final Collection<? extends Enum<?>> values) {
		return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}
}
