package de.topteacher.ui.component;

import java.util.List;
import java.util.Objects;
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

final class EhRequirementSection extends Composite<VerticalLayout> implements EhEditable {

	private final EhRequirement requirement;
	private final Handler handler;
	private final EhRequirementPointBadge pointsBadge;
	private final MarkdownEditor descriptionEditor;
	private final IntegerField maxPoints;
	private final Checkbox bonus;
	private final Component summary;
	private final Component description;
	private final Component fields;
	private final Component actions;
	private String savedDescriptionMarkdown;
	private int savedMaxPoints;
	private boolean savedBonus;

	EhRequirementSection(final EhRequirement requirement, final List<EhRequirement> siblings,
			final EhSectionComponents components, final Handler handler, final String requirementNumber,
			final Supplier<String> pointsLabelSupplier) {
		this(requirement, components, handler, components.markdownEditor(requirement.descriptionMarkdown(), "Beschreibung"),
				requirementNumber, components.requirementPointBadge(pointsLabelSupplier), siblings);
	}

	private EhRequirementSection(final EhRequirement requirement, final EhSectionComponents components,
			final Handler handler, final MarkdownEditor descriptionEditor, final String requirementNumber,
			final EhRequirementPointBadge pointsBadge, final List<EhRequirement> siblings) {
		this.requirement = requirement;
		this.handler = handler;
		this.pointsBadge = pointsBadge;
		this.descriptionEditor = descriptionEditor;
		this.summary = components.requirementSummary(requirementNumber(requirementNumber), pointsBadge);
		this.description = components.markdownBlock("Beschreibung", descriptionEditor);
		this.maxPoints = maxPoints(requirement);
		this.bonus = new Checkbox("Bonusaufgabe", requirement.bonus());
		this.savedDescriptionMarkdown = normalized(requirement.descriptionMarkdown());
		this.savedMaxPoints = requirement.maxPoints();
		this.savedBonus = requirement.bonus();
		components.trackDirty(descriptionEditor);
		components.trackDirty(maxPoints);
		components.trackDirty(bonus);
		this.fields = components.requirementFields(maxPoints, bonus);
		this.actions = components.actionRow(components.actionComponentsWithMoveButtons(siblings, requirement, handler,
				List.of(components.saveButton()),
				List.of(components.deleteButton(event -> handler.delete(requirement)))));
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

	@Override
	public void refreshBadges() {
		pointsBadge.refreshBadges();
	}

	@Override
	public boolean isDirty() {
		return !Objects.equals(savedDescriptionMarkdown, componentsValue(descriptionEditor))
				|| !Objects.equals(savedMaxPoints, maxPoints.getValue()) || savedBonus != bonus.getValue();
	}

	@Override
	public boolean save() {
		if (!isDirty()) {
			return true;
		}
		if (maxPoints.getValue() == null || maxPoints.getValue() < 0) {
			Notification.show("Max. Punkte müssen 0 oder größer sein.");
			return false;
		}
		final String descriptionMarkdown = componentsValue(descriptionEditor);
		final int maxPointsValue = maxPoints.getValue();
		final boolean bonusValue = bonus.getValue();
		handler.save(requirement, descriptionMarkdown, maxPointsValue, bonusValue);
		savedDescriptionMarkdown = descriptionMarkdown;
		savedMaxPoints = maxPointsValue;
		savedBonus = bonusValue;
		return true;
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

	private static String componentsValue(final MarkdownEditor editor) {
		return normalized(editor.getValue());
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}

	interface Handler extends EhSectionHandler<EhRequirement> {

		void save(EhRequirement requirement, String descriptionMarkdown, int maxPoints, boolean bonus);
	}
}
