package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class ExamTaskSection extends AbstractLoeSection<LoeTask> {

	private final TextField title;
	private final MarkdownEditor description;
	private final IntegerField maxPoints;
	private final Handler handler;
	private String savedTitle;
	private String savedDescriptionMarkdown;
	private int savedMaxPoints;

	ExamTaskSection(final LoeTask task, final List<LoeTask> siblings, final LoeSectionComponents components,
			final Handler handler, final boolean locked) {
		this(task, siblings, components, handler, fields(task, components), locked);
	}

	private ExamTaskSection(final LoeTask task, final List<LoeTask> siblings, final LoeSectionComponents components,
			final Handler handler, final Fields fields, final boolean locked) {
		super(task, "tt-eh-task", components.summary("Aufgabe", fields.title(), fields.pointBadge()),
				fields.pointBadge(), List.of());
		this.title = fields.title();
		this.description = fields.description();
		this.maxPoints = fields.maxPoints();
		this.handler = handler;
		this.savedTitle = task.title();
		this.savedDescriptionMarkdown = normalized(task.descriptionMarkdown());
		this.savedMaxPoints = task.maxPoints();
		title.setReadOnly(locked);
		description.setEnabled(!locked);
		maxPoints.setReadOnly(locked);
		components.trackDirty(title);
		components.trackDirty(description);
		components.trackPoints(maxPoints);
		final Button delete = components.deleteButton("Aufgabe löschen?", () -> handler.delete(task));
		if (locked) {
			components.lockCorrectionModeAction(delete);
		}
		final Component descriptionBlock = locked
				? components.markdownViewBlock("Aufgabentext", task.descriptionMarkdown())
				: components.markdownBlock("Aufgabentext", description);
		addToBody(descriptionBlock, pointsControl(maxPoints),
				editorBlockWithMoveButtons(components, siblings, handler, List.of(), List.of(delete), locked));
	}

	private static Fields fields(final LoeTask task, final LoeSectionComponents components) {
		final TextField title = components.summaryTitleField(task.title());
		final MarkdownEditor description = components.markdownEditor(task.descriptionMarkdown(), "Aufgabentext");
		final IntegerField maxPoints = maxPoints(task);
		final LoePointBadge pointBadge = components.pointBadge("Max.",
				() -> new LoePoints(maxPoints.getValue() == null ? 0 : maxPoints.getValue(), 0));
		return new Fields(title, description, maxPoints, pointBadge);
	}

	@Override
	protected boolean isSectionDirty() {
		return !Objects.equals(savedTitle, title.getValue())
				|| !Objects.equals(savedDescriptionMarkdown, normalized(description.getValue()))
				|| !Objects.equals(savedMaxPoints, maxPoints.getValue());
	}

	LoeTask task() {
		return item();
	}

	int pendingMaxPoints() {
		return maxPoints.getValue() == null ? 0 : maxPoints.getValue();
	}

	@Override
	protected boolean saveSection() {
		if (title.getValue() == null || title.getValue().isBlank()) {
			Notification.show("Titel ist erforderlich.");
			return false;
		}
		if (maxPoints.getValue() == null || maxPoints.getValue() < 0) {
			Notification.show("Max. Punkte müssen 0 oder größer sein.");
			return false;
		}
		final String descriptionMarkdown = normalized(description.getValue());
		handler.save(item(), title.getValue(), descriptionMarkdown, maxPoints.getValue());
		savedTitle = title.getValue();
		savedDescriptionMarkdown = descriptionMarkdown;
		savedMaxPoints = maxPoints.getValue();
		return true;
	}

	private static IntegerField maxPoints(final LoeTask task) {
		final IntegerField maxPoints = new IntegerField();
		maxPoints.addClassName("tt-eh-requirement-points-field");
		maxPoints.setMin(0);
		maxPoints.setStepButtonsVisible(true);
		maxPoints.setValue(task.maxPoints());
		return maxPoints;
	}

	private static HorizontalLayout pointsControl(final IntegerField maxPoints) {
		final Span label = new Span("Max. Punkte");
		label.addClassName("tt-field-label");
		final HorizontalLayout control = new HorizontalLayout(label, maxPoints);
		control.addClassName("tt-eh-requirement-points-control");
		control.setPadding(false);
		control.setSpacing(false);
		return control;
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}

	interface Handler extends LoeSectionHandler<LoeTask> {

		void save(LoeTask task, String title, String descriptionMarkdown, int maxPoints);
	}

	private record Fields(TextField title, MarkdownEditor description, IntegerField maxPoints,
			LoePointBadge pointBadge) {
	}
}

