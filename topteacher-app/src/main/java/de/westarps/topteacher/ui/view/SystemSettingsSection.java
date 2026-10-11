package de.westarps.topteacher.ui.view;

import com.vaadin.flow.component.Component;

/** A persistent content section shown inside the system-settings navigator. */
public interface SystemSettingsSection {

	String label();

	Component content();
}
