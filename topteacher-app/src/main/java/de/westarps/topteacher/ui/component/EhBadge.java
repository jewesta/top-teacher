package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Span;

abstract class EhBadge extends Composite<Span> implements EhRefreshable {

	private String text = "";

	protected EhBadge(final String className) {
		getContent().addClassName(className);
	}

	protected void setBadgeText(final String text) {
		this.text = text;
		getContent().removeAll();
		getContent().setText(text);
	}

	protected void setBadgeComponents(final String text, final Component... components) {
		this.text = text;
		getContent().setText("");
		getContent().removeAll();
		getContent().add(components);
	}

	String getText() {
		return text;
	}

}
