package de.westarps.topteacher.ui.component.eh;

interface EhSectionHandler<T> {

	void move(T item, int offset);

	void delete(T item);

}
