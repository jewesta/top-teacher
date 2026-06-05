package de.topteacher.ui.component;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Span;

abstract class EhBadge extends Composite<Span> implements EhRefreshable {

	protected EhBadge(final String className) {
		getContent().addClassName(className);
	}

	protected void setBadgeText(final String text) {
		getContent().setText(text);
	}

	String getText() {
		return getContent().getText();
	}

}
