package de.westarps.topteacher.ui.component;

import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;

import de.westarps.topteacher.model.EhRequirement;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownExtension;

final class EhRequirementSection extends Composite<VerticalLayout> implements EhEditable {

	private final EhRequirement requirement;
	private final Handler handler;
	private final MarkdownEditor descriptionEditor;
	private final IntegerField maxPoints;
	private final Checkbox bonus;
	private final Component summary;
	private final Component description;
	private final Component actions;
	private String savedDescriptionMarkdown;
	private int savedMaxPoints;
	private boolean savedBonus;

	EhRequirementSection(final EhRequirement requirement, final List<EhRequirement> siblings,
			final EhSectionComponents components, final Handler handler, final String requirementNumber) {
		this(requirement, components, handler,
				components.markdownEditor(requirement.descriptionMarkdown(), "Beschreibung",
						MarkdownExtension.EH_CRITERIA),
				requirementNumber, maxPoints(requirement), bonus(requirement), siblings);
	}

	private EhRequirementSection(final EhRequirement requirement, final EhSectionComponents components,
			final Handler handler, final MarkdownEditor descriptionEditor, final String requirementNumber,
			final IntegerField maxPoints, final Checkbox bonus, final List<EhRequirement> siblings) {
		this.requirement = requirement;
		this.handler = handler;
		this.descriptionEditor = descriptionEditor;
		this.maxPoints = maxPoints;
		this.bonus = bonus;
		this.summary = components.requirementSummary(requirementNumber, headerControls(bonus, maxPoints));
		this.description = components.markdownBlock("Beschreibung", descriptionEditor);
		this.savedDescriptionMarkdown = normalized(requirement.descriptionMarkdown());
		this.savedMaxPoints = requirement.maxPoints();
		this.savedBonus = requirement.bonus();
		components.trackDirty(descriptionEditor);
		components.trackDirty(maxPoints);
		components.trackDirty(bonus);
		this.actions = components.actionRow(components.actionComponentsWithMoveButtons(siblings, requirement, handler,
				List.of(components.saveButton()),
				List.of(components.deleteButton(event -> handler.delete(requirement)))));
		getContent();
	}

	@Override
	protected VerticalLayout initContent() {
		final VerticalLayout editor = new VerticalLayout(summary, description, actions);
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

	private static IntegerField maxPoints(final EhRequirement requirement) {
		final IntegerField maxPoints = new IntegerField();
		maxPoints.addClassName("tt-eh-requirement-points-field");
		maxPoints.getElement().setAttribute("aria-label", "Max. Punkte");
		maxPoints.setMin(0);
		maxPoints.setStepButtonsVisible(true);
		maxPoints.setValue(requirement.maxPoints());
		stopSummaryToggle(maxPoints);
		return maxPoints;
	}

	private static Checkbox bonus(final EhRequirement requirement) {
		final Checkbox bonus = new Checkbox();
		bonus.getElement().setAttribute("aria-label", "Bonus");
		bonus.setValue(requirement.bonus());
		stopSummaryToggle(bonus);
		return bonus;
	}

	private static HorizontalLayout headerControls(final Checkbox bonus, final IntegerField maxPoints) {
		final HorizontalLayout controls = new HorizontalLayout(bonusControl(bonus), maxPointsControl(maxPoints));
		controls.addClassName("tt-eh-requirement-header-controls");
		controls.setPadding(false);
		controls.setSpacing(false);
		return controls;
	}

	private static HorizontalLayout bonusControl(final Checkbox bonus) {
		final Span label = new Span("Bonus");
		label.addClassName("tt-field-label");

		final HorizontalLayout control = new HorizontalLayout(label, bonus);
		control.addClassName("tt-eh-requirement-bonus-control");
		control.setPadding(false);
		control.setSpacing(false);
		return control;
	}

	private static HorizontalLayout maxPointsControl(final IntegerField maxPoints) {
		final Span label = new Span("Max. Punkte");
		label.addClassName("tt-field-label");

		final HorizontalLayout control = new HorizontalLayout(label, maxPoints);
		control.addClassName("tt-eh-requirement-points-control");
		control.setPadding(false);
		control.setSpacing(false);
		return control;
	}

	private static void stopSummaryToggle(final Component component) {
		component.addAttachListener(event -> component.getElement().executeJs("""
				this.addEventListener('click', event => event.stopPropagation());
				this.addEventListener('keydown', event => event.stopPropagation());
				"""));
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
