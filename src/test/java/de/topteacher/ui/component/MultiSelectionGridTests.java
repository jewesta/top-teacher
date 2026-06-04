package de.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.grid.Grid.SelectionMode;

class MultiSelectionGridTests {

	@Test
	void managesSelectionWithoutVaadinSelectionModel() {
		final MultiSelectionGrid<String> grid = new MultiSelectionGrid<>();

		grid.select("Ada");

		assertThat(grid.getSelectedItems()).containsExactly("Ada");
		assertThat(grid.isSelected("Ada")).isTrue();
		assertThat(grid.getSelectionModel().getSelectedItems()).isEmpty();
	}

	@Test
	void rejectsVaadinMultiSelectionModeToAvoidCheckboxColumn() {
		final MultiSelectionGrid<String> grid = new MultiSelectionGrid<>();

		assertThatThrownBy(() -> grid.setSelectionMode(SelectionMode.MULTI))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void selectsVisibleRangeAdditively() {
		final TestGrid grid = new TestGrid();
		grid.setItems(List.of("Ada", "Grace", "Marie", "Katherine"));

		grid.select("Grace");
		grid.selectRangeForTest("Grace", "Katherine");

		assertThat(grid.getSelectedItems()).containsExactly("Grace", "Marie", "Katherine");
	}

	@Test
	void notifiesListenersWhenSelectionChanges() {
		final MultiSelectionGrid<String> grid = new MultiSelectionGrid<>();
		final AtomicReference<Set<String>> latestSelection = new AtomicReference<>();

		grid.addSelectionChangedListener(latestSelection::set);
		grid.select("Ada");

		assertThat(latestSelection.get()).containsExactly("Ada");
	}

	@Test
	void composesCustomPartNamesWithSelectionPartName() {
		final MultiSelectionGrid<String> grid = new MultiSelectionGrid<>();

		grid.setPartNameGenerator(item -> "custom-row");
		grid.select("Ada");

		assertThat(grid.getPartNameGenerator().apply("Ada")).isEqualTo("custom-row tt-selected-row");
		assertThat(grid.getPartNameGenerator().apply("Grace")).isEqualTo("custom-row");
	}

	private static class TestGrid extends MultiSelectionGrid<String> {

		void selectRangeForTest(final String startItem, final String endItem) {
			selectRange(startItem, endItem);
		}
	}
}
