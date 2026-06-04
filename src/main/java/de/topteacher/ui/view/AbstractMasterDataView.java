package de.topteacher.ui.view;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.HasDynamicTitle;

import de.topteacher.ui.component.MultiSelectionGrid;

public abstract class AbstractMasterDataView<T> extends VerticalLayout implements HasDynamicTitle {

	private final String pageTitle;
	private final String viewClassName;
	private final MultiSelectionGrid<T> grid;
	private final Div editorHost = new Div();
	private final TabSheet contextTabs = new TabSheet();

	private boolean initialized;
	private EditorMode editorMode = EditorMode.SINGLE_SELECT;
	private List<T> selectedItems = List.of();
	private Component singleSelectEditor;
	private Component multiSelectEditor;

	protected AbstractMasterDataView(final String pageTitle, final String viewClassName,
			final MultiSelectionGrid<T> grid) {
		this.pageTitle = Objects.requireNonNull(pageTitle, "Page title can not be null");
		this.viewClassName = Objects.requireNonNull(viewClassName, "View class name can not be null");
		this.grid = Objects.requireNonNull(grid, "Grid can not be null");
	}

	@Override
	public String getPageTitle() {
		return pageTitle;
	}

	protected final void initializeView() {
		if (initialized) {
			throw new IllegalStateException("View has already been initialized.");
		}
		initialized = true;

		setSizeFull();
		setPadding(false);
		setSpacing(false);
		addClassNames("tt-master-data-view", viewClassName);

		configureGrid(grid);
		grid.setSizeFull();

		singleSelectEditor = Objects.requireNonNull(createSingleSelectEditor(), "Single-select editor can not be null");
		multiSelectEditor = Objects.requireNonNull(createMultiSelectEditor(), "Multi-select editor can not be null");

		grid.addSelectionChangedListener(this::updateEditorMode);
		add(createPageContent());
		updateEditorMode(grid.getSelectedItems());
	}

	protected abstract void configureGrid(MultiSelectionGrid<T> grid);

	protected abstract Component createSingleSelectEditor();

	protected abstract Component createMultiSelectEditor();

	protected void onEditorModeChanged(final EditorMode editorMode, final List<T> selectedItems) {
	}

	protected final MultiSelectionGrid<T> getGrid() {
		return grid;
	}

	protected final TabSheet getContextTabs() {
		return contextTabs;
	}

	protected final EditorMode getEditorMode() {
		return editorMode;
	}

	protected final List<T> getSelectedItems() {
		return selectedItems;
	}

	protected final void setGridItems(final Collection<T> items) {
		grid.setItems(items);
	}

	protected final void clearSelection() {
		grid.deselectAll();
	}

	protected double getSplitterPosition() {
		return 70;
	}

	protected String getListAreaMinWidth() {
		return "24rem";
	}

	protected String getContextAreaMinWidth() {
		return "20rem";
	}

	private Component createPageContent() {
		final VerticalLayout listArea = new VerticalLayout(grid);
		listArea.addClassName("tt-master-list");
		listArea.setPadding(false);
		listArea.setSpacing(false);
		listArea.setSizeFull();

		final SplitLayout splitLayout = new SplitLayout(listArea, createContextArea());
		splitLayout.addClassName("tt-master-data-split");
		splitLayout.setSizeFull();
		splitLayout.setSplitterPosition(getSplitterPosition());
		splitLayout.setPrimaryStyle("min-width", getListAreaMinWidth());
		splitLayout.setSecondaryStyle("min-width", getContextAreaMinWidth());
		return splitLayout;
	}

	private Component createContextArea() {
		editorHost.addClassName("tt-editor-host");
		editorHost.setSizeFull();

		contextTabs.addClassName("tt-context-tabs");
		contextTabs.setSizeFull();
		contextTabs.add("Editor", editorHost);

		final VerticalLayout contextArea = new VerticalLayout(contextTabs);
		contextArea.addClassName("tt-context-area");
		contextArea.setPadding(false);
		contextArea.setSpacing(false);
		contextArea.setSizeFull();
		return contextArea;
	}

	private void updateEditorMode(final Set<T> selection) {
		selectedItems = List.copyOf(selection);
		final EditorMode nextEditorMode = selectedItems.size() > 1 ? EditorMode.MULTI_SELECT : EditorMode.SINGLE_SELECT;
		if (nextEditorMode != editorMode || editorHost.getChildren().findAny().isEmpty()) {
			editorHost.removeAll();
			editorHost.add(nextEditorMode == EditorMode.MULTI_SELECT ? multiSelectEditor : singleSelectEditor);
			editorMode = nextEditorMode;
		}
		onEditorModeChanged(editorMode, selectedItems);
	}
}
