package de.topteacher.ui.component;

import java.util.Collection;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

abstract class AbstractEhSection<T> extends Composite<Details> {

	private final T item;
	private final Component summary;
	private final VerticalLayout body;

	protected AbstractEhSection(final T item, final String sectionClassName, final Component summary) {
		this.item = item;
		this.summary = summary;
		this.body = new VerticalLayout();
		body.addClassName("tt-eh-section-content");
		body.setPadding(false);
		body.setWidthFull();

		getContent().addClassNames("tt-eh-details", sectionClassName);
	}

	@Override
	protected Details initContent() {
		return new Details(summary, body);
	}

	protected T item() {
		return item;
	}

	protected void addToBody(final Component... components) {
		body.add(components);
	}

	protected void addToBody(final Collection<? extends Component> components) {
		components.forEach(body::add);
	}
}
