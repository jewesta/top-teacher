package de.topteacher.ui.component;

import java.util.List;
import java.util.function.Supplier;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.model.EhTask;

final class EhTaskSection extends AbstractEhSection<EhTask> {

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
		title.addValueChangeListener(event -> {
			if (event.isFromClient()) {
				saveTitle(task, title, handler);
			}
		});
		addToBody(editorBlockWithMoveButtons(components, siblings, handler,
				List.of(components.saveButton(event -> saveTitle(task, title, handler)),
						components.commandButton("Anforderung hinzufügen", VaadinIcon.PLUS,
								event -> handler.addRequirement(task))),
				List.of(collapseState.toggleButton(descendantKeys), components.deleteButton(event -> handler.delete(task)))));
		addToBody(requirements);
	}

	private static void saveTitle(final EhTask task, final TextField title, final Handler handler) {
		if (isBlank(title.getValue())) {
			Notification.show("Titel ist erforderlich.");
			title.setValue(task.title());
			return;
		}
		handler.saveTitle(task, title.getValue());
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	interface Handler extends EhTitledSectionHandler<EhTask> {

		void addRequirement(EhTask task);
	}
}
