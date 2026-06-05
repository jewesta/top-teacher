package de.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import de.topteacher.model.EhRequirement;

final class EhRequirementSection extends Composite<VerticalLayout> {

	private final EhRequirement requirement;
	private final Component summary;
	private final Component description;
	private final Component fields;
	private final Component actions;

	EhRequirementSection(final EhRequirement requirement, final Component summary, final Component description,
			final Component fields, final Component actions) {
		this.requirement = requirement;
		this.summary = summary;
		this.description = description;
		this.fields = fields;
		this.actions = actions;
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
}
