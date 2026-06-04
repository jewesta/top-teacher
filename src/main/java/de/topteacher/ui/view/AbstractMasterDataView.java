package de.topteacher.ui.view;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;

import de.topteacher.ui.component.MultiSelectionGrid;

public abstract class AbstractMasterDataView<T> extends VerticalLayout implements HasDynamicTitle {

	private final String pageTitle;
	private final String viewClassName;
	private final MultiSelectionGrid<T> grid;
	private final TextField searchField = new TextField();
	private final Div editorHost = new Div();
	private final TabSheet contextTabs = new TabSheet();

	private ListDataProvider<T> dataProvider;
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

		configureSearchField();
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

	protected String getSearchText(final T item) {
		return item == null ? "" : item.toString();
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

	protected final TextField getSearchField() {
		return searchField;
	}

	protected final void setGridItems(final Collection<T> items) {
		dataProvider = DataProvider.ofCollection(List.copyOf(Objects.requireNonNull(items, "Items can not be null")));
		applySearchFilter();
		grid.setItems(dataProvider);
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

	protected Component createListToolbarPrefix() {
		return null;
	}

	private void configureSearchField() {
		searchField.addClassName("tt-master-search");
		searchField.setClearButtonVisible(true);
		searchField.setLabel("Suchen");
		searchField.setPlaceholder("Suchbegriff");
		searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
		searchField.setValueChangeMode(ValueChangeMode.EAGER);
		searchField.setWidthFull();
		searchField.addValueChangeListener(event -> applySearchFilter());
	}

	private Component createPageContent() {
		final VerticalLayout listArea = new VerticalLayout(createListToolbar(), grid);
		listArea.addClassName("tt-master-list");
		listArea.setPadding(false);
		listArea.setSpacing(false);
		listArea.setSizeFull();
		listArea.expand(grid);

		final SplitLayout splitLayout = new SplitLayout(listArea, createContextArea());
		splitLayout.addClassName("tt-master-data-split");
		splitLayout.setSizeFull();
		splitLayout.setSplitterPosition(getSplitterPosition());
		splitLayout.setPrimaryStyle("min-width", getListAreaMinWidth());
		splitLayout.setSecondaryStyle("min-width", getContextAreaMinWidth());
		return splitLayout;
	}

	private Component createListToolbar() {
		final Component toolbarPrefix = createListToolbarPrefix();
		final HorizontalLayout toolbar = toolbarPrefix == null ? new HorizontalLayout(searchField)
				: new HorizontalLayout(toolbarPrefix, searchField);
		toolbar.addClassName("tt-master-toolbar");
		toolbar.setAlignItems(Alignment.END);
		toolbar.setPadding(false);
		toolbar.setSpacing(false);
		toolbar.setWidthFull();
		toolbar.getStyle().set("flex-wrap", "wrap");
		if (toolbarPrefix != null) {
			toolbar.setFlexGrow(0, toolbarPrefix);
		}
		toolbar.setFlexGrow(0, searchField);
		return toolbar;
	}

	private Component createContextArea() {
		editorHost.addClassName("tt-editor-host");
		editorHost.setSizeFull();

		contextTabs.addClassName("tt-context-tabs");
		contextTabs.setSizeFull();
		contextTabs.add("Bearbeiten", editorHost);

		final VerticalLayout contextArea = new VerticalLayout(contextTabs);
		contextArea.addClassName("tt-context-area");
		contextArea.setPadding(false);
		contextArea.setSpacing(false);
		contextArea.setSizeFull();
		return contextArea;
	}

	private void applySearchFilter() {
		if (dataProvider == null) {
			return;
		}

		final List<String> searchTokens = getSearchTokens();
		dataProvider.setFilter(item -> matchesSearch(item, searchTokens));
		deselectFilteredOutItems(searchTokens);
	}

	private List<String> getSearchTokens() {
		return Arrays.stream(normalize(searchField.getValue()).split("\\s+")).filter(token -> !token.isBlank())
				.toList();
	}

	private boolean matchesSearch(final T item, final List<String> searchTokens) {
		if (searchTokens.isEmpty()) {
			return true;
		}

		final String searchableText = normalize(getSearchText(item));
		return searchTokens.stream().allMatch(searchableText::contains);
	}

	private String normalize(final String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private void deselectFilteredOutItems(final List<String> searchTokens) {
		List.copyOf(grid.getSelectedItems()).stream().filter(item -> !matchesSearch(item, searchTokens))
				.forEach(grid::deselect);
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
