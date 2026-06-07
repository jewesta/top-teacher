package de.westarps.topteacher.ui.component.eh;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.eh.EhPart;

final class EhPartSection extends AbstractEhSection<EhPart> {

	private final EhPercentageBadge percentageBadge;
	private final Handler handler;
	private final TextField title;
	private String savedTitle;

	EhPartSection(final EhPart part, final List<EhPart> siblings, final List<EhCategorySection> categories,
			final EhSectionComponents components, final EhCollapseState collapseState, final Handler handler,
			final Supplier<Integer> percentageSupplier, final Supplier<EhPoints> pointsSupplier,
			final List<String> descendantKeys) {
		this(part, siblings, categories, components, collapseState, handler, descendantKeys,
				components.summaryTitleField(part.title()), components.percentageBadge(percentageSupplier),
				components.pointBadge("Summe", pointsSupplier));
	}

	private EhPartSection(final EhPart part, final List<EhPart> siblings, final List<EhCategorySection> categories,
			final EhSectionComponents components, final EhCollapseState collapseState, final Handler handler,
			final List<String> descendantKeys, final TextField title, final EhPercentageBadge percentageBadge,
			final EhPointBadge pointBadge) {
		super(part, "tt-eh-part", components.partSummary(title, percentageBadge, pointBadge), pointBadge, categories);
		this.percentageBadge = percentageBadge;
		this.handler = handler;
		this.title = title;
		this.savedTitle = part.title();
		components.trackDirty(title);
		addToBody(editorBlockWithMoveButtons(components, siblings, handler,
				List.of(components.commandButton("Leistungskategorie hinzufügen", VaadinIcon.PLUS,
						event -> handler.addCategory(part))),
				List.of(collapseState.toggleButton(descendantKeys),
						components.deleteButton(event -> handler.delete(part)))));
		addToBody(categories);
	}

	@Override
	protected void refreshSectionBadges() {
		percentageBadge.refreshBadges();
	}

	@Override
	protected boolean isSectionDirty() {
		return !Objects.equals(savedTitle, title.getValue());
	}

	@Override
	protected boolean saveSection() {
		if (isBlank(title.getValue())) {
			Notification.show("Titel ist erforderlich.");
			title.setValue(savedTitle);
			return false;
		}
		handler.saveTitle(item(), title.getValue());
		savedTitle = title.getValue();
		return true;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	interface Handler extends EhTitledSectionHandler<EhPart> {

		void addCategory(EhPart part);
	}
}
