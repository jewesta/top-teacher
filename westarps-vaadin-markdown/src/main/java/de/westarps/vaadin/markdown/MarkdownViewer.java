package de.westarps.vaadin.markdown;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.JsonNode;

@SuppressWarnings("serial")
@CssImport("./styles/ws-markdown-editor-styles.css")
@JsModule("./de/westarps/vaadin/markdown/ws-markdown-viewer.tsx")
@NpmPackage(value = "@uiw/react-md-editor", version = "4.0.4")
@NpmPackage(value = "rehype-sanitize", version = "6.0.0")
@Tag("ws-markdown-viewer")
public class MarkdownViewer extends MarkdownComponent {

	private MarkdownTagRenderMode tagRenderMode = MarkdownTagRenderMode.DEFAULT;
	private Set<String> checkedTagKeys = Set.of();

	public MarkdownViewer() {
		super("");
	}

	public MarkdownViewer(final String content) {
		super(content);
	}

	@Override
	public MarkdownTag getTag() {
		return super.getTag();
	}

	@Override
	public void setTag(final MarkdownTag tag) {
		super.setTag(tag);
	}

	public void clearTag() {
		setTag(null);
	}

	public MarkdownTagRenderMode getTagRenderMode() {
		return tagRenderMode;
	}

	public void setTagRenderMode(final MarkdownTagRenderMode tagRenderMode) {
		this.tagRenderMode = tagRenderMode == null ? MarkdownTagRenderMode.DEFAULT : tagRenderMode;
		setState("tagRenderMode", this.tagRenderMode.name());
	}

	public Set<String> getCheckedTagKeys() {
		return Set.copyOf(checkedTagKeys);
	}

	public void setCheckedTagKeys(final Collection<String> checkedTagKeys) {
		this.checkedTagKeys = checkedTagKeys == null ? Set.of()
				: checkedTagKeys.stream().filter(Objects::nonNull).filter(key -> !key.isBlank())
						.collect(Collectors.toCollection(LinkedHashSet::new));
		setState("checkedTagKeys", List.copyOf(this.checkedTagKeys));
	}

	public Registration addTagCheckedChangeListener(final SerializableConsumer<MarkdownTagCheckedChange> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("tag-checked-changed", event -> {
			final JsonNode eventData = event.getEventData();
			final String key = text(eventData, "event.detail.key");
			if (key == null || key.isBlank()) {
				return;
			}
			listener.accept(new MarkdownTagCheckedChange(key, bool(eventData, "event.detail.checked")));
		}).addEventData("event.detail.key").addEventData("event.detail.checked");
	}

	private static String text(final JsonNode eventData, final String key) {
		final JsonNode value = eventData.get(key);
		return value == null ? "" : value.asString("");
	}

	private static boolean bool(final JsonNode eventData, final String key) {
		final JsonNode value = eventData.get(key);
		return value != null && value.asBoolean(false);
	}
}
