package de.westarps.topteacher;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class LocalBrowserLauncher {

	private static final Logger LOGGER = LoggerFactory.getLogger(LocalBrowserLauncher.class);

	private final boolean enabled;
	private final int serverPort;
	private final String contextPath;
	private final BrowserOpener browserOpener;

	@Autowired
	LocalBrowserLauncher(@Value("${tt.launch-browser:false}") final boolean enabled,
			@Value("${server.port:8080}") final int serverPort,
			@Value("${server.servlet.context-path:}") final String contextPath) {
		this(enabled, serverPort, contextPath, LocalBrowserLauncher::openWithDesktop);
	}

	LocalBrowserLauncher(final boolean enabled, final int serverPort, final String contextPath,
			final BrowserOpener browserOpener) {
		this.enabled = enabled;
		this.serverPort = serverPort;
		this.contextPath = contextPath;
		this.browserOpener = browserOpener;
	}

	@EventListener(ApplicationReadyEvent.class)
	void openBrowser() {
		if (!enabled) {
			return;
		}

		final URI uri = localApplicationUri(serverPort, contextPath);
		try {
			browserOpener.open(uri);
		} catch (final IOException | RuntimeException exception) {
			LOGGER.warn("Could not open TopTeacher in the default browser at {}.", uri, exception);
		}
	}

	static URI localApplicationUri(final int serverPort, final String contextPath) {
		return URI.create("http://localhost:" + serverPort + normalizeContextPath(contextPath) + "/");
	}

	private static String normalizeContextPath(final String contextPath) {
		if (!StringUtils.hasText(contextPath) || "/".equals(contextPath)) {
			return "";
		}

		String normalized = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
		while (normalized.endsWith("/") && normalized.length() > 1) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	static void openWithDesktop(final URI uri) throws IOException {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			LOGGER.info("Desktop browser integration is not available. Open TopTeacher manually at {}.", uri);
			return;
		}
		Desktop.getDesktop().browse(uri);
	}

	@FunctionalInterface
	interface BrowserOpener {

		void open(URI uri) throws IOException;
	}
}
