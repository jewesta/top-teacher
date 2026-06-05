package de.topteacher.ui.component;

import java.util.List;
import java.util.function.Supplier;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.model.EhPart;

final class EhPartSection extends AbstractEhSection<EhPart> {

	private final EhPercentageBadge percentageBadge;

	EhPartSection(final EhPart part, final List<EhPart> siblings, final List<EhCategorySection> categories,
			final EhSectionComponents components, final EhCollapseState collapseState, final Handler handler,
			final Supplier<Integer> percentageSupplier, final Supplier<EhPoints> pointsSupplier,
			final List<String> descendantKeys) {
		this(part, siblings, categories, components, collapseState, handler, descendantKeys,
				components.summaryTitleField(part.title()), components.percentageBadge(percentageSupplier),
				components.pointBadge("Summe", pointsSupplier));
	}

	private EhPartSection(final EhPart part, final List<EhPart> siblings, final List<EhCategorySection> categories,
			final EhSectionComponents components,
			final EhCollapseState collapseState, final Handler handler, final List<String> descendantKeys, final TextField title,
			final EhPercentageBadge percentageBadge, final EhPointBadge pointBadge) {
		super(part, "tt-eh-part", components.partSummary(title, percentageBadge, pointBadge), pointBadge, categories);
		this.percentageBadge = percentageBadge;
		title.addValueChangeListener(event -> {
			if (event.isFromClient()) {
				saveTitle(part, title, handler);
			}
		});
		addToBody(editorBlockWithMoveButtons(components, siblings, handler,
				List.of(components.saveButton(event -> saveTitle(part, title, handler)),
						components.commandButton("Leistungskategorie hinzufügen", VaadinIcon.PLUS,
								event -> handler.addCategory(part))),
				List.of(collapseState.toggleButton(descendantKeys), components.deleteButton(event -> handler.delete(part)))));
		addToBody(categories);
	}

	@Override
	protected void refreshSectionBadges() {
		percentageBadge.refreshBadges();
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

	interface Handler extends EhTitledSectionHandler<EhPart> {

		void addCategory(EhPart part);
	}
}
