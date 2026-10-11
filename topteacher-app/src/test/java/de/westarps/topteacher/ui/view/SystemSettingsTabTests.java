package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;

class SystemSettingsTabTests {

	@Test
	@SuppressWarnings("unchecked")
	void usesFixedNavigationAndSelectsFirstSection() {
		final Div fileAccess = new Div();
		final Div backup = new Div();
		final SystemSettingsSection first = new StaticSection("MCP > Dateibehandlung", fileAccess);
		final SystemSettingsSection second = new StaticSection("Datenbank > Sicherung", backup);

		final SystemSettingsTab systemSettings = new SystemSettingsTab(List.of(first, second));

		final Grid<SystemSettingsSection> navigation = (Grid<SystemSettingsSection>) components(systemSettings,
				Grid.class).getFirst();
		final SplitLayout split = components(systemSettings, SplitLayout.class).getFirst();
		assertThat(systemSettings.label()).isEqualTo("Systemeinstellungen");
		assertThat(navigation.getSelectedItems()).containsExactly(first);
		assertThat(components(systemSettings, TabSheet.class)).isEmpty();
		assertThat(components(systemSettings, Div.class)).contains(fileAccess).doesNotContain(backup);
		assertThat(split.getSplitterPosition()).isEqualTo(30);
	}

	@Test
	@SuppressWarnings("unchecked")
	void keepsSectionStateWhenSwitchingBetweenSections() {
		final TextField fileAccessValue = new TextField();
		final Div fileAccess = new Div(fileAccessValue);
		final Div backup = new Div();
		final SystemSettingsSection first = new StaticSection("MCP > Dateibehandlung", fileAccess);
		final SystemSettingsSection second = new StaticSection("Datenbank > Sicherung", backup);
		final SystemSettingsTab systemSettings = new SystemSettingsTab(List.of(first, second));
		final Grid<SystemSettingsSection> navigation = (Grid<SystemSettingsSection>) components(systemSettings, Grid.class)
				.getFirst();

		fileAccessValue.setValue("ungespeichert");
		navigation.select(second);
		navigation.select(first);

		assertThat(fileAccessValue.getValue()).isEqualTo("ungespeichert");
		assertThat(fileAccess.getParent()).isPresent();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private record StaticSection(String label, Component content) implements SystemSettingsSection {
	}
}
