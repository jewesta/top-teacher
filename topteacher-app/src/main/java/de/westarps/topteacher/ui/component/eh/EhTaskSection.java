package de.westarps.topteacher.ui.component.eh;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.eh.EhTask;

final class EhTaskSection extends AbstractEhSection<EhTask> {

	private final TextField title;
	private final Handler handler;
	private String savedTitle;

	EhTaskSection(final EhTask task, final List<EhTask> siblings, final List<EhRequirementSection> requirements,
			final EhSectionComponents components, final EhCollapseState collapseState, final Handler handler,
			final Supplier<EhPoints> pointsSupplier, final List<String> descendantKeys) {
		this(task, siblings, requirements, components, collapseState, handler, descendantKeys,
				components.summaryTitleField(task.title()), components.pointBadge("Summe", pointsSupplier));
	}

	private EhTaskSection(final EhTask task, final List<EhTask> siblings, final List<EhRequirementSection> requirements,
			final EhSectionComponents components, final EhCollapseState collapseState, final Handler handler,
			final List<String> descendantKeys, final TextField title, final EhPointBadge pointBadge) {
		super(task, "tt-eh-task", components.summary("Teilaufgabe", title, pointBadge), pointBadge, requirements);
		this.title = title;
		this.handler = handler;
		this.savedTitle = task.title();
		components.trackDirty(title);
		addToBody(editorBlockWithMoveButtons(components, siblings, handler,
				List.of(components.commandButton("Anforderung hinzufügen", VaadinIcon.PLUS,
						event -> handler.addRequirement(task))),
				List.of(collapseState.toggleButton(descendantKeys),
						components.deleteButton(event -> handler.delete(task)))));
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

	interface Handler extends EhTitledSectionHandler<EhTask> {

		void addRequirement(EhTask task);
	}
}
