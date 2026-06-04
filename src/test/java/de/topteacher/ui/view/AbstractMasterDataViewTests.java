package de.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;

import de.topteacher.ui.component.MultiSelectionGrid;

class AbstractMasterDataViewTests {

	@Test
	void switchesEditorModeBySelectionCount() {
		final TestMasterDataView view = new TestMasterDataView();
		view.grid().setItems(List.of("Ada", "Grace"));

		view.grid().select("Ada");

		assertThat(view.editorMode()).isEqualTo(EditorMode.SINGLE_SELECT);
		assertThat(view.selectedItems()).containsExactly("Ada");

		view.grid().select("Grace");

		assertThat(view.editorMode()).isEqualTo(EditorMode.MULTI_SELECT);
		assertThat(view.selectedItems()).containsExactly("Ada", "Grace");
	}

	@Test
	void rejectsDoubleInitialization() {
		final TestMasterDataView view = new TestMasterDataView();

		assertThatThrownBy(view::initializeAgain).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void filtersGridItemsBySearchText() {
		final TestMasterDataView view = new TestMasterDataView();
		view.setGridItemsForTest(List.of("Ada Lovelace active", "Grace Hopper inactive"));

		view.search("ada active");

		assertThat(view.visibleItems()).containsExactly("Ada Lovelace active");
	}

	@Test
	void clearsSelectionWhenSearchFiltersSelectedItemOut() {
		final TestMasterDataView view = new TestMasterDataView();
		view.setGridItemsForTest(List.of("Ada Lovelace active", "Grace Hopper inactive"));
		view.grid().select("Grace Hopper inactive");

		view.search("ada");

		assertThat(view.grid().getSelectedItems()).isEmpty();
		assertThat(view.selectedItems()).isEmpty();
	}

	private static class TestMasterDataView extends AbstractMasterDataView<String> {

		private EditorMode editorMode;
		private List<String> selectedItems = List.of();

		TestMasterDataView() {
			super("Test", "tt-test-view", new MultiSelectionGrid<>());
			initializeView();
		}

		@Override
		protected void configureGrid(final MultiSelectionGrid<String> grid) {
		}

		@Override
		protected Component createSingleSelectEditor() {
			return new Span("single");
		}

		@Override
		protected Component createMultiSelectEditor() {
			return new Span("multi");
		}

		@Override
		protected void onEditorModeChanged(final EditorMode editorMode, final List<String> selectedItems) {
			this.editorMode = editorMode;
			this.selectedItems = selectedItems;
		}

		private MultiSelectionGrid<String> grid() {
			return getGrid();
		}

		private void setGridItemsForTest(final List<String> items) {
			setGridItems(items);
		}

		private List<String> visibleItems() {
			return grid().getListDataView().getItems().toList();
		}

		private void search(final String value) {
			getSearchField().setValue(value);
		}

		private EditorMode editorMode() {
			return editorMode;
		}

		private List<String> selectedItems() {
			return selectedItems;
		}

		private void initializeAgain() {
			initializeView();
		}
	}
}
