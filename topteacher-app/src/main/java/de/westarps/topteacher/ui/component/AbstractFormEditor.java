package de.westarps.topteacher.ui.component;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public abstract class AbstractFormEditor extends VerticalLayout {

	private static final FormLayout.ResponsiveStep SINGLE_COLUMN = new FormLayout.ResponsiveStep("0", 1);
	private static final FormLayout.ResponsiveStep TWO_COLUMNS = new FormLayout.ResponsiveStep("32rem", 2);

	private final FormLayout form = new FormLayout();
	private final HorizontalLayout actions = new HorizontalLayout();

	protected AbstractFormEditor(final String className, final FormColumns formColumns,
			final List<? extends Component> leadingComponents, final List<? extends Component> formFields,
			final List<? extends Component> actionComponents) {
		addClassNames("tt-editor", className);
		setPadding(false);
		setWidthFull();

		form.addClassName("tt-editor-form");
		form.add(formFields.toArray(Component[]::new));
		form.setResponsiveSteps(formColumns.responsiveSteps());
		form.setWidthFull();

		actions.addClassName("tt-editor-actions");
		actions.setSpacing(true);
		actions.add(actionComponents.toArray(Component[]::new));

		add(leadingComponents.toArray(Component[]::new));
		if (!formFields.isEmpty()) {
			add(form);
		}
		if (!actionComponents.isEmpty()) {
			add(actions);
		}
	}

	public static AbstractFormEditor responsive(final String className, final List<? extends Component> formFields,
			final List<? extends Component> actionComponents) {
		return new DefaultFormEditor(className, FormColumns.RESPONSIVE, List.of(), formFields, actionComponents);
	}

	public static AbstractFormEditor singleColumn(final String className,
			final List<? extends Component> leadingComponents, final List<? extends Component> formFields,
			final List<? extends Component> actionComponents) {
		return new DefaultFormEditor(className, FormColumns.SINGLE, leadingComponents, formFields, actionComponents);
	}

	public static AbstractFormEditor contentOnly(final String className,
			final List<? extends Component> leadingComponents) {
		return new DefaultFormEditor(className, FormColumns.SINGLE, leadingComponents, List.of(), List.of());
	}

	protected FormLayout form() {
		return form;
	}

	protected HorizontalLayout actions() {
		return actions;
	}

	protected enum FormColumns {
		SINGLE(List.of(SINGLE_COLUMN)),
		RESPONSIVE(List.of(SINGLE_COLUMN, TWO_COLUMNS));

		private final List<FormLayout.ResponsiveStep> responsiveSteps;

		FormColumns(final List<FormLayout.ResponsiveStep> responsiveSteps) {
			this.responsiveSteps = responsiveSteps;
		}

		private List<FormLayout.ResponsiveStep> responsiveSteps() {
			return responsiveSteps;
		}
	}

	private static final class DefaultFormEditor extends AbstractFormEditor {

		private DefaultFormEditor(final String className, final FormColumns formColumns,
				final List<? extends Component> leadingComponents, final List<? extends Component> formFields,
				final List<? extends Component> actionComponents) {
			super(className, formColumns, leadingComponents, formFields, actionComponents);
		}
	}
}
