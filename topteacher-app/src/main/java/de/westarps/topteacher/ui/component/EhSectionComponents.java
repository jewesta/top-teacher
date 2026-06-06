package de.westarps.topteacher.ui.component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.model.EhCriterionParser;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownTag;

final class EhSectionComponents {

	static final MarkdownTag CRITERION_TAG = MarkdownTag.nextNumber(EhCriterionParser.TAG_NAMESPACE,
			"Kriterium markieren");

	private final EhSaveController saveController;

	EhSectionComponents(final EhSaveController saveController) {
		this.saveController = saveController;
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

	MarkdownEditor markdownEditor(final String value, final String placeholder) {
		final MarkdownEditor editor = new MarkdownEditor(value == null ? "" : value);
		editor.addClassName("tt-markdown-editor");
		editor.setPlaceholder(placeholder);
		editor.setWidthFull();
		editor.setHeight("14rem");
		return editor;
	}

	MarkdownEditor requirementDescriptionEditor(final String value, final String placeholder) {
		final MarkdownEditor editor = markdownEditor(value, placeholder);
		editor.setTag(CRITERION_TAG);
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

	Component summary(final String type, final Component title, final Component pointBadge) {
		final Span typeLabel = new Span(type);
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title, pointBadge);
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

	Component requirementSummary(final String requirementNumber, final Component pointControl) {
		final Span typeLabel = new Span("Anforderung " + requirementNumber);
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, pointControl);
		summary.addClassName("tt-eh-summary");
		summary.setPadding(false);
		summary.setWidthFull();
		summary.setFlexGrow(1, typeLabel);
		return summary;
	}

	EhPointBadge pointBadge(final String label, final Supplier<EhPoints> pointsSupplier) {
		return new EhPointBadge(label, pointsSupplier);
	}

	EhPercentageBadge percentageBadge(final Supplier<Integer> percentageSupplier) {
		return new EhPercentageBadge(percentageSupplier);
	}

	Button saveButton() {
		final Button button = commandButton("Speichern", VaadinIcon.CHECK, event -> saveController.save());
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		return saveController.register(button);
	}

	Button commandButton(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = new Button(text, icon.create(), listener);
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

	Button deleteButton(final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = iconButton("Löschen", VaadinIcon.TRASH, listener);
		button.addThemeVariants(ButtonVariant.LUMO_ERROR);
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
		final HorizontalLayout actions = new HorizontalLayout();
		actionComponents.forEach(actions::add);
		actions.addClassName("tt-eh-actions");
		actions.setPadding(false);
		return actions;
	}

	<T> List<Component> actionComponentsWithMoveButtons(final List<T> siblings, final T item,
			final EhSectionHandler<T> handler, final Collection<? extends Component> leadingActions,
			final Collection<? extends Component> trailingActions) {
		final List<Component> actionComponents = new ArrayList<>();
		actionComponents.addAll(leadingActions);
		actionComponents.add(moveButton("Nach oben", -1, siblings, item, event -> handler.move(item, -1)));
		actionComponents.add(moveButton("Nach unten", 1, siblings, item, event -> handler.move(item, 1)));
		actionComponents.addAll(trailingActions);
		return actionComponents;
	}

	void trackDirty(final HasValue<?, ?> field) {
		field.addValueChangeListener(event -> saveController.update());
	}
}
