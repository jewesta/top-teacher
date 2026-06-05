package de.topteacher.ui.component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.flowingcode.vaadin.addons.markdown.MarkdownEditor;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.model.EhCategory;

final class EhCategorySection extends AbstractEhSection<EhCategory> {

	private final TextField title;
	private final MarkdownEditor description;
	private final Handler handler;
	private String savedTitle;
	private String savedDescriptionMarkdown;

	EhCategorySection(final EhCategory category, final List<EhCategory> siblings, final List<EhTaskSection> tasks,
			final EhSectionComponents components, final EhCollapseState collapseState, final Handler handler,
			final Supplier<EhPoints> pointsSupplier, final List<String> descendantKeys) {
		this(category, siblings, tasks, components, collapseState, handler, descendantKeys,
				components.summaryTitleField(category.title()),
				components.markdownEditor(category.descriptionMarkdown(), "Beschreibung"),
				components.pointBadge("Summe", pointsSupplier));
	}

	private EhCategorySection(final EhCategory category, final List<EhCategory> siblings,
			final List<EhTaskSection> tasks, final EhSectionComponents components, final EhCollapseState collapseState,
			final Handler handler, final List<String> descendantKeys, final TextField title,
			final MarkdownEditor description, final EhPointBadge pointBadge) {
		super(category, "tt-eh-category", components.summary("Leistungskategorie", title, pointBadge), pointBadge,
				tasks);
		this.title = title;
		this.description = description;
		this.handler = handler;
		this.savedTitle = category.title();
		this.savedDescriptionMarkdown = normalized(category.descriptionMarkdown());
		components.trackDirty(title);
		components.trackDirty(description);
		addToBody(components.markdownBlock("Beschreibung", description),
				editorBlockWithMoveButtons(components, siblings, handler,
						List.of(components.saveButton(),
								components.commandButton("Teilaufgabe hinzufügen", VaadinIcon.PLUS,
										event -> handler.addTask(category))),
						List.of(collapseState.toggleButton(descendantKeys),
								components.deleteButton(event -> handler.delete(category)))));
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

	interface Handler extends EhTitledSectionHandler<EhCategory> {

		void save(EhCategory category, String title, String descriptionMarkdown);

		void addTask(EhCategory category);
	}
}
