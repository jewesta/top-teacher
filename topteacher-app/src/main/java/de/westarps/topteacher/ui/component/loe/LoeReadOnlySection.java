package de.westarps.topteacher.ui.component.loe;

import java.util.Collection;
import java.util.List;

import com.vaadin.flow.component.Component;

final class LoeReadOnlySection<T> extends AbstractLoeSection<T> {

	<C extends Component & LoeEditable> LoeReadOnlySection(final T item, final String sectionClassName,
			final Component summary, final LoeRefreshable sectionBadge,
			final Collection<? extends Component> bodyComponents,
			final List<C> children) {
		super(item, sectionClassName, summary, sectionBadge, children);
		addToBody(bodyComponents);
		addToBody(children);
	}
}

