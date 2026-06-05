package de.topteacher.ui.component;

import java.util.Collection;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

abstract class AbstractEhSection<T> extends Composite<Details> implements EhRefreshable {

	private final T item;
	private final Component summary;
	private final VerticalLayout body;
	private final EhPointBadge pointBadge;
	private final List<? extends EhRefreshable> children;

	protected AbstractEhSection(final T item, final String sectionClassName, final Component summary,
			final EhPointBadge pointBadge, final List<? extends EhRefreshable> children) {
		this.item = item;
		this.summary = summary;
		this.pointBadge = pointBadge;
		this.children = List.copyOf(children);
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

	@Override
	public void refreshBadges() {
		refreshSectionBadges();
		pointBadge.refreshBadges();
		children.forEach(EhRefreshable::refreshBadges);
	}

	protected void refreshSectionBadges() {
	}

}
