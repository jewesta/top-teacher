package de.westarps.topteacher.ui.component.eh;

interface EhEditable extends EhRefreshable {

	boolean isDirty();

	boolean save();
}
