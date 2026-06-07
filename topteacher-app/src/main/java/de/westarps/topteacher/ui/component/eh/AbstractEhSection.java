package de.westarps.topteacher.ui.component.eh;

import java.util.Collection;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

abstract class AbstractEhSection<T> extends Composite<Details> implements EhEditable {

	private final T item;
	private final Component summary;
	private final VerticalLayout body;
	private final EhPointBadge pointBadge;
	private final List<? extends EhEditable> children;

	protected AbstractEhSection(final T item, final String sectionClassName, final Component summary,
			final EhPointBadge pointBadge, final List<? extends EhEditable> children) {
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

	protected Component editorBlockWithMoveButtons(final EhSectionComponents components, final List<T> siblings,
			final EhSectionHandler<T> handler, final Collection<? extends Component> leadingActions,
			final Collection<? extends Component> trailingActions) {
		return components.editorBlock(
				components.actionComponentsWithMoveButtons(siblings, item, handler, leadingActions, trailingActions));
	}

	@Override
	public void refreshBadges() {
		refreshSectionBadges();
		pointBadge.refreshBadges();
		children.forEach(EhRefreshable::refreshBadges);
	}

	@Override
	public boolean isDirty() {
		return isSectionDirty() || children.stream().anyMatch(EhEditable::isDirty);
	}

	@Override
	public boolean save() {
		if (isSectionDirty() && !saveSection()) {
			return false;
		}
		for (final EhEditable child : children) {
			if (!child.save()) {
				return false;
			}
		}
		return true;
	}

	protected void refreshSectionBadges() {
	}

	protected boolean isSectionDirty() {
		return false;
	}

	protected boolean saveSection() {
		return true;
	}

}
