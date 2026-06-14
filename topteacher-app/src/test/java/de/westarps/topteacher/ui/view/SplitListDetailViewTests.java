package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.ui.component.MultiSelectionGrid;

class SplitListDetailViewTests {

	@Test
	void switchesEditorModeBySelectionCount() {
		final TestSplitListDetailView view = new TestSplitListDetailView();
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
		final TestSplitListDetailView view = new TestSplitListDetailView();

		assertThatThrownBy(view::initializeAgain).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void filtersGridItemsBySearchText() {
		final TestSplitListDetailView view = new TestSplitListDetailView();
		view.setGridItemsForTest(List.of("Ada Lovelace active", "Grace Hopper inactive"));

		view.search("ada active");

		assertThat(view.visibleItems()).containsExactly("Ada Lovelace active");
	}

	@Test
	void clearsSelectionWhenSearchFiltersSelectedItemOut() {
		final TestSplitListDetailView view = new TestSplitListDetailView();
		view.setGridItemsForTest(List.of("Ada Lovelace active", "Grace Hopper inactive"));
		view.grid().select("Grace Hopper inactive");

		view.search("ada");

		assertThat(view.grid().getSelectedItems()).isEmpty();
		assertThat(view.selectedItems()).isEmpty();
	}

	@Test
	void keepsQuickFilterFirstInListToolbar() {
		final Button newButton = new Button("Neu");
		final TestSplitListDetailView view = new TestSplitListDetailView(newButton);

		final List<Component> toolbarChildren = toolbar(view).getChildren().toList();

		assertThat(toolbarChildren).hasSize(2);
		assertThat(toolbarChildren.getFirst()).isInstanceOf(TextField.class);
		assertThat(((TextField) toolbarChildren.getFirst()).getLabel()).isEqualTo("Schnellfilter");
		assertThat(toolbarChildren.get(1)).isSameAs(newButton);
	}

	@Test
	void initializesWithDefaultHooks() {
		final SplitListDetailView<String> view = new SplitListDetailView<>("Test", "tt-test-view",
				new MultiSelectionGrid<>());

		view.initializeView();

		assertThat(view.getChildren().toList()).isNotEmpty();
	}

	private static class TestSplitListDetailView extends SplitListDetailView<String> {

		private final List<Component> toolbarComponents;
		private EditorMode editorMode;
		private List<String> selectedItems = List.of();

		TestSplitListDetailView(final Component... toolbarComponents) {
			super("Test", "tt-test-view", new MultiSelectionGrid<>());
			this.toolbarComponents = List.of(toolbarComponents);
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
		protected List<Component> createListToolbarComponents() {
			return toolbarComponents;
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

	private static HorizontalLayout toolbar(final Component root) {
		return components(root, HorizontalLayout.class).stream()
				.filter(layout -> layout.hasClassName("tt-master-toolbar")).findFirst().orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
