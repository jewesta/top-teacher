package de.westarps.vaadin.markdown;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vaadin.flow.component.AbstractCompositeField;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.JsonNode;

@SuppressWarnings("serial")
@CssImport("./styles/ws-markdown-editor-styles.css")
@JsModule("./de/westarps/vaadin/markdown/ws-markdown-editor.tsx")
@NpmPackage(value = "@uiw/react-md-editor", version = "4.0.4")
@NpmPackage(value = "rehype-sanitize", version = "6.0.0")
@Uses(MarkdownEditorComponent.class)
public class MarkdownEditor extends AbstractCompositeField<MarkdownEditorComponent, MarkdownEditor, String>
		implements HasSize {

	private static final Pattern NUMERIC_FOOTNOTE_PATTERN = Pattern.compile("\\[\\^(\\d+)]");

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

	public Collection<String> getExtensionIds() {
		return getEditor().getExtensionIds();
	}

	public void setExtensionIds(final Collection<String> extensionIds) {
		getEditor().setExtensionIds(extensionIds);
	}

	public Map<String, ?> getExtensionState() {
		return getEditor().getExtensionState();
	}

	public void setExtensionState(final Map<String, ?> extensionState) {
		getEditor().setExtensionState(extensionState);
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

	public Registration addFootnoteRequestListener(final SerializableConsumer<InsertionPoint> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("markdown-extension-event", event -> {
			final JsonNode data = event.getEventData();
			if (!"footnote-requested".equals(data.path("event.detail.name").asString(""))) {
				return;
			}
			listener.accept(new InsertionPoint(data.path("event.detail.selectionEnd").asInt(getValue().length())));
		}).addEventData("event.detail.name").addEventData("event.detail.selectionEnd");
	}

	public Registration addAlertRequestListener(final SerializableConsumer<BlockSelection> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("markdown-extension-event", event -> {
			final JsonNode data = event.getEventData();
			if (!"alert-requested".equals(data.path("event.detail.name").asString(""))) {
				return;
			}
			listener.accept(new BlockSelection(data.path("event.detail.selectionStart").asInt(getValue().length()),
					data.path("event.detail.selectionEnd").asInt(getValue().length())));
		}).addEventData("event.detail.name").addEventData("event.detail.selectionStart")
				.addEventData("event.detail.selectionEnd");
	}

	public String selectedBlockText(final BlockSelection selection) {
		final BlockSelection range = blockRange(selection);
		return getValue().substring(range.start(), range.end());
	}

	public Optional<AlertBlock> findAlert(final BlockSelection selection) {
		return MarkdownAlertSource.find(getValue(), Objects.requireNonNull(selection, "selection must not be null"));
	}

	public void insertAlert(final BlockSelection selection, final MarkdownAlertType type, final String text) {
		Objects.requireNonNull(type, "type must not be null");
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("alert text must not be blank");
		}
		final var existing = findAlert(selection);
		if (existing.isPresent()) {
			final AlertBlock alert = existing.orElseThrow();
			final String current = getValue();
			final String original = current.substring(alert.selection().start(), alert.selection().end());
			final String newline = original.contains("\r\n") ? "\r\n" : "\n";
			final String body = text.replace("\r\n", "\n").replace('\r', '\n')
					.replace("\n", newline + alert.continuationPrefix() + "> ");
			final String block = "> [!" + type.name() + "]" + newline + alert.continuationPrefix() + "> " + body;
			setValue(current.substring(0, alert.selection().start()) + block + current.substring(alert.selection().end()));
			getElement().executeJs("this.focusAt($0)", alert.selection().start() + block.length());
			return;
		}
		final BlockSelection range = blockRange(selection);
		final String current = getValue();
		final String before = current.substring(0, range.start());
		final String after = current.substring(range.end());
		final String body = text.strip().replace("\r\n", "\n").replace('\r', '\n')
				.replace("\n", "\n> ");
		final String block = "> [!" + type.name() + "]\n> " + body;
		final String prefix = before.isEmpty() || before.endsWith("\n\n") ? ""
				: before.endsWith("\n") ? "\n" : "\n\n";
		final String suffix = after.isEmpty() || after.startsWith("\n\n") ? ""
				: after.startsWith("\n") ? "\n" : "\n\n";
		setValue(before + prefix + block + suffix + after);
		getElement().executeJs("this.focusAt($0)", before.length() + prefix.length() + block.length());
	}

	private BlockSelection blockRange(final BlockSelection selection) {
		Objects.requireNonNull(selection, "selection must not be null");
		final String current = getValue();
		final int start = Math.max(0, Math.min(selection.start(), current.length()));
		final int end = Math.max(start, Math.min(selection.end(), current.length()));
		if (start == end) {
			return new BlockSelection(start, end);
		}
		final int blockStart = start == 0 ? 0 : current.lastIndexOf('\n', start - 1) + 1;
		final int lastSelected = current.charAt(end - 1) == '\n' ? end - 1 : end;
		final int nextNewline = current.indexOf('\n', lastSelected);
		return new BlockSelection(blockStart, nextNewline < 0 ? current.length() : nextNewline);
	}

	public void insertFootnote(final InsertionPoint insertionPoint, final String text) {
		Objects.requireNonNull(insertionPoint, "insertionPoint must not be null");
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("footnote text must not be blank");
		}
		final String current = getValue();
		final int position = Math.max(0, Math.min(insertionPoint.position(), current.length()));
		final String id = Integer.toString(nextFootnoteNumber(current));
		final String reference = "[^" + id + "]";
		final String withReference = current.substring(0, position) + reference + current.substring(position);
		final String separator = withReference.endsWith("\n\n") ? "" : withReference.endsWith("\n") ? "\n" : "\n\n";
		final String definition = "[^" + id + "]: " + footnoteDefinitionText(text);
		setValue(withReference + separator + definition);
		getElement().executeJs("this.focusAt($0)", position + reference.length());
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

	private static int nextFootnoteNumber(final String markdown) {
		int maximum = 0;
		final Matcher matcher = NUMERIC_FOOTNOTE_PATTERN.matcher(markdown);
		while (matcher.find()) {
			try {
				maximum = Math.max(maximum, Integer.parseInt(matcher.group(1)));
			} catch (final NumberFormatException exception) {
				/* An impractically large label is already occupied but must not prevent insertion. */
			}
		}
		return maximum + 1;
	}

	private static String footnoteDefinitionText(final String text) {
		return text.strip().replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\n    ");
	}

	public record InsertionPoint(int position) {
	}

	public record AlertBlock(BlockSelection selection, MarkdownAlertType type, String text, String continuationPrefix) {
	}

	public record BlockSelection(int start, int end) {
	}
}
