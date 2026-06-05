package de.topteacher.ui.component;

import java.util.Collection;

import com.vaadin.flow.component.Component;

import de.topteacher.model.EhPart;

final class EhPartSection extends AbstractEhSection<EhPart> {

	EhPartSection(final EhPart part, final Component summary, final Component actions,
			final Collection<? extends Component> categories) {
		super(part, "tt-eh-part", summary);
		addToBody(actions);
		addToBody(categories);
	}

}
