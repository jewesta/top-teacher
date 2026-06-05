package de.topteacher.ui.component;

import java.util.Collection;

import com.vaadin.flow.component.Component;

import de.topteacher.model.EhTask;

final class EhTaskSection extends AbstractEhSection<EhTask> {

	EhTaskSection(final EhTask task, final Component summary, final Component actions,
			final Collection<? extends Component> requirements) {
		super(task, "tt-eh-task", summary);
		addToBody(actions);
		addToBody(requirements);
	}

}
