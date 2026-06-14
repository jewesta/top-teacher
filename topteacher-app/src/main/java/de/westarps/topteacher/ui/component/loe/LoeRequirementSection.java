package de.westarps.topteacher.ui.component.loe;

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

import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionParser;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class LoeRequirementSection extends Composite<VerticalLayout> implements LoeEditable {

	private final LoeRequirement requirement;
	private final Handler handler;
	private final MarkdownEditor descriptionEditor;
	private final IntegerField maxPoints;
	private final Button bonusButton;
	private final Component summary;
	private final Component description;
	private final Component actions;
	private final boolean correctionMode;
	private String savedDescriptionMarkdown;
	private int savedMaxPoints;
	private boolean savedBonus;
	private boolean bonus;

	LoeRequirementSection(final LoeRequirement requirement, final List<LoeRequirement> siblings,
			final LoeSectionComponents components, final Handler handler, final String requirementNumber,
			final boolean correctionMode) {
		this(requirement, components, handler,
				components.requirementDescriptionEditor(requirement.descriptionMarkdown(), "Beschreibung"),
				requirementNumber, maxPoints(requirement), bonusButton(), siblings, correctionMode);
	}

	private LoeRequirementSection(final LoeRequirement requirement, final LoeSectionComponents components,
			final Handler handler, final MarkdownEditor descriptionEditor, final String requirementNumber,
			final IntegerField maxPoints, final Button bonusButton, final List<LoeRequirement> siblings,
			final boolean correctionMode) {
		this.requirement = requirement;
		this.handler = handler;
		this.descriptionEditor = descriptionEditor;
		this.maxPoints = maxPoints;
		this.bonusButton = bonusButton;
		this.correctionMode = correctionMode;
		this.bonus = requirement.bonus();
		this.summary = components.requirementSummary(requirementNumber, bonusControl(bonusButton),
				headerControls(maxPoints));
		this.description = components.markdownBlock(descriptionEditor);
		this.savedDescriptionMarkdown = normalized(requirement.descriptionMarkdown());
		this.savedMaxPoints = requirement.maxPoints();
		this.savedBonus = requirement.bonus();
		updateBonusButton();
		maxPoints.setEnabled(!correctionMode);
		bonusButton.setEnabled(!correctionMode);
		components.trackDirty(descriptionEditor);
		components.trackDirty(maxPoints);
		bonusButton.addClickListener(event -> {
			bonus = !bonus;
			updateBonusButton();
			components.updateDirty();
		});
		final Button delete = components.deleteButton("Anforderung löschen?", () -> handler.delete(requirement));
		if (correctionMode) {
			components.lockCorrectionModeAction(delete);
		}
		this.actions = components.actionRow(components.actionComponentsWithMoveButtons(siblings, requirement, handler,
				List.of(), List.of(delete), correctionMode));
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

	LoeRequirement requirement() {
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
		if (correctionMode && (savedMaxPoints != maxPointsValue || savedBonus != bonusValue
				|| !criterionKeys(savedDescriptionMarkdown).equals(criterionKeys(descriptionMarkdown)))) {
			Notification.show(LoeSectionComponents.CORRECTION_MODE_TOOLTIP);
			return false;
		}
		handler.save(requirement, descriptionMarkdown, maxPointsValue, bonusValue);
		savedDescriptionMarkdown = descriptionMarkdown;
		savedMaxPoints = maxPointsValue;
		savedBonus = bonusValue;
		return true;
	}

	private static IntegerField maxPoints(final LoeRequirement requirement) {
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

	private static HorizontalLayout headerControls(final IntegerField maxPoints) {
		final HorizontalLayout controls = new HorizontalLayout(maxPointsControl(maxPoints));
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

	private List<String> criterionKeys(final String descriptionMarkdown) {
		return LoeCriterionParser.parse(requirement.id(), descriptionMarkdown).stream().map(LoeCriterion::criterionKey)
				.toList();
	}

	interface Handler extends LoeSectionHandler<LoeRequirement> {

		void save(LoeRequirement requirement, String descriptionMarkdown, int maxPoints, boolean bonus);
	}
}
