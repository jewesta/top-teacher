package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class LoeCategorySection extends AbstractLoeSection<LoeCategory> {

	private final TextField title;
	private final MarkdownEditor description;
	private final Handler handler;
	private String savedTitle;
	private String savedDescriptionMarkdown;

	LoeCategorySection(final LoeCategory category, final List<LoeCategory> siblings, final List<LoeTaskSection> tasks,
			final LoeSectionComponents components, final LoeCollapseState collapseState, final Handler handler,
			final Supplier<LoePoints> pointsSupplier, final List<String> descendantKeys, final boolean correctionMode) {
		this(category, siblings, tasks, components, collapseState, handler, descendantKeys,
				components.summaryTitleField(category.title()),
				components.categoryCommentEditor(category.descriptionMarkdown(), "Beschreibung"),
				components.pointBadge("Summe", pointsSupplier), correctionMode);
	}

	private LoeCategorySection(final LoeCategory category, final List<LoeCategory> siblings,
			final List<LoeTaskSection> tasks, final LoeSectionComponents components,
			final LoeCollapseState collapseState, final Handler handler, final List<String> descendantKeys,
			final TextField title, final MarkdownEditor description, final LoePointBadge pointBadge,
			final boolean correctionMode) {
		super(category, "tt-eh-category", components.summary("Leistungskategorie", title, pointBadge), pointBadge,
				tasks);
		this.title = title;
		this.description = description;
		this.handler = handler;
		this.savedTitle = category.title();
		this.savedDescriptionMarkdown = normalized(category.descriptionMarkdown());
		components.trackDirty(title);
		components.trackDirty(description);
		final Button addTask = components.commandButton("Teilaufgabe hinzufügen", VaadinIcon.PLUS,
				event -> handler.addTask(category));
		final Button delete = components.deleteButton("Leistungskategorie löschen?", () -> handler.delete(category));
		if (correctionMode) {
			components.lockCorrectionModeAction(addTask);
			components.lockCorrectionModeAction(delete);
		}
		addToBody(components.markdownBlock("Beschreibung", description),
				editorBlockWithMoveButtons(components, siblings, handler, List.of(addTask),
						List.of(collapseState.toggleButton(descendantKeys), delete), correctionMode));
		addToBody(tasks);
	}

	@Override
	protected boolean isSectionDirty() {
		return !Objects.equals(savedTitle, title.getValue())
				|| !Objects.equals(savedDescriptionMarkdown, componentsValue(description));
	}

	@Override
	protected boolean saveSection() {
		if (isBlank(title.getValue())) {
			Notification.show("Titel ist erforderlich.");
			return false;
		}
		final String descriptionMarkdown = componentsValue(description);
		handler.save(item(), title.getValue(), descriptionMarkdown);
		savedTitle = title.getValue();
		savedDescriptionMarkdown = descriptionMarkdown;
		return true;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	private static String componentsValue(final MarkdownEditor editor) {
		return normalized(editor.getValue());
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}

	interface Handler extends LoeTitledSectionHandler<LoeCategory> {

		void save(LoeCategory category, String title, String descriptionMarkdown);

		void addTask(LoeCategory category);
	}
}
