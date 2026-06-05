package de.topteacher.ui.component;

import java.util.Collection;

import com.vaadin.flow.component.Component;

import de.topteacher.model.EhCategory;

final class EhCategorySection extends AbstractEhSection<EhCategory> {

	EhCategorySection(final EhCategory category, final Component summary, final Component description,
			final Component actions, final Collection<? extends Component> tasks) {
		super(category, "tt-eh-category", summary);
		addToBody(description, actions);
		addToBody(tasks);
	}

}
