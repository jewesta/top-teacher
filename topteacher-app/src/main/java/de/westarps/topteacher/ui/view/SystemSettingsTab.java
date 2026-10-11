package de.westarps.topteacher.ui.view;

import java.util.List;

import org.springframework.core.annotation.Order;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Order(10)
@UIScope
@SpringComponent
public class SystemSettingsTab extends VerticalLayout implements SettingsTab {

	private final List<SystemSettingsSection> sections;
	private final Grid<SystemSettingsSection> navigation = new Grid<>();
	private final Div sectionHost = new Div();
	private SystemSettingsSection selectedSection;

	public SystemSettingsTab(final List<SystemSettingsSection> sections) {
		if (sections.isEmpty()) {
			throw new IllegalArgumentException("System settings require at least one section.");
		}
		this.sections = List.copyOf(sections);
		configureContent();
	}

	@Override
	public String label() {
		return "Systemeinstellungen";
	}

	@Override
	public Component content() {
		return this;
	}

	private void configureContent() {
		addClassNames("tt-master-data-view", "tt-system-settings-tab");
		setPadding(false);
		setSpacing(false);
		setSizeFull();

		configureNavigation();
		add(createPageContent());
		navigation.select(sections.getFirst());
	}

	private void configureNavigation() {
		navigation.addClassName("tt-system-settings-navigation");
		navigation.addColumn(SystemSettingsSection::label).setHeader("Bereich").setFlexGrow(1);
		navigation.setItems(sections);
		navigation.setSelectionMode(Grid.SelectionMode.SINGLE);
		navigation.addSelectionListener(event -> event.getFirstSelectedItem().ifPresentOrElse(this::showSection, () -> {
			if (selectedSection != null) {
				navigation.select(selectedSection);
			}
		}));
		navigation.setSizeFull();
	}

	private Component createPageContent() {
		final VerticalLayout listArea = new VerticalLayout(navigation);
		listArea.addClassNames("tt-master-list", "tt-system-settings-list");
		listArea.setPadding(false);
		listArea.setSpacing(false);
		listArea.setSizeFull();
		listArea.expand(navigation);

		sectionHost.addClassName("tt-system-settings-section-host");
		sectionHost.setSizeFull();

		final VerticalLayout contextArea = new VerticalLayout(sectionHost);
		contextArea.addClassName("tt-context-area");
		contextArea.setPadding(false);
		contextArea.setSpacing(false);
		contextArea.setSizeFull();
		contextArea.expand(sectionHost);

		final SplitLayout splitLayout = new SplitLayout(listArea, contextArea);
		splitLayout.addClassName("tt-master-data-split");
		splitLayout.setSizeFull();
		splitLayout.setSplitterPosition(30);
		splitLayout.setPrimaryStyle("min-width", "14rem");
		splitLayout.setSecondaryStyle("min-width", "28rem");
		return splitLayout;
	}

	private void showSection(final SystemSettingsSection section) {
		selectedSection = section;
		final Component content = section.content();
		content.getElement().removeFromParent();
		sectionHost.removeAll();
		sectionHost.add(content);
	}
}
