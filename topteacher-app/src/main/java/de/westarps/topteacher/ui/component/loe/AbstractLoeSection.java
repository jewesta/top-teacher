package de.westarps.topteacher.ui.component.loe;

import java.util.Collection;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

abstract class AbstractLoeSection<T> extends Composite<Details> implements LoeEditable {

	private final T item;
	private final Component summary;
	private final VerticalLayout body;
	private final LoeRefreshable sectionBadge;
	private final List<? extends LoeEditable> children;

	protected AbstractLoeSection(final T item, final String sectionClassName, final Component summary,
			final LoeRefreshable sectionBadge, final List<? extends LoeEditable> children) {
		this.item = item;
		this.summary = summary;
		this.sectionBadge = sectionBadge;
		this.children = List.copyOf(children);
		this.body = new VerticalLayout();
		body.addClassName("tt-eh-section-content");
		body.setPadding(false);
		body.setWidthFull();

		getContent().addClassNames("tt-eh-details", sectionClassName);
	}

	@Override
	protected Details initContent() {
		return new ExplicitToggleDetails(summary, body);
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

	protected Component editorBlockWithMoveButtons(final LoeSectionComponents components, final List<T> siblings,
			final LoeSectionHandler<T> handler, final Collection<? extends Component> leadingActions,
			final Collection<? extends Component> trailingActions, final boolean correctionMode) {
		return components.editorBlock(components.actionComponentsWithMoveButtons(siblings, item, handler,
				leadingActions, trailingActions, correctionMode));
	}

	@Override
	public void refreshBadges() {
		refreshSectionBadges();
		if (sectionBadge != null) {
			sectionBadge.refreshBadges();
		}
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

	private static final class ExplicitToggleDetails extends Details {

		private ExplicitToggleDetails(final Component summary, final Component body) {
			super(summary, body);
			getElement().setAttribute("data-summary-toggle", "explicit");
		}

		@Override
		protected Component createSummaryContainer() {
			final Div container = new Div();
			container.addClassName("tt-eh-details-summary");
			container.addAttachListener(event -> container.getElement().executeJs("""
				if (!this.__ttExplicitSectionToggle) {
				   this.__ttExplicitSectionToggle = true;
				   this.addEventListener('click', event => event.stopPropagation());
				}
				"""));
			return container;
		}
	}

}
