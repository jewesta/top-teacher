package de.westarps.topteacher.ui.component.eh;

interface EhTitledSectionHandler<T> extends EhSectionHandler<T> {

	void saveTitle(T item, String title);

}
