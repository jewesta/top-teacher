package de.westarps.topteacher.markdown;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@SuppressWarnings("serial")
@JsModule("./de/westarps/topteacher/markdown/tt-markdown-editor.tsx")
@Tag("tt-markdown-editor")
final class TopTeacherMarkdownEditorComponent extends TopTeacherMarkdownComponent {

	private static final int UNLIMITED = -1;
	private Set<MarkdownToolbarCommand> hiddenToolbarCommands = EnumSet.of(MarkdownToolbarCommand.IMAGE);

	TopTeacherMarkdownEditorComponent() {
		super("");
		setHiddenToolbarCommands(hiddenToolbarCommands);
	}

	String getPlaceholder() {
		return getState("placeholder", String.class);
	}

	void setPlaceholder(final String placeholder) {
		setState("placeholder", placeholder);
	}

	int getMaxLength() {
		final Integer maxLength = getState("maxLength", Integer.class);
		return maxLength == null ? UNLIMITED : maxLength;
	}

	void setMaxLength(final int maxLength) {
		setState("maxLength", maxLength);
	}

	Set<MarkdownToolbarCommand> getHiddenToolbarCommands() {
		return Set.copyOf(hiddenToolbarCommands);
	}

	void setHiddenToolbarCommands(final Collection<MarkdownToolbarCommand> hiddenToolbarCommands) {
		this.hiddenToolbarCommands = enumSetOf(MarkdownToolbarCommand.class, hiddenToolbarCommands);
		setState("hiddenToolbarCommands", enumStateValue(this.hiddenToolbarCommands));
	}

	void hideToolbarCommand(final MarkdownToolbarCommand command) {
		final Set<MarkdownToolbarCommand> updatedCommands = enumSetOf(MarkdownToolbarCommand.class,
				hiddenToolbarCommands);
		updatedCommands.add(command);
		setHiddenToolbarCommands(updatedCommands);
	}

	void showToolbarCommand(final MarkdownToolbarCommand command) {
		final Set<MarkdownToolbarCommand> updatedCommands = enumSetOf(MarkdownToolbarCommand.class,
				hiddenToolbarCommands);
		updatedCommands.remove(command);
		setHiddenToolbarCommands(updatedCommands);
	}
}
