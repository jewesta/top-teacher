package de.westarps.topteacher.ui;

import com.vaadin.flow.server.VaadinServletRequest;

public final class UiUrls {

	private UiUrls() {
	}

	public static String contextRelative(final String path) {
		final VaadinServletRequest request = VaadinServletRequest.getCurrent();
		final String contextPath = request == null ? "" : request.getContextPath();
		return contextPath + path;
	}
}
