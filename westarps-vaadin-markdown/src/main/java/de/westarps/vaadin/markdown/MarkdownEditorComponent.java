package de.westarps.vaadin.markdown;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@SuppressWarnings("serial")
@JsModule("./de/westarps/vaadin/markdown/ws-markdown-editor.tsx")
@Tag("ws-markdown-editor")
final class MarkdownEditorComponent extends MarkdownComponent {

	private static final int UNLIMITED = -1;
	private Set<MarkdownToolbarCommand> hiddenToolbarCommands = EnumSet.of(MarkdownToolbarCommand.IMAGE);

	MarkdownEditorComponent() {
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
