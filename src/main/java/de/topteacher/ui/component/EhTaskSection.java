package de.topteacher.ui.component;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.model.EhTask;

final class EhTaskSection extends AbstractEhSection<EhTask> {

	EhTaskSection(final EhTask task, final List<EhTask> siblings,
			final Collection<? extends Component> requirements, final EhSectionComponents components,
			final EhCollapseState collapseState, final Handler handler, final Supplier<EhPoints> pointsSupplier,
			final List<String> descendantKeys) {
		this(task, siblings, requirements, components, collapseState, handler, pointsSupplier, descendantKeys,
				components.summaryTitleField(task.title()));
	}

	private EhTaskSection(final EhTask task, final List<EhTask> siblings,
			final Collection<? extends Component> requirements, final EhSectionComponents components,
			final EhCollapseState collapseState, final Handler handler, final Supplier<EhPoints> pointsSupplier,
			final List<String> descendantKeys, final TextField title) {
		super(task, "tt-eh-task", components.summary("Teilaufgabe", title, pointsSupplier));
		title.addValueChangeListener(event -> {
			if (event.isFromClient()) {
				saveTitle(task, title, handler);
			}
		});
		addToBody(components.editorBlock(components.saveButton(event -> saveTitle(task, title, handler)),
				components.commandButton("Anforderung hinzufügen", VaadinIcon.PLUS, event -> handler.addRequirement(task)),
				components.moveButton("Nach oben", -1, siblings, task, event -> handler.move(task, -1)),
				components.moveButton("Nach unten", 1, siblings, task, event -> handler.move(task, 1)),
				collapseState.toggleButton(descendantKeys), components.deleteButton(event -> handler.delete(task))));
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

	interface Handler {

		void saveTitle(EhTask task, String title);

		void addRequirement(EhTask task);

		void move(EhTask task, int offset);

		void delete(EhTask task);
	}
}
