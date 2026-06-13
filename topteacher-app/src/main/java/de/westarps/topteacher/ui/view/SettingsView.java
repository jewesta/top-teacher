package de.westarps.topteacher.ui.view;

import java.util.List;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import de.westarps.topteacher.ui.MainLayout;

@Route(value = "settings", layout = MainLayout.class)
public class SettingsView extends VerticalLayout implements HasDynamicTitle {

	private final List<SettingsTab> settingsTabs;

	public SettingsView(final List<SettingsTab> settingsTabs) {
		this.settingsTabs = List.copyOf(settingsTabs);

		configureView();
	}

	@Override
	public String getPageTitle() {
		return "Einstellungen";
	}

	private void configureView() {
		addClassName("tt-settings-view");
		setPadding(false);
		setSpacing(false);
		setSizeFull();
		add(createContent());
	}

	private TabSheet createContent() {
		final TabSheet tabs = new TabSheet();
		tabs.addClassName("tt-settings-tabs");
		tabs.setSizeFull();
		settingsTabs.forEach(settingsTab -> tabs.add(settingsTab.label(), settingsTab.content()));
		return tabs;
	}
}
