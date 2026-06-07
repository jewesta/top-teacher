package de.westarps.vaadin.markdown;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

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
		this.hiddenToolbarCommands = enumSetOf(hiddenToolbarCommands);
		setState("hiddenToolbarCommands", enumStateValue(this.hiddenToolbarCommands));
	}

	void hideToolbarCommand(final MarkdownToolbarCommand command) {
		final Set<MarkdownToolbarCommand> updatedCommands = enumSetOf(hiddenToolbarCommands);
		updatedCommands.add(command);
		setHiddenToolbarCommands(updatedCommands);
	}

	void showToolbarCommand(final MarkdownToolbarCommand command) {
		final Set<MarkdownToolbarCommand> updatedCommands = enumSetOf(hiddenToolbarCommands);
		updatedCommands.remove(command);
		setHiddenToolbarCommands(updatedCommands);
	}

	private static Set<MarkdownToolbarCommand> enumSetOf(final Collection<MarkdownToolbarCommand> values) {
		final Set<MarkdownToolbarCommand> copy = EnumSet.noneOf(MarkdownToolbarCommand.class);
		if (values != null) {
			copy.addAll(values);
		}
		return copy;
	}

	private static String enumStateValue(final Collection<? extends Enum<?>> values) {
		return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
	}
}
