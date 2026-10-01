package de.westarps.vaadin.tray;

import java.util.Collection;
import java.util.List;

public interface TrayController<I> {

	TrayState getState();

	void hide();

	void peek();

	void show();

	/**
	 * Returns an immutable snapshot in visual top-to-bottom order.
	 */
	List<I> getItems();

	/**
	 * Replaces all items while retaining their supplied iteration order.
	 */
	void setItems(Collection<? extends I> items);

	/**
	 * Adds an item at the top of the tray.
	 */
	void addItem(I item);

	/**
	 * Adds all supplied items at the top without reversing their iteration order.
	 */
	void addItems(Collection<? extends I> items);

	/**
	 * Removes the first matching item in visual order.
	 */
	boolean removeItem(I item);

	void clearItems();
}
