package de.westarps.topteacher.markdown;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.vaadin.flow.component.AbstractCompositeField;
import com.vaadin.flow.component.HasSize;

@SuppressWarnings("serial")
public class TopTeacherMarkdownEditor extends
		AbstractCompositeField<TopTeacherMarkdownEditorComponent, TopTeacherMarkdownEditor, String> implements HasSize {

	public TopTeacherMarkdownEditor() {
		this("");
	}

	public TopTeacherMarkdownEditor(final String initialValue) {
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

	public Set<MarkdownExtension> getExtensions() {
		return getEditor().getExtensions();
	}

	public void setExtensions(final Collection<MarkdownExtension> extensions) {
		getEditor().setExtensions(extensions);
	}

	public void setExtensions(final MarkdownExtension... extensions) {
		setExtensions(Arrays.asList(extensions));
	}

	public void enableExtension(final MarkdownExtension extension) {
		getEditor().enableExtension(extension);
	}

	public void disableExtension(final MarkdownExtension extension) {
		getEditor().disableExtension(extension);
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
	protected TopTeacherMarkdownEditorComponent initContent() {
		return new TopTeacherMarkdownEditorComponent();
	}

	@Override
	protected void setPresentationValue(final String newPresentationValue) {
		getEditor().setContent(normalized(newPresentationValue));
	}

	private TopTeacherMarkdownEditorComponent getEditor() {
		return getContent();
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}
}
