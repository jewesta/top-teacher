package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class LoeMaterialSection extends AbstractLoeSection<LoeMaterial> {

	private final TextField title;
	private final MarkdownEditor description;
	private final Handler handler;
	private String savedTitle;
	private String savedDescriptionMarkdown;

	LoeMaterialSection(final LoeMaterial material, final List<LoeMaterial> siblings,
			final LoeSectionComponents components, final Handler handler, final boolean locked) {
		this(material, siblings, components, handler, components.summaryTitleField(material.title()),
				components.markdownEditor(material.descriptionMarkdown(), "Quellentext oder anderes Material"), locked);
	}

	private LoeMaterialSection(final LoeMaterial material, final List<LoeMaterial> siblings,
			final LoeSectionComponents components, final Handler handler, final TextField title,
			final MarkdownEditor description, final boolean locked) {
		super(material, "tt-eh-category", components.summary("Material", title), null, List.of());
		this.title = title;
		this.description = description;
		this.handler = handler;
		this.savedTitle = material.title();
		this.savedDescriptionMarkdown = normalized(material.descriptionMarkdown());
		title.setReadOnly(locked);
		description.setEnabled(!locked);
		components.trackDirty(title);
		components.trackDirty(description);
		final Button delete = components.deleteButton("Material löschen?", () -> handler.delete(material));
		if (locked) {
			components.lockCorrectionModeAction(delete);
		}
		final Component descriptionBlock = locked
				? components.markdownViewBlock("Inhalt", material.descriptionMarkdown())
				: components.markdownBlock("Inhalt", description);
		addToBody(descriptionBlock,
				editorBlockWithMoveButtons(components, siblings, handler, List.of(), List.of(delete), locked));
	}

	@Override
	protected boolean isSectionDirty() {
		return !Objects.equals(savedTitle, title.getValue())
				|| !Objects.equals(savedDescriptionMarkdown, normalized(description.getValue()));
	}

	@Override
	protected boolean saveSection() {
		if (title.getValue() == null || title.getValue().isBlank()) {
			Notification.show("Titel ist erforderlich.");
			return false;
		}
		final String descriptionMarkdown = normalized(description.getValue());
		handler.save(item(), title.getValue(), descriptionMarkdown);
		savedTitle = title.getValue();
		savedDescriptionMarkdown = descriptionMarkdown;
		return true;
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}

	interface Handler extends LoeSectionHandler<LoeMaterial> {

		void save(LoeMaterial material, String title, String descriptionMarkdown);
	}
}

