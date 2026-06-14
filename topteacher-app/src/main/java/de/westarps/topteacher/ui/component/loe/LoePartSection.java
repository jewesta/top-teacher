package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.loe.LoePart;

final class LoePartSection extends AbstractLoeSection<LoePart> {

	private final LoePercentageBadge percentageBadge;
	private final Handler handler;
	private final TextField title;
	private String savedTitle;

	LoePartSection(final LoePart part, final List<LoePart> siblings, final List<LoeCategorySection> categories,
			final LoeSectionComponents components, final LoeCollapseState collapseState, final Handler handler,
			final Supplier<Integer> percentageSupplier, final Supplier<LoePoints> pointsSupplier,
			final List<String> descendantKeys, final boolean correctionMode) {
		this(part, siblings, categories, components, collapseState, handler, descendantKeys,
				components.summaryTitleField(part.title()), components.percentageBadge(percentageSupplier),
				components.pointBadge("Summe", pointsSupplier), correctionMode);
	}

	private LoePartSection(final LoePart part, final List<LoePart> siblings, final List<LoeCategorySection> categories,
			final LoeSectionComponents components, final LoeCollapseState collapseState, final Handler handler,
			final List<String> descendantKeys, final TextField title, final LoePercentageBadge percentageBadge,
			final LoePointBadge pointBadge, final boolean correctionMode) {
		super(part, "tt-eh-part", components.partSummary(title, percentageBadge, pointBadge), pointBadge, categories);
		this.percentageBadge = percentageBadge;
		this.handler = handler;
		this.title = title;
		this.savedTitle = part.title();
		components.trackDirty(title);
		final Button addCategory = components.commandButton("Leistungskategorie hinzufügen", VaadinIcon.PLUS,
				event -> handler.addCategory(part));
		final Button delete = components.deleteButton("Klausurteil löschen?", () -> handler.delete(part));
		if (correctionMode) {
			components.lockCorrectionModeAction(addCategory);
			components.lockCorrectionModeAction(delete);
		}
		addToBody(editorBlockWithMoveButtons(components, siblings, handler, List.of(addCategory),
				List.of(collapseState.toggleButton(descendantKeys), delete), correctionMode));
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

	interface Handler extends LoeTitledSectionHandler<LoePart> {

		void addCategory(LoePart part);
	}
}
