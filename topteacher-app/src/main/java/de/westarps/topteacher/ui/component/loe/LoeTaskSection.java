package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.loe.LoeTask;

final class LoeTaskSection extends AbstractLoeSection<LoeTask> {

	private final TextField title;
	private final Handler handler;
	private String savedTitle;

	LoeTaskSection(final LoeTask task, final List<LoeTask> siblings, final List<LoeRequirementSection> requirements,
			final LoeSectionComponents components, final LoeCollapseState collapseState, final Handler handler,
			final Supplier<LoePoints> pointsSupplier, final List<String> descendantKeys, final boolean correctionMode) {
		this(task, siblings, requirements, components, collapseState, handler, descendantKeys,
				components.summaryTitleField(task.title()), components.pointBadge("Summe", pointsSupplier),
				correctionMode);
	}

	private LoeTaskSection(final LoeTask task, final List<LoeTask> siblings,
			final List<LoeRequirementSection> requirements, final LoeSectionComponents components,
			final LoeCollapseState collapseState, final Handler handler, final List<String> descendantKeys,
			final TextField title, final LoePointBadge pointBadge, final boolean correctionMode) {
		super(task, "tt-eh-task", components.summary("Teilaufgabe", title, pointBadge), pointBadge, requirements);
		this.title = title;
		this.handler = handler;
		this.savedTitle = task.title();
		components.trackDirty(title);
		final Button addRequirement = components.commandButton("Anforderung hinzufügen", VaadinIcon.PLUS,
				event -> handler.addRequirement(task));
		final Button delete = components.deleteButton("Teilaufgabe löschen?", () -> handler.delete(task));
		if (correctionMode) {
			components.lockCorrectionModeAction(addRequirement);
			components.lockCorrectionModeAction(delete);
		}
		addToBody(editorBlockWithMoveButtons(components, siblings, handler, List.of(addRequirement),
				List.of(collapseState.toggleButton(descendantKeys), delete), correctionMode));
		addToBody(requirements);
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

	interface Handler extends LoeTitledSectionHandler<LoeTask> {

		void addRequirement(LoeTask task);
	}
}
