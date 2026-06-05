package de.topteacher.ui.component;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.model.EhPart;

final class EhPartSection extends AbstractEhSection<EhPart> {

	EhPartSection(final EhPart part, final List<EhPart> siblings, final Collection<? extends Component> categories,
			final EhSectionComponents components, final EhCollapseState collapseState, final Handler handler,
			final Supplier<Integer> percentageSupplier, final Supplier<EhPoints> pointsSupplier,
			final List<String> descendantKeys) {
		this(part, siblings, categories, components, collapseState, handler, percentageSupplier, pointsSupplier,
				descendantKeys, components.summaryTitleField(part.title()));
	}

	private EhPartSection(final EhPart part, final List<EhPart> siblings,
			final Collection<? extends Component> categories, final EhSectionComponents components,
			final EhCollapseState collapseState, final Handler handler, final Supplier<Integer> percentageSupplier,
			final Supplier<EhPoints> pointsSupplier, final List<String> descendantKeys, final TextField title) {
		super(part, "tt-eh-part", components.partSummary(title, percentageSupplier, pointsSupplier));
		title.addValueChangeListener(event -> {
			if (event.isFromClient()) {
				saveTitle(part, title, handler);
			}
		});
		addToBody(components.editorBlock(components.saveButton(event -> saveTitle(part, title, handler)),
				components.commandButton("Leistungskategorie hinzufügen", VaadinIcon.PLUS,
						event -> handler.addCategory(part)),
				components.moveButton("Nach oben", -1, siblings, part, event -> handler.move(part, -1)),
				components.moveButton("Nach unten", 1, siblings, part, event -> handler.move(part, 1)),
				collapseState.toggleButton(descendantKeys), components.deleteButton(event -> handler.delete(part))));
		addToBody(categories);
	}

	private static void saveTitle(final EhPart part, final TextField title, final Handler handler) {
		if (isBlank(title.getValue())) {
			Notification.show("Titel ist erforderlich.");
			title.setValue(part.title());
			return;
		}
		handler.saveTitle(part, title.getValue());
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	interface Handler {

		void saveTitle(EhPart part, String title);

		void addCategory(EhPart part);

		void move(EhPart part, int offset);

		void delete(EhPart part);
	}
}
