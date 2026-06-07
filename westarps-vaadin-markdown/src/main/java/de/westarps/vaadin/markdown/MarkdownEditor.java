package de.westarps.vaadin.markdown;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.vaadin.flow.component.AbstractCompositeField;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.dependency.Uses;

@SuppressWarnings("serial")
@CssImport("./styles/ws-markdown-editor-styles.css")
@JsModule("./de/westarps/vaadin/markdown/ws-markdown-editor.tsx")
@NpmPackage(value = "@uiw/react-md-editor", version = "4.0.4")
@NpmPackage(value = "rehype-sanitize", version = "6.0.0")
@Uses(MarkdownEditorComponent.class)
public class MarkdownEditor extends AbstractCompositeField<MarkdownEditorComponent, MarkdownEditor, String>
		implements HasSize {

	public MarkdownEditor() {
		this("");
	}

	public MarkdownEditor(final String initialValue) {
		super("");
		getEditor().addContentChangeListener(newValue -> setModelValue(normalized(newValue), true));
		getEditor().setContent(normalized(initialValue));
	}

	public String getPlaceholder() {
		return getEditor().getPlaceholder();
	}

	public void setPlaceholder(final String placeholder) {
		getEditor().setPlaceholder(placeholder);
	}

	public int getMaxLength() {
		return getEditor().getMaxLength();
	}

	public void setMaxLength(final int maxLength) {
		getEditor().setMaxLength(maxLength);
	}

	public MarkdownTag getTag() {
		return getEditor().getTag();
	}

	public void setTag(final MarkdownTag tag) {
		getEditor().setTag(tag);
	}

	public void clearTag() {
		setTag(null);
	}

	public String getCanvasColor() {
		return getEditor().getCanvasColor();
	}

	public void setCanvasColor(final String canvasColor) {
		getEditor().setCanvasColor(canvasColor);
	}

	public Set<MarkdownToolbarCommand> getHiddenToolbarCommands() {
		return getEditor().getHiddenToolbarCommands();
	}

	public void setHiddenToolbarCommands(final Collection<MarkdownToolbarCommand> hiddenToolbarCommands) {
		getEditor().setHiddenToolbarCommands(hiddenToolbarCommands);
	}

	public void setHiddenToolbarCommands(final MarkdownToolbarCommand... hiddenToolbarCommands) {
		setHiddenToolbarCommands(Arrays.asList(hiddenToolbarCommands));
	}

	public void hideToolbarCommand(final MarkdownToolbarCommand command) {
		getEditor().hideToolbarCommand(command);
	}

	public void showToolbarCommand(final MarkdownToolbarCommand command) {
		getEditor().showToolbarCommand(command);
	}

	@Override
	protected MarkdownEditorComponent initContent() {
		return new MarkdownEditorComponent();
	}

	@Override
	protected void setPresentationValue(final String newPresentationValue) {
		getEditor().setContent(normalized(newPresentationValue));
	}

	private MarkdownEditorComponent getEditor() {
		return getContent();
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}
}
