package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Locale;
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
import de.westarps.validate.TestResult;
import de.westarps.vaadin.animate.Animations;
import de.westarps.vaadin.animate.Effect;
import de.westarps.vaadin.animate.Speed;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class LoeRequirementSection extends Composite<VerticalLayout> implements LoeEditable {

	private final LoeRequirement requirement;
	private final Handler handler;
	private final MarkdownEditor descriptionEditor;
	private final IntegerField maxPoints;
	private final Button bonusButton;
	private final Span allocationMessage;
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
				requirementNumber, maxPoints(requirement), bonusButton(), allocationMessage(), siblings,
				correctionMode);
	}

	private LoeRequirementSection(final LoeRequirement requirement, final LoeSectionComponents components,
			final Handler handler, final MarkdownEditor descriptionEditor, final String requirementNumber,
			final IntegerField maxPoints, final Button bonusButton, final Span allocationMessage,
			final List<LoeRequirement> siblings, final boolean correctionMode) {
		this.requirement = requirement;
		this.handler = handler;
		this.descriptionEditor = descriptionEditor;
		this.maxPoints = maxPoints;
		this.bonusButton = bonusButton;
		this.allocationMessage = allocationMessage;
		this.correctionMode = correctionMode;
		this.bonus = requirement.bonus();
		this.summary = components.requirementSummary(requirementNumber, bonusControl(bonusButton),
				headerControls(allocationMessage, maxPoints));
		this.description = components.markdownBlock(descriptionEditor);
		this.savedDescriptionMarkdown = normalized(requirement.descriptionMarkdown());
		this.savedMaxPoints = requirement.maxPoints();
		this.savedBonus = requirement.bonus();
		updateBonusButton();
		maxPoints.setEnabled(!correctionMode);
		bonusButton.setEnabled(!correctionMode);
		components.trackDirty(descriptionEditor);
		components.trackPoints(maxPoints);
		bonusButton.addClickListener(event -> {
			bonus = !bonus;
			updateBonusButton();
			components.updatePoints();
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

	LoeRequirement pendingRequirement() {
		return new LoeRequirement(requirement.id(), requirement.taskId(), componentsValue(descriptionEditor),
				validMaxPoints() ? maxPoints.getValue() : 0, bonus, requirement.sortOrder());
	}

	boolean hasValidMaxPoints() {
		return validMaxPoints();
	}

	void emphasize() {
		Animations.playOnce(getContent(), Effect.PULSE, Speed.FASTER);
	}

	@Override
	public void refreshBadges() {
		// The requirement has no point badge; its message is refreshed from validation results.
	}

	void setCriterionValidation(final List<TestResult> results) {
		if (results.isEmpty()) {
			allocationMessage.setText("");
			allocationMessage.setVisible(false);
			allocationMessage.getElement().removeAttribute("data-severity");
			return;
		}
		final TestResult result = results.getFirst();
		allocationMessage.getElement().setAttribute("data-severity",
				result.severity().name().toLowerCase(Locale.ROOT));
		allocationMessage.setText(result.message());
		allocationMessage.setVisible(true);
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
				|| !criterionDefinitions(savedDescriptionMarkdown).equals(criterionDefinitions(descriptionMarkdown)))) {
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

	private boolean validMaxPoints() {
		return maxPoints.getValue() != null && maxPoints.getValue() >= 0;
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

	private static HorizontalLayout headerControls(final Span allocationMessage, final IntegerField maxPoints) {
		final HorizontalLayout controls = new HorizontalLayout(maxPointsControl(allocationMessage, maxPoints));
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

	private static HorizontalLayout maxPointsControl(final Span allocationMessage, final IntegerField maxPoints) {
		final Span label = new Span("Max. Punkte");
		label.addClassName("tt-field-label");

		final HorizontalLayout control = new HorizontalLayout(allocationMessage, label, maxPoints);
		control.addClassName("tt-eh-requirement-points-control");
		control.setPadding(false);
		control.setSpacing(false);
		return control;
	}

	private static Span allocationMessage() {
		final Span message = new Span();
		message.addClassName("tt-eh-requirement-allocation-message");
		message.setVisible(false);
		return message;
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

	private List<String> criterionDefinitions(final String descriptionMarkdown) {
		return LoeCriterionParser.parse(requirement.id(), descriptionMarkdown).stream()
				.map(criterion -> criterion.criterionKey() + ":" + criterion.pointUnits()).toList();
	}

	interface Handler extends LoeSectionHandler<LoeRequirement> {

		void save(LoeRequirement requirement, String descriptionMarkdown, int maxPoints, boolean bonus);
	}
}
