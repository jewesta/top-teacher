package de.westarps.topteacher.ui.component.loe;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownToolbarCommand;
import tools.jackson.databind.JsonNode;

@SuppressWarnings("serial")
@JsModule("./tt-material-image-markdown.tsx")
final class MaterialMarkdownEditor extends MarkdownEditor {

	static final String EXTENSION_ID = "tt-material-image";
	private final MaterialImageResources imageResources = new MaterialImageResources(this);

	MaterialMarkdownEditor(final String value, final Collection<LoeMaterialImage> images) {
		super(value);
		showToolbarCommand(MarkdownToolbarCommand.FOOTNOTE);
		setExtensionIds(List.of(EXTENSION_ID));
		setImages(images);
	}

	void setImages(final Collection<LoeMaterialImage> images) {
		setExtensionState(Map.of("materialImages", imageResources.register(images)));
	}

	Registration addImageRequestListener(final SerializableConsumer<InsertionPoint> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("markdown-extension-event", event -> {
			final JsonNode data = event.getEventData();
			if (!"material-image-requested".equals(data.path("event.detail.name").asString(""))) {
				return;
			}
			listener.accept(new InsertionPoint(data.path("event.detail.selectionStart").asInt(getValue().length()),
					data.path("event.detail.selectionEnd").asInt(getValue().length()),
					data.path("event.detail.selectedText").asString("")));
		}).addEventData("event.detail.name").addEventData("event.detail.selectionStart")
				.addEventData("event.detail.selectionEnd").addEventData("event.detail.selectedText");
	}

	Registration addFileDropListener(final SerializableConsumer<FileDrop> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("markdown-extension-event", event -> {
			final JsonNode data = event.getEventData();
			if (!"file-drop".equals(data.path("event.detail.name").asString(""))) {
				return;
			}
			listener.accept(new FileDrop(data.path("event.detail.fileCount").asInt(0),
					data.path("event.detail.fileTypes").path(0).asString(""),
					new InsertionPoint(data.path("event.detail.selectionStart").asInt(getValue().length()),
							data.path("event.detail.selectionEnd").asInt(getValue().length()), "")));
		}).addEventData("event.detail.name").addEventData("event.detail.fileCount")
				.addEventData("event.detail.fileTypes").addEventData("event.detail.selectionStart")
				.addEventData("event.detail.selectionEnd");
	}

	void uploadDroppedFileWith(final Upload upload) {
		upload.getElement().executeJs("this._addFiles($0.takeDroppedFiles())", getElement());
	}

	void insertImage(final InsertionPoint insertionPoint, final String declaration) {
		final String current = getValue();
		final int start = Math.max(0, Math.min(insertionPoint.start(), current.length()));
		final int end = Math.max(start, Math.min(insertionPoint.end(), current.length()));
		final String updated = current.substring(0, start) + declaration + current.substring(end);
		setValue(updated);
		getElement().executeJs("this.focusAt($0)", start + declaration.length());
	}

	record InsertionPoint(int start, int end, String selectedText) {
	}

	record FileDrop(int fileCount, String mediaType, InsertionPoint insertionPoint) {
	}
}
