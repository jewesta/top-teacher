package de.westarps.topteacher.ui.component.loe;

interface LoeEditable extends LoeRefreshable {

	boolean isDirty();

	boolean save();
}
