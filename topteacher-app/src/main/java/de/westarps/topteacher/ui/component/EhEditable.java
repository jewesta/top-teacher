package de.westarps.topteacher.ui.component;

interface EhEditable extends EhRefreshable {

	boolean isDirty();

	boolean save();
}
