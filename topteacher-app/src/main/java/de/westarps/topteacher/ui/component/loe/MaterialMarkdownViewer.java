package de.westarps.topteacher.ui.component.loe;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.dependency.JsModule;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.vaadin.markdown.MarkdownViewer;

@SuppressWarnings("serial")
@JsModule("./tt-material-image-markdown.tsx")
final class MaterialMarkdownViewer extends MarkdownViewer {

	private final MaterialImageResources resources;

	MaterialMarkdownViewer(final String content, final Collection<LoeMaterialImage> images) {
		super(content);
		resources = new MaterialImageResources(this);
		setExtensionIds(List.of(MaterialMarkdownEditor.EXTENSION_ID));
		setExtensionState(Map.of("materialImages", resources.register(images)));
	}

	void releaseResources() {
		resources.unregister();
		setExtensionState(Map.of());
		setContent("");
	}
}
