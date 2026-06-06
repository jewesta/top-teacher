package de.westarps.topteacher.ui.component;

import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;

import de.westarps.topteacher.model.EhRequirement;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class EhRequirementSection extends Composite<VerticalLayout> implements EhEditable {

	private final EhRequirement requirement;
	private final Handler handler;
	private final MarkdownEditor descriptionEditor;
	private final IntegerField maxPoints;
	private final Button bonusButton;
	private final Component summary;
	private final Component description;
	private final Component actions;
	private String savedDescriptionMarkdown;
	private int savedMaxPoints;
	private boolean savedBonus;
	private boolean bonus;

	EhRequirementSection(final EhRequirement requirement, final List<EhRequirement> siblings,
			final EhSectionComponents components, final Handler handler, final String requirementNumber) {
		this(requirement, components, handler,
				components.requirementDescriptionEditor(requirement.descriptionMarkdown(), "Beschreibung"),
				requirementNumber, maxPoints(requirement), bonusButton(), siblings);
	}

	private EhRequirementSection(final EhRequirement requirement, final EhSectionComponents components,
			final Handler handler, final MarkdownEditor descriptionEditor, final String requirementNumber,
			final IntegerField maxPoints, final Button bonusButton, final List<EhRequirement> siblings) {
		this.requirement = requirement;
		this.handler = handler;
		this.descriptionEditor = descriptionEditor;
		this.maxPoints = maxPoints;
		this.bonusButton = bonusButton;
		this.bonus = requirement.bonus();
		this.summary = components.requirementSummary(requirementNumber, headerControls(bonusButton, maxPoints));
		this.description = components.markdownBlock("Beschreibung", descriptionEditor);
		this.savedDescriptionMarkdown = normalized(requirement.descriptionMarkdown());
		this.savedMaxPoints = requirement.maxPoints();
		this.savedBonus = requirement.bonus();
		updateBonusButton();
		components.trackDirty(descriptionEditor);
		components.trackDirty(maxPoints);
		bonusButton.addClickListener(event -> {
			bonus = !bonus;
			updateBonusButton();
			components.updateDirty();
		});
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
				|| !Objects.equals(savedMaxPoints, maxPoints.getValue()) || savedBonus != bonus;
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
		final boolean bonusValue = bonus;
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

	private static Button bonusButton() {
		final Icon star = VaadinIcon.STAR.create();

		final Button bonusButton = new Button(star);
		bonusButton.addClassName("tt-eh-bonus-toggle");
		bonusButton.addThemeVariants(ButtonVariant.LUMO_ICON);
		bonusButton.getElement().setAttribute("data-action", "toggle-bonus");
		bonusButton.setAriaLabel("Sternchen-Aufgabe");
		bonusButton.setTooltipText("Sternchen-Aufgabe / Bonusaufgabe");
		stopSummaryToggle(bonusButton);
		return bonusButton;
	}

	private void updateBonusButton() {
		bonusButton.setClassName("tt-eh-bonus-toggle-active", bonus);
		bonusButton.getElement().setAttribute("aria-pressed", String.valueOf(bonus));
	}

	private static HorizontalLayout headerControls(final Button bonusButton, final IntegerField maxPoints) {
		final HorizontalLayout controls = new HorizontalLayout(bonusControl(bonusButton), maxPointsControl(maxPoints));
		controls.addClassName("tt-eh-requirement-header-controls");
		controls.setPadding(false);
		controls.setSpacing(false);
		return controls;
	}

	private static HorizontalLayout bonusControl(final Button bonusButton) {
		final HorizontalLayout control = new HorizontalLayout(bonusButton);
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
