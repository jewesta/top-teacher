package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.ui.component.ActionToolbar;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownViewer;

final class LoeSectionComponents {

	static final String CORRECTION_MODE_TOOLTIP = "Korrekturmodus: Ergebnisse vorhanden. Struktur, Punkte und Kriterien sind gesperrt.";

	private final LoeSaveController saveController;
	private final ConfirmDialog discardConfirmation = new ConfirmDialog();
	private Runnable valueChangeHandler = () -> {
	};
	private Runnable pointsChangeHandler = () -> {
	};

	LoeSectionComponents(final LoeSaveController saveController) {
		this.saveController = saveController;
		TopTeacherDialogs.configureDiscardConfirmation(discardConfirmation, "Änderungen verwerfen?", () -> {
			discardConfirmation.close();
			saveController.discard();
		});
	}

	void setValueChangeHandler(final Runnable valueChangeHandler) {
		this.valueChangeHandler = valueChangeHandler == null ? () -> {
		} : valueChangeHandler;
	}

	void setPointsChangeHandler(final Runnable pointsChangeHandler) {
		this.pointsChangeHandler = pointsChangeHandler == null ? () -> {
		} : pointsChangeHandler;
	}

	TextField summaryTitleField(final String value) {
		final TextField field = new TextField();
		field.addClassName("tt-eh-summary-title-field");
		field.setValue(value);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		field.setWidthFull();
		field.getElement().setAttribute("aria-label", "Titel");
		field.addAttachListener(event -> field.getElement().executeJs("""
			this.addEventListener('click', event => event.stopPropagation());
			this.addEventListener('keydown', event => event.stopPropagation());
			"""));
		return field;
	}

	Span summaryTitle(final String value) {
		final Span title = new Span(value);
		title.addClassName("tt-eh-summary-title");
		return title;
	}

	MarkdownEditor markdownEditor(final String value, final String placeholder) {
		final MarkdownEditor editor = configureMarkdownEditor(new MarkdownEditor(value == null ? "" : value), placeholder);
		addFileDropNotification(editor, "Hier können keine Dateien eingefügt werden. Bilder kannst Du im Material hinzufügen.");
		return editor;
	}

	MaterialMarkdownEditor materialMarkdownEditor(final String value, final String placeholder,
			final Collection<LoeMaterialImage> images) {
		final MaterialMarkdownEditor editor = (MaterialMarkdownEditor) configureMarkdownEditor(
				new MaterialMarkdownEditor(value == null ? "" : value, images), placeholder);
		editor.setHeight("24rem");
		return editor;
	}

	private MarkdownEditor configureMarkdownEditor(final MarkdownEditor editor, final String placeholder) {
		editor.addClassName("tt-markdown-editor");
		editor.setPlaceholder(placeholder);
		editor.setWidthFull();
		editor.setHeight("14rem");
		return editor;
	}

	MarkdownEditor categoryCommentEditor(final String value, final String placeholder) {
		final MarkdownEditor editor = markdownEditor(value, placeholder);
		editor.setHeight("7rem");
		return editor;
	}

	MarkdownEditor requirementDescriptionEditor(final String value, final String placeholder) {
		final MarkdownEditor editor = configureMarkdownEditor(
				new CriterionMarkdownEditor(value == null ? "" : value), placeholder);
		addFileDropNotification(editor, "Hier können keine Dateien eingefügt werden. Bilder kannst Du im Material hinzufügen.");
		return editor;
	}

	Component markdownBlock(final String label, final MarkdownEditor editor) {
		final Span editorLabel = new Span(label);
		editorLabel.addClassName("tt-field-label");

		final VerticalLayout block = new VerticalLayout(editorLabel, editor);
		block.addClassName("tt-markdown-block");
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	Component markdownBlock(final MarkdownEditor editor) {
		final VerticalLayout block = new VerticalLayout(editor);
		block.addClassName("tt-markdown-block");
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	Component markdownViewBlock(final String label, final String value) {
		final MarkdownViewer viewer = new MarkdownViewer(value == null ? "" : value);
		addFileDropNotification(viewer, "Hier können keine Dateien eingefügt werden. Bilder kannst Du im Material hinzufügen.");
		return markdownViewerBlock(label, viewer);
	}

	Component materialMarkdownViewBlock(final String label, final String value,
			final Collection<LoeMaterialImage> images) {
		final MarkdownViewer viewer = new MaterialMarkdownViewer(value == null ? "" : value, images);
		addFileDropNotification(viewer, "Material ist gesperrt und kann nicht mehr geändert werden.");
		return markdownViewerBlock(label, viewer);
	}

	private Component markdownViewerBlock(final String label, final MarkdownViewer viewer) {
		viewer.addClassName("tt-markdown-viewer");
		viewer.setWidthFull();
		final Span viewerLabel = new Span(label);
		viewerLabel.addClassName("tt-field-label");
		final VerticalLayout block = new VerticalLayout(viewerLabel, viewer);
		block.addClassName("tt-markdown-block");
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	private static void addFileDropNotification(final Component component, final String message) {
		component.getElement().addEventListener("markdown-extension-event", event -> {
			if ("file-drop".equals(event.getEventData().path("event.detail.name").asString(""))) {
				com.vaadin.flow.component.notification.Notification.show(message);
			}
		}).addEventData("event.detail.name");
	}

	Component summary(final String type, final Component title, final Component... controls) {
		final Span typeLabel = new Span(type);
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title);
		summary.add(controls);
		summary.addClassName("tt-eh-summary");
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	Component partSummary(final Component title, final Component percentageBadge, final Component pointBadge) {
		final Span typeLabel = new Span("Klausurteil");
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title, percentageBadge, pointBadge);
		summary.addClassNames("tt-eh-summary", "tt-eh-summary-with-percentage");
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	Component requirementSummary(final String requirementNumber, final Component bonusControl,
			final Component pointControl) {
		final Span typeLabel = new Span("Anforderung " + requirementNumber);
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, bonusControl, pointControl);
		summary.addClassName("tt-eh-summary");
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	LoePointBadge pointBadge(final String label, final Supplier<LoePoints> pointsSupplier) {
		return new LoePointBadge(label, pointsSupplier);
	}

	LoePercentageBadge percentageBadge(final Supplier<Integer> percentageSupplier) {
		return new LoePercentageBadge(percentageSupplier);
	}

	LoeWholePointStepper wholePointStepper(final String label, final int value, final boolean readOnly,
			final IntConsumer changeHandler) {
		final LoeWholePointStepper stepper = new LoeWholePointStepper(label);
		stepper.setValue(value);
		stepper.setReadOnly(readOnly);
		stepper.setChangeHandler(updatedValue -> {
			stepper.setValue(updatedValue);
			if (changeHandler != null) {
				changeHandler.accept(updatedValue);
			}
		});
		return stepper;
	}

	Button saveButton() {
		final Button button = Buttons.save(event -> saveController.save());
		button.addThemeVariants(ButtonVariant.LUMO_SMALL);
		return saveController.registerSave(button);
	}

	Button discardButton() {
		final Button button = Buttons.dialogOpener("Verwerfen", VaadinIcon.ROTATE_LEFT,
				event -> discardConfirmation.open());
		button.addThemeVariants(ButtonVariant.LUMO_SMALL);
		return saveController.register(button);
	}

	ConfirmDialog discardConfirmation() {
		return discardConfirmation;
	}

	Button commandButton(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = icon == VaadinIcon.PLUS ? Buttons.add(text, listener) : Buttons.command(text, icon, listener);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL);
		return button;
	}

	Button iconButton(final String label, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = new Button(icon.create(), listener);
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		return button;
	}

	<T> Button moveButton(final String label, final int offset, final List<T> siblings, final T item,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = iconButton(label, offset < 0 ? VaadinIcon.ARROW_UP : VaadinIcon.ARROW_DOWN, listener);
		final int index = siblings.indexOf(item);
		button.setEnabled(index >= 0 && index + offset >= 0 && index + offset < siblings.size());
		return button;
	}

	<T> Button moveButton(final String label, final int offset, final List<T> siblings, final T item,
			final ComponentEventListener<ClickEvent<Button>> listener, final boolean correctionMode) {
		final Button button = moveButton(label, offset, siblings, item, listener);
		applyCorrectionModeLock(button, correctionMode);
		return button;
	}

	Button deleteButton(final String confirmationHeader, final Runnable deleteAction) {
		final Button button = Buttons.deleteIcon("Löschen", confirmationHeader, deleteAction);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		return button;
	}

	Component editorBlock(final Component... actionComponents) {
		return editorBlock(List.of(actionComponents));
	}

	Component editorBlock(final Collection<? extends Component> actionComponents) {
		final VerticalLayout layout = new VerticalLayout(actionRow(actionComponents));
		layout.addClassName("tt-eh-editor-block");
		layout.setPadding(false);
		layout.setWidthFull();
		return layout;
	}

	HorizontalLayout actionRow(final Component... actionComponents) {
		return actionRow(List.of(actionComponents));
	}

	HorizontalLayout actionRow(final Collection<? extends Component> actionComponents) {
		final HorizontalLayout actions = new ActionToolbar();
		actionComponents.forEach(actions::add);
		actions.addClassName("tt-eh-actions");
		actions.setAlignItems(HorizontalLayout.Alignment.CENTER);
		actions.setPadding(false);
		return actions;
	}

	<T> List<Component> actionComponentsWithMoveButtons(final List<T> siblings, final T item,
			final LoeSectionHandler<T> handler, final Collection<? extends Component> leadingActions,
			final Collection<? extends Component> trailingActions) {
		return actionComponentsWithMoveButtons(siblings, item, handler, leadingActions, trailingActions, false);
	}

	<T> List<Component> actionComponentsWithMoveButtons(final List<T> siblings, final T item,
			final LoeSectionHandler<T> handler, final Collection<? extends Component> leadingActions,
			final Collection<? extends Component> trailingActions, final boolean correctionMode) {
		final List<Component> actionComponents = new ArrayList<>();
		actionComponents.addAll(leadingActions);
		actionComponents
				.add(moveButton("Nach oben", -1, siblings, item, event -> handler.move(item, -1), correctionMode));
		actionComponents
				.add(moveButton("Nach unten", 1, siblings, item, event -> handler.move(item, 1), correctionMode));
		actionComponents.addAll(trailingActions);
		return actionComponents;
	}

	void lockCorrectionModeAction(final Component component) {
		applyCorrectionModeLock(component, true);
	}

	private void applyCorrectionModeLock(final Component component, final boolean correctionMode) {
		if (!correctionMode || !(component instanceof Button button)) {
			return;
		}
		button.setEnabled(false);
		button.setTooltipText(CORRECTION_MODE_TOOLTIP);
	}

	void trackDirty(final HasValue<?, ?> field) {
		field.addValueChangeListener(event -> updateDirty());
	}

	void trackPoints(final HasValue<?, ?> field) {
		field.addValueChangeListener(event -> updatePoints());
	}

	void updatePoints() {
		pointsChangeHandler.run();
		updateDirty();
	}

	void updateDirty() {
		valueChangeHandler.run();
		saveController.update();
	}
}
