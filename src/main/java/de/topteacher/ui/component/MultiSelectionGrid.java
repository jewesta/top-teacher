package de.topteacher.ui.component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSelectionModel;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.shared.Registration;

/**
 * A flat grid with desktop-style row multi-selection and no checkbox column.
 *
 * @param <T> item type
 */
public class MultiSelectionGrid<T> extends Grid<T> {

	private static final String CLASS_NAME = "tt-multi-selection-grid";
	private static final String SELECTED_ROW_PART = "tt-selected-row";

	private final Set<T> selectedItems = new LinkedHashSet<>();
	private final List<SerializableConsumer<Set<T>>> selectionChangedListeners = new ArrayList<>();

	private SerializableFunction<T, String> customPartNameGenerator = item -> null;
	private T anchorItem;

	public MultiSelectionGrid() {
		super();
		configureRowSelection();
	}

	public MultiSelectionGrid(final Class<T> beanType) {
		super(beanType);
		configureRowSelection();
	}

	public MultiSelectionGrid(final Class<T> beanType, final boolean autoCreateColumns) {
		super(beanType, autoCreateColumns);
		configureRowSelection();
	}

	public MultiSelectionGrid(final Collection<T> items) {
		this();
		setItems(items);
	}

	public MultiSelectionGrid(final ListDataProvider<T> dataProvider) {
		this();
		setItems(dataProvider);
	}

	public MultiSelectionGrid(final DataProvider<T, Void> dataProvider) {
		this();
		setItems(dataProvider);
	}

	private void configureRowSelection() {
		addClassName(CLASS_NAME);
		super.setSelectionMode(SelectionMode.NONE);
		super.setPartNameGenerator(this::getComposedPartName);

		addItemClickListener(event -> {
			final T clickedItem = event.getItem();
			if (clickedItem == null) {
				return;
			}

			if (event.isCtrlKey() || event.isMetaKey()) {
				toggleSelection(clickedItem);
				return;
			}

			if (event.isShiftKey()) {
				selectRangeFromAnchor(clickedItem);
				return;
			}

			selectOnly(clickedItem);
		});
	}

	@Override
	public GridSelectionModel<T> setSelectionMode(final SelectionMode selectionMode) {
		if (selectionMode != SelectionMode.NONE) {
			throw new UnsupportedOperationException(
					"MultiSelectionGrid manages row selection itself so it can avoid Vaadin's checkbox column.");
		}
		return super.setSelectionMode(selectionMode);
	}

	@Override
	public void setPartNameGenerator(final SerializableFunction<T, String> partNameGenerator) {
		customPartNameGenerator = Objects.requireNonNull(partNameGenerator, "Part name generator can not be null");
		refreshViewport();
	}

	@Override
	public Set<T> getSelectedItems() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(selectedItems));
	}

	@Override
	public void select(final T item) {
		Objects.requireNonNull(item, "Item can not be null");

		final Set<T> previousSelection = snapshotSelection();
		selectedItems.add(item);
		anchorItem = item;
		selectionChanged(previousSelection);
	}

	@Override
	public void deselect(final T item) {
		Objects.requireNonNull(item, "Item can not be null");

		final Set<T> previousSelection = snapshotSelection();
		selectedItems.remove(item);
		selectionChanged(previousSelection);
	}

	@Override
	public void deselectAll() {
		final Set<T> previousSelection = snapshotSelection();
		selectedItems.clear();
		anchorItem = null;
		selectionChanged(previousSelection);
	}

	public Registration addSelectionChangedListener(final SerializableConsumer<Set<T>> listener) {
		Objects.requireNonNull(listener, "Selection changed listener can not be null");
		selectionChangedListeners.add(listener);
		return () -> selectionChangedListeners.remove(listener);
	}

	public boolean isSelected(final T item) {
		return selectedItems.contains(item);
	}

	protected void selectRange(final T startItem, final T endItem) {
		Objects.requireNonNull(startItem, "Start item can not be null");
		Objects.requireNonNull(endItem, "End item can not be null");

		final List<T> visibleItems = getVisibleItems();
		final int startIndex = indexOf(visibleItems, startItem);
		final int endIndex = indexOf(visibleItems, endItem);
		if (startIndex == -1 || endIndex == -1) {
			select(endItem);
			return;
		}

		final Set<T> previousSelection = snapshotSelection();
		final int from = Math.min(startIndex, endIndex);
		final int to = Math.max(startIndex, endIndex);
		for (int index = from; index <= to; index++) {
			selectedItems.add(visibleItems.get(index));
		}
		selectionChanged(previousSelection);
	}

	private void selectOnly(final T item) {
		final Set<T> previousSelection = snapshotSelection();
		selectedItems.clear();
		selectedItems.add(item);
		anchorItem = item;
		selectionChanged(previousSelection);
	}

	private void toggleSelection(final T item) {
		final Set<T> previousSelection = snapshotSelection();
		if (selectedItems.contains(item)) {
			selectedItems.remove(item);
		} else {
			selectedItems.add(item);
		}
		anchorItem = item;
		selectionChanged(previousSelection);
	}

	private void selectRangeFromAnchor(final T clickedItem) {
		if (anchorItem == null) {
			select(clickedItem);
			return;
		}
		selectRange(anchorItem, clickedItem);
	}

	private Set<T> snapshotSelection() {
		return new LinkedHashSet<>(selectedItems);
	}

	private void selectionChanged(final Set<T> previousSelection) {
		if (previousSelection.equals(selectedItems)) {
			return;
		}

		refreshViewport();
		final Set<T> immutableSelection = getSelectedItems();
		new ArrayList<>(selectionChangedListeners).forEach(listener -> listener.accept(immutableSelection));
	}

	private List<T> getVisibleItems() {
		try {
			return getListDataView().getItems().toList();
		} catch (final RuntimeException exception) {
			return List.of();
		}
	}

	private int indexOf(final List<T> items, final T searchedItem) {
		for (int index = 0; index < items.size(); index++) {
			if (Objects.equals(items.get(index), searchedItem)) {
				return index;
			}
		}
		return -1;
	}

	private String getComposedPartName(final T item) {
		final String customPartName = customPartNameGenerator.apply(item);
		if (!selectedItems.contains(item)) {
			return customPartName;
		}
		if (customPartName == null || customPartName.isBlank()) {
			return SELECTED_ROW_PART;
		}
		return customPartName + " " + SELECTED_ROW_PART;
	}
}
