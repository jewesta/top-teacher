package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class ExamTaskSection extends AbstractLoeSection<LoeTask> {

	private final TextField title;
	private final MarkdownEditor description;
	private final LoeWholePointStepper maxPoints;
	private final Handler handler;
	private String savedTitle;
	private String savedDescriptionMarkdown;
	private int savedMaxPoints;

	ExamTaskSection(final LoeTask task, final List<LoeTask> siblings, final LoeSectionComponents components,
			final Handler handler, final boolean locked) {
		this(task, siblings, components, handler, fields(task, components, locked), locked);
	}

	private ExamTaskSection(final LoeTask task, final List<LoeTask> siblings, final LoeSectionComponents components,
			final Handler handler, final Fields fields, final boolean locked) {
		super(task, "tt-eh-task", components.summary("Aufgabe", fields.title(), fields.maxPoints()), null, List.of());
		this.title = fields.title();
		this.description = fields.description();
		this.maxPoints = fields.maxPoints();
		this.handler = handler;
		this.savedTitle = task.title();
		this.savedDescriptionMarkdown = normalized(task.descriptionMarkdown());
		this.savedMaxPoints = task.maxPoints();
		title.setReadOnly(locked);
		description.setEnabled(!locked);
		components.trackDirty(title);
		components.trackDirty(description);
		final Button delete = components.deleteButton("Aufgabe löschen?", () -> handler.delete(task));
		if (locked) {
			components.lockCorrectionModeAction(delete);
		}
		final Component descriptionBlock = locked
				? components.markdownViewBlock("Aufgabentext", task.descriptionMarkdown())
				: components.markdownBlock("Aufgabentext", description);
		addToBody(editorBlockWithMoveButtons(components, siblings, handler, List.of(), List.of(delete), locked),
				descriptionBlock);
	}

	private static Fields fields(final LoeTask task, final LoeSectionComponents components, final boolean locked) {
		final TextField title = components.summaryTitleField(task.title());
		final MarkdownEditor description = components.markdownEditor(task.descriptionMarkdown(), "Aufgabentext");
		final LoeWholePointStepper maxPoints = components.wholePointStepper("Max. Punkte", task.maxPoints(), locked,
				ignored -> components.updatePoints());
		return new Fields(title, description, maxPoints);
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
		return maxPoints.getValue();
	}

	@Override
	protected boolean saveSection() {
		if (title.getValue() == null || title.getValue().isBlank()) {
			Notification.show("Titel ist erforderlich.");
			return false;
		}
		if (maxPoints.getValue() < 0) {
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

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}

	interface Handler extends LoeSectionHandler<LoeTask> {

		void save(LoeTask task, String title, String descriptionMarkdown, int maxPoints);
	}

	private record Fields(TextField title, MarkdownEditor description, LoeWholePointStepper maxPoints) {
	}
}

