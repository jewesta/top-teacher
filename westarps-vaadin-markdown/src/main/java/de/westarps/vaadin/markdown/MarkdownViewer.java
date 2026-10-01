package de.westarps.vaadin.markdown;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.shared.Registration;

@SuppressWarnings("serial")
@CssImport("./styles/ws-markdown-editor-styles.css")
@JsModule("./de/westarps/vaadin/markdown/ws-markdown-viewer.tsx")
@NpmPackage(value = "@uiw/react-md-editor", version = "4.0.4")
@NpmPackage(value = "rehype-sanitize", version = "6.0.0")
@Tag("ws-markdown-viewer")
public class MarkdownViewer extends MarkdownComponent {

	private Map<String, ?> extensionState = Map.of();

	public MarkdownViewer() {
		super("");
	}

	public MarkdownViewer(final String content) {
		super(content);
	}

	public List<String> getExtensionIds() {
		return super.getExtensionIds();
	}

	public void setExtensionIds(final Collection<String> extensionIds) {
		super.setExtensionIds(extensionIds);
	}

	public Map<String, ?> getExtensionState() {
		return extensionState;
	}

	public void setExtensionState(final Map<String, ?> extensionState) {
		this.extensionState = extensionState == null ? Map.of() : Map.copyOf(extensionState);
		setState("extensionState", this.extensionState);
	}

	public Registration addRenderCompleteListener(final SerializableRunnable listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("render-complete", event -> listener.run());
	}
}
