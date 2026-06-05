package de.topteacher.ui.component;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.flowingcode.vaadin.addons.markdown.MarkdownEditor;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.model.EhCategory;

final class EhCategorySection extends AbstractEhSection<EhCategory> {

	EhCategorySection(final EhCategory category, final List<EhCategory> siblings,
			final Collection<? extends Component> tasks, final EhSectionComponents components,
			final EhCollapseState collapseState, final Handler handler, final Supplier<EhPoints> pointsSupplier,
			final List<String> descendantKeys) {
		this(category, siblings, tasks, components, collapseState, handler, pointsSupplier, descendantKeys,
				components.summaryTitleField(category.title()),
				components.markdownEditor(category.descriptionMarkdown(), "Beschreibung"));
	}

	private EhCategorySection(final EhCategory category, final List<EhCategory> siblings,
			final Collection<? extends Component> tasks, final EhSectionComponents components,
			final EhCollapseState collapseState, final Handler handler, final Supplier<EhPoints> pointsSupplier,
			final List<String> descendantKeys, final TextField title, final MarkdownEditor description) {
		super(category, "tt-eh-category", components.summary("Leistungskategorie", title, pointsSupplier));
		title.addValueChangeListener(event -> {
			if (event.isFromClient()) {
				saveTitle(category, title, handler);
			}
		});
		addToBody(components.markdownBlock("Beschreibung", description),
				components.editorBlock(components.saveButton(
						event -> save(category, title, description, components, handler)),
						components.commandButton("Teilaufgabe hinzufügen", VaadinIcon.PLUS,
								event -> handler.addTask(category)),
						components.moveButton("Nach oben", -1, siblings, category, event -> handler.move(category, -1)),
						components.moveButton("Nach unten", 1, siblings, category, event -> handler.move(category, 1)),
						collapseState.toggleButton(descendantKeys),
						components.deleteButton(event -> handler.delete(category))));
		addToBody(tasks);
	}

	private static void saveTitle(final EhCategory category, final TextField title, final Handler handler) {
		if (isBlank(title.getValue())) {
			Notification.show("Titel ist erforderlich.");
			title.setValue(category.title());
			return;
		}
		handler.saveTitle(category, title.getValue());
	}

	private static void save(final EhCategory category, final TextField title, final MarkdownEditor description,
			final EhSectionComponents components, final Handler handler) {
		if (isBlank(title.getValue())) {
			Notification.show("Titel ist erforderlich.");
			return;
		}
		handler.save(category, title.getValue(), components.value(description));
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	interface Handler {

		void saveTitle(EhCategory category, String title);

		void save(EhCategory category, String title, String descriptionMarkdown);

		void addTask(EhCategory category);

		void move(EhCategory category, int offset);

		void delete(EhCategory category);
	}
}
