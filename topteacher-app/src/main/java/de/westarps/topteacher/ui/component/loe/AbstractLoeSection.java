package de.westarps.topteacher.ui.component.loe;

import java.util.Collection;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

abstract class AbstractLoeSection<T> extends Composite<Details> implements LoeEditable {

	private final T item;
	private final Component summary;
	private final VerticalLayout body;
	private final LoePointBadge pointBadge;
	private final List<? extends LoeEditable> children;

	protected AbstractLoeSection(final T item, final String sectionClassName, final Component summary,
			final LoePointBadge pointBadge, final List<? extends LoeEditable> children) {
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

	protected Component editorBlockWithMoveButtons(final LoeSectionComponents components, final List<T> siblings,
			final LoeSectionHandler<T> handler, final Collection<? extends Component> leadingActions,
			final Collection<? extends Component> trailingActions) {
		return components.editorBlock(
				components.actionComponentsWithMoveButtons(siblings, item, handler, leadingActions, trailingActions));
	}

	@Override
	public void refreshBadges() {
		refreshSectionBadges();
		pointBadge.refreshBadges();
		children.forEach(LoeRefreshable::refreshBadges);
	}

	@Override
	public boolean isDirty() {
		return isSectionDirty() || children.stream().anyMatch(LoeEditable::isDirty);
	}

	@Override
	public boolean save() {
		if (isSectionDirty() && !saveSection()) {
			return false;
		}
		for (final LoeEditable child : children) {
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
