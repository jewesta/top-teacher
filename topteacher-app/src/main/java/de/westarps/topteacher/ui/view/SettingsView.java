package de.westarps.topteacher.ui.view;

import java.util.List;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
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
		setAlignItems(Alignment.STRETCH);

		final TabSheet tabs = createContent();
		add(tabs);
		expand(tabs);
	}

	private TabSheet createContent() {
		final TabSheet tabs = new TabSheet();
		tabs.addClassName("tt-settings-tabs");
		tabs.addThemeVariants(TabSheetVariant.LUMO_NO_PADDING);
		tabs.setSizeFull();
		settingsTabs.forEach(settingsTab -> addTab(tabs, settingsTab));
		return tabs;
	}

	private void addTab(final TabSheet tabs, final SettingsTab settingsTab) {
		final var content = settingsTab.content();
		content.getElement().removeFromParent();
		tabs.add(settingsTab.label(), content);
	}
}
