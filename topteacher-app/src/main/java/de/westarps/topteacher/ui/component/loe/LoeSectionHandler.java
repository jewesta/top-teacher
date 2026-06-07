package de.westarps.topteacher.ui.component.loe;

interface LoeSectionHandler<T> {

	void move(T item, int offset);

	void delete(T item);

}
