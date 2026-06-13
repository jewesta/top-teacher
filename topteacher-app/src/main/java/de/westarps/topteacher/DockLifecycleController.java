package de.westarps.topteacher;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.desktop.AppReopenedListener;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class DockLifecycleController {

	private static final Logger LOGGER = LoggerFactory.getLogger(DockLifecycleController.class);
	private static final String ABOUT_MENU_ITEM_LABEL = "About " + ApplicationVersion.DISPLAY_APP_NAME;

	private final boolean enabled;
	private final int serverPort;
	private final String contextPath;
	private final ConfigurableApplicationContext applicationContext;

	@Autowired
	DockLifecycleController(@Value("${tt.dock-icon:false}") final boolean enabled,
			@Value("${server.port:8080}") final int serverPort,
			@Value("${server.servlet.context-path:}") final String contextPath,
			final ConfigurableApplicationContext applicationContext) {
		this.enabled = enabled;
		this.serverPort = serverPort;
		this.contextPath = contextPath;
		this.applicationContext = applicationContext;
	}

	@EventListener(ApplicationReadyEvent.class)
	void configureDockIcon() {
		if (!enabled) {
			return;
		}

		final URI applicationUri = LocalBrowserLauncher.localApplicationUri(serverPort, contextPath);
		configureApplicationEvents(applicationUri);
		configureDockMenu();
	}

	private void configureApplicationEvents(final URI applicationUri) {
		final AppReopenedListener reopenedListener = event -> openApplication(applicationUri);
		final QuitHandler quitHandler = (event, response) -> quitApplication(response);

		if (!Desktop.isDesktopSupported()) {
			return;
		}

		final Desktop desktop = Desktop.getDesktop();
		if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
			desktop.addAppEventListener(reopenedListener);
		}
		if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
			desktop.setAboutHandler(event -> showAboutDialog());
		}
		if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
			desktop.setQuitHandler(quitHandler);
		}
	}

	private void openApplication(final URI applicationUri) {
		try {
			LocalBrowserLauncher.openWithDesktop(applicationUri);
		} catch (final IOException | RuntimeException exception) {
			LOGGER.warn("Could not open TopTeacher in the default browser at {}.", applicationUri, exception);
		}
	}

	private void quitApplication(final QuitResponse response) {
		int exitCode = 0;
		try {
			exitCode = SpringApplication.exit(applicationContext);
		} catch (final RuntimeException exception) {
			exitCode = 1;
			LOGGER.warn("Could not close TopTeacher cleanly after a native quit request.", exception);
		} finally {
			response.performQuit();
			System.exit(exitCode);
		}
	}

	private void configureDockMenu() {
		if (!isMacOs() || GraphicsEnvironment.isHeadless()) {
			return;
		}

		final PopupMenu dockMenu = new PopupMenu();
		final MenuItem aboutItem = new MenuItem(ABOUT_MENU_ITEM_LABEL);
		aboutItem.addActionListener(event -> showAboutDialog());
		dockMenu.add(aboutItem);

		try {
			final Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
			final Object application = applicationClass.getMethod("getApplication").invoke(null);
			applicationClass.getMethod("setDockMenu", PopupMenu.class).invoke(application, dockMenu);
			LOGGER.info("Configured TopTeacher macOS Dock menu.");
		} catch (final ReflectiveOperationException | LinkageError | RuntimeException exception) {
			LOGGER.warn("Could not configure TopTeacher macOS Dock menu.", exception);
		}
	}

	private void showAboutDialog() {
		EventQueue.invokeLater(() -> {
			requestForeground();

			final JOptionPane aboutPane = new JOptionPane(aboutMessage(), JOptionPane.INFORMATION_MESSAGE,
					JOptionPane.DEFAULT_OPTION, aboutIcon());
			final JDialog aboutDialog = aboutPane.createDialog(ABOUT_MENU_ITEM_LABEL);
			aboutDialog.setAlwaysOnTop(true);
			aboutDialog.setVisible(true);
			aboutDialog.dispose();
		});
	}

	private void requestForeground() {
		if (!Desktop.isDesktopSupported()) {
			return;
		}

		final Desktop desktop = Desktop.getDesktop();
		if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
			desktop.requestForeground(true);
		}
	}

	private static String aboutMessage() {
		return ApplicationVersion.displayVersion()
				.map(version -> ApplicationVersion.DISPLAY_APP_NAME + "\nVersion " + version + "\n\n"
						+ ApplicationVersion.COPYRIGHT)
				.orElse(ApplicationVersion.DISPLAY_APP_NAME + "\n\n" + ApplicationVersion.COPYRIGHT);
	}

	private static Icon aboutIcon() {
		final URL iconUrl = DockLifecycleController.class
				.getResource("/META-INF/resources/images/topteacher-icon-about.png");
		if (iconUrl == null) {
			return null;
		}

		final ImageIcon icon = new ImageIcon(iconUrl);
		final Image scaledImage = icon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
		return new ImageIcon(scaledImage);
	}

	private static boolean isMacOs() {
		return System.getProperty("os.name", "").toLowerCase().contains("mac");
	}
}
