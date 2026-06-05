package de.topteacher.ui.component;

import java.util.List;
import java.util.function.Supplier;

import com.flowingcode.vaadin.addons.markdown.MarkdownEditor;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;

import de.topteacher.model.EhRequirement;

final class EhRequirementSection extends Composite<VerticalLayout> {

	private final EhRequirement requirement;
	private final Component summary;
	private final Component description;
	private final Component fields;
	private final Component actions;

	EhRequirementSection(final EhRequirement requirement, final List<EhRequirement> siblings,
			final EhSectionComponents components, final Handler handler, final String requirementNumber,
			final Supplier<String> pointsLabelSupplier) {
		this(requirement, components, handler, components.markdownEditor(requirement.descriptionMarkdown(), "Beschreibung"),
				requirementNumber, pointsLabelSupplier, siblings);
	}

	private EhRequirementSection(final EhRequirement requirement, final EhSectionComponents components,
			final Handler handler, final MarkdownEditor descriptionEditor, final String requirementNumber,
			final Supplier<String> pointsLabelSupplier, final List<EhRequirement> siblings) {
		this.requirement = requirement;
		this.summary = components.requirementSummary(requirementNumber(requirementNumber), pointsLabelSupplier);
		this.description = components.markdownBlock("Beschreibung", descriptionEditor);
		final IntegerField maxPoints = maxPoints(requirement);
		final Checkbox bonus = new Checkbox("Bonusaufgabe", requirement.bonus());
		this.fields = components.requirementFields(maxPoints, bonus);
		this.actions = components.actionRow(components.saveButton(event -> save(requirement, descriptionEditor, maxPoints,
				bonus, components, handler)),
				components.moveButton("Nach oben", -1, siblings, requirement, event -> handler.move(requirement, -1)),
				components.moveButton("Nach unten", 1, siblings, requirement, event -> handler.move(requirement, 1)),
				components.deleteButton(event -> handler.delete(requirement)));
		getContent();
	}

	@Override
	protected VerticalLayout initContent() {
		final VerticalLayout editor = new VerticalLayout(summary, description, fields, actions);
		editor.addClassName("tt-eh-requirement");
		editor.setPadding(false);
		editor.setWidthFull();
		return editor;
	}

	EhRequirement requirement() {
		return requirement;
	}

	private static Span requirementNumber(final String requirementNumber) {
		final Span number = new Span(requirementNumber);
		number.addClassName("tt-eh-summary-title");
		return number;
	}

	private static IntegerField maxPoints(final EhRequirement requirement) {
		final IntegerField maxPoints = new IntegerField("Max. Punkte");
		maxPoints.setMin(0);
		maxPoints.setStepButtonsVisible(true);
		maxPoints.setValue(requirement.maxPoints());
		maxPoints.setWidth("10rem");
		return maxPoints;
	}

	private static void save(final EhRequirement requirement, final MarkdownEditor description,
			final IntegerField maxPoints, final Checkbox bonus, final EhSectionComponents components,
			final Handler handler) {
		if (maxPoints.getValue() == null || maxPoints.getValue() < 0) {
			Notification.show("Max. Punkte müssen 0 oder größer sein.");
			return;
		}
		handler.save(requirement, components.value(description), maxPoints.getValue(), bonus.getValue());
	}

	interface Handler {

		void save(EhRequirement requirement, String descriptionMarkdown, int maxPoints, boolean bonus);

		void move(EhRequirement requirement, int offset);

		void delete(EhRequirement requirement);
	}
}
