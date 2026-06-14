package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;

@SuppressWarnings({ "rawtypes", "unchecked" })
class GradingScaleSettingsTabTests {

	@Test
	void hidesLifecycleInCreateModeAndShowsItInEditMode() {
		final GradingScale gradingScale = new GradingScale(1, "Einführungsphase", 100, Lifecycle.ACTIVE);
		final GradingScaleRepository gradingScaleRepository = mock(GradingScaleRepository.class);
		when(gradingScaleRepository.findAll()).thenReturn(List.of(gradingScale));
		when(gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id())).thenReturn(List.of());

		final GradingScaleSettingsTab tab = new GradingScaleSettingsTab(gradingScaleRepository);
		final ComboBox<Lifecycle> lifecycle = comboBox(tab, "Status");

		assertThat(lifecycle.isVisible()).isFalse();

		grid(tab).select(gradingScale);

		assertThat(lifecycle.isVisible()).isTrue();
	}

	@Test
	void hidesQuickFilter() {
		final GradingScaleRepository gradingScaleRepository = mock(GradingScaleRepository.class);
		when(gradingScaleRepository.findAll()).thenReturn(List.of());

		final GradingScaleSettingsTab tab = new GradingScaleSettingsTab(gradingScaleRepository);

		assertThat(components(tab, TextField.class)).noneMatch(field -> "Schnellfilter".equals(field.getLabel()));
	}

	private static Grid<GradingScale> grid(final Component root) {
		return components(root, Grid.class).getFirst();
	}

	private static ComboBox<Lifecycle> comboBox(final Component root, final String label) {
		return components(root, ComboBox.class).stream().filter(comboBox -> label.equals(comboBox.getLabel()))
				.findFirst().map(comboBox -> (ComboBox<Lifecycle>) comboBox).orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), children(root).flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private static Stream<Component> children(final Component root) {
		final Stream<Component> ordinaryChildren = root.getChildren();
		if (!(root instanceof TabSheet tabSheet)) {
			return ordinaryChildren;
		}
		final Stream<Component> tabChildren = IntStream.range(0, tabSheet.getTabCount())
				.mapToObj(index -> tabSheet.getComponent(tabSheet.getTabAt(index)));
		return Stream.concat(ordinaryChildren, tabChildren);
	}
}
