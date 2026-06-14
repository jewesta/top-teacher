package de.westarps.topteacher.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLink;

import de.westarps.topteacher.ApplicationVersion;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.ui.view.CoursesView;
import de.westarps.topteacher.ui.view.DatabaseInitializationDialogFactory;
import de.westarps.topteacher.ui.view.ExamsView;
import de.westarps.topteacher.ui.view.PupilsView;
import de.westarps.topteacher.ui.view.SettingsView;

public class MainLayout extends AppLayout implements AfterNavigationObserver {

	private final AppSettings appSettings;
	private final DatabaseInitializationDialogFactory databaseInitializationDialogs;
	private final Tabs navigationTabs = new Tabs();
	private final Tab pupilsTab = navigationTab("Schüler:innen", VaadinIcon.ACADEMY_CAP, PupilsView.class);
	private final Tab coursesTab = navigationTab("Kurse", VaadinIcon.BOOK, CoursesView.class);
	private final Tab examsTab = navigationTab("Klausuren", VaadinIcon.EDIT, ExamsView.class);
	private final Tab settingsTab = navigationTab("Einstellungen", VaadinIcon.COG, SettingsView.class);
	private boolean firstStartDialogShown;
	private boolean backupErrorShown;

	public MainLayout(final AppSettings appSettings,
			final DatabaseInitializationDialogFactory databaseInitializationDialogs) {
		this.appSettings = appSettings;
		this.databaseInitializationDialogs = databaseInitializationDialogs;
		addToNavbar(createHeader());
	}

	@Override
	protected void onAttach(final AttachEvent attachEvent) {
		super.onAttach(attachEvent);
		if (showFirstStartDialogIfRequired()) {
			return;
		}
		showBackupErrorIfPresent();
	}

	@Override
	public void afterNavigation(final AfterNavigationEvent event) {
		final Component content = getContent();
		if (content instanceof PupilsView) {
			navigationTabs.setSelectedTab(pupilsTab);
		} else if (content instanceof CoursesView) {
			navigationTabs.setSelectedTab(coursesTab);
		} else if (content instanceof ExamsView) {
			navigationTabs.setSelectedTab(examsTab);
		} else if (content instanceof SettingsView) {
			navigationTabs.setSelectedTab(settingsTab);
		}
	}

	private Component createHeader() {
		final Header header = new Header();
		header.addClassName("tt-header");

		final Image logo = new Image("images/topteacher-logo.svg", ApplicationVersion.DISPLAY_APP_NAME);
		logo.addClassName("tt-app-logo");
		final Span appBrand = new Span();
		appBrand.addClassName("tt-app-brand");
		appBrand.add(logo);

		ApplicationVersion.displayVersion().ifPresent(version -> {
			final Span versionLabel = new Span(version);
			versionLabel.addClassName("tt-app-version");
			appBrand.add(versionLabel);
		});

		navigationTabs.addClassName("tt-top-navigation");
		navigationTabs.add(pupilsTab, coursesTab, examsTab, settingsTab);

		final HorizontalLayout spacer = new HorizontalLayout();
		spacer.addClassName("tt-header-spacer");

		header.add(appBrand, navigationTabs, spacer);
		return header;
	}

	private Tab navigationTab(final String label, final VaadinIcon icon, final Class<? extends Component> view) {
		final RouterLink link = new RouterLink();
		link.addClassName("tt-top-navigation-link");
		link.setRoute(view);
		link.add(icon.create(), new Span(label));
		return new Tab(link);
	}

	private boolean showFirstStartDialogIfRequired() {
		if (firstStartDialogShown) {
			return false;
		}

		firstStartDialogShown = databaseInitializationDialogs.openFirstStartDialogIfRequired();
		return firstStartDialogShown;
	}

	private void showBackupErrorIfPresent() {
		if (backupErrorShown) {
			return;
		}

		appSettings.ttEventDatabaseBackupError().ifPresent(error -> {
			backupErrorShown = true;
			final ConfirmDialog dialog = new ConfirmDialog();
			dialog.setHeader("Datenbank-Backup fehlgeschlagen");
			dialog.setText(error);
			dialog.setConfirmText("OK");
			dialog.addConfirmListener(event -> appSettings.clearTtEventDatabaseBackupError());
			dialog.open();
		});
	}
}
