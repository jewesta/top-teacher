package de.westarps.topteacher.markdown;

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
