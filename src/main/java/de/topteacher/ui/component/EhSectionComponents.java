package de.topteacher.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.flowingcode.vaadin.addons.markdown.MarkdownEditor;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

final class EhSectionComponents {

	private final List<PointBadge> pointBadges = new ArrayList<>();
	private final List<PercentageBadge> percentageBadges = new ArrayList<>();
	private final List<RequirementBadge> requirementBadges = new ArrayList<>();

	void clearBadges() {
		pointBadges.clear();
		percentageBadges.clear();
		requirementBadges.clear();
	}

	TextField summaryTitleField(final String value) {
		final TextField field = new TextField();
		field.addClassName("tt-eh-summary-title-field");
		field.setValue(value);
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

	Component markdownBlock(final String label, final MarkdownEditor editor) {
		final Span editorLabel = new Span(label);
		editorLabel.addClassName("tt-field-label");

		final VerticalLayout block = new VerticalLayout(editorLabel, editor);
		block.addClassName("tt-markdown-block");
		block.setPadding(false);
		block.setWidthFull();
		return block;
	}

	Component summary(final String type, final Component title, final Supplier<EhPoints> pointsSupplier) {
		final Span typeLabel = new Span(type);
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title, summaryBadge("Summe", pointsSupplier));
		summary.addClassName("tt-eh-summary");
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	Component partSummary(final Component title, final Supplier<Integer> percentageSupplier,
			final Supplier<EhPoints> pointsSupplier) {
		final Span typeLabel = new Span("Klausurteil");
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title, percentageBadge(percentageSupplier),
				summaryBadge("Summe", pointsSupplier));
		summary.addClassNames("tt-eh-summary", "tt-eh-summary-with-percentage");
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	Component requirementSummary(final Component title, final Supplier<String> pointsLabelSupplier) {
		final Span typeLabel = new Span("Anforderung");
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title, requirementPointsBadge(pointsLabelSupplier));
		summary.addClassName("tt-eh-summary");
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	Span summaryBadge(final String label, final Supplier<EhPoints> pointsSupplier) {
		final Span badge = new Span();
		badge.addClassName("tt-eh-points");
		pointBadges.add(new PointBadge(label, badge, pointsSupplier));
		updatePointBadge(label, badge, pointsSupplier.get());
		return badge;
	}

	Button saveButton(final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = commandButton("Speichern", VaadinIcon.CHECK, listener);
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		return button;
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
		final VerticalLayout layout = new VerticalLayout(actionRow(actionComponents));
		layout.addClassName("tt-eh-editor-block");
		layout.setPadding(false);
		layout.setWidthFull();
		return layout;
	}

	HorizontalLayout actionRow(final Component... actionComponents) {
		final HorizontalLayout actions = new HorizontalLayout(actionComponents);
		actions.addClassName("tt-eh-actions");
		actions.setPadding(false);
		return actions;
	}

	HorizontalLayout requirementFields(final IntegerField maxPoints, final Checkbox bonus) {
		final HorizontalLayout fields = new HorizontalLayout(maxPoints, bonus);
		fields.addClassName("tt-eh-requirement-fields");
		fields.setAlignItems(Alignment.END);
		fields.setPadding(false);
		return fields;
	}

	void refreshBadges() {
		pointBadges.forEach(pointBadge -> updatePointBadge(pointBadge.label(), pointBadge.badge(),
				pointBadge.pointsSupplier().get()));
		percentageBadges.forEach(percentageBadge -> updatePercentageBadge(percentageBadge.badge(),
				percentageBadge.percentageSupplier().get()));
		requirementBadges.forEach(requirementBadge -> updateRequirementBadge(requirementBadge.badge(),
				requirementBadge.pointsLabelSupplier().get()));
	}

	String value(final MarkdownEditor editor) {
		return editor.getValue() == null ? "" : editor.getValue();
	}

	private Span percentageBadge(final Supplier<Integer> percentageSupplier) {
		final Span badge = new Span();
		badge.addClassName("tt-eh-percentage");
		percentageBadges.add(new PercentageBadge(badge, percentageSupplier));
		updatePercentageBadge(badge, percentageSupplier.get());
		return badge;
	}

	private Span requirementPointsBadge(final Supplier<String> pointsLabelSupplier) {
		final Span badge = new Span();
		badge.addClassName("tt-eh-points");
		requirementBadges.add(new RequirementBadge(badge, pointsLabelSupplier));
		updateRequirementBadge(badge, pointsLabelSupplier.get());
		return badge;
	}

	private void updatePointBadge(final String label, final Span badge, final EhPoints points) {
		badge.setText(label + ": " + points.label());
	}

	private void updatePercentageBadge(final Span badge, final int percentage) {
		badge.setText(percentage + " %");
	}

	private void updateRequirementBadge(final Span badge, final String pointsLabel) {
		badge.setText(pointsLabel);
	}

	private record PointBadge(String label, Span badge, Supplier<EhPoints> pointsSupplier) {
	}

	private record PercentageBadge(Span badge, Supplier<Integer> percentageSupplier) {
	}

	private record RequirementBadge(Span badge, Supplier<String> pointsLabelSupplier) {
	}
}
