package de.topteacher.ui.component;

interface EhSectionHandler<T> {

	void move(T item, int offset);

	void delete(T item);

}
