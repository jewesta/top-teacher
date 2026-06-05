package de.topteacher.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
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

import de.topteacher.ui.view.CoursesView;
import de.topteacher.ui.view.ExamsView;
import de.topteacher.ui.view.PupilsView;

public class MainLayout extends AppLayout implements AfterNavigationObserver {

	private final Tabs navigationTabs = new Tabs();
	private final Tab pupilsTab = navigationTab("Schüler", VaadinIcon.ACADEMY_CAP, PupilsView.class);
	private final Tab coursesTab = navigationTab("Kurse", VaadinIcon.BOOK, CoursesView.class);
	private final Tab examsTab = navigationTab("Klausuren", VaadinIcon.EDIT, ExamsView.class);

	public MainLayout() {
		addToNavbar(createHeader());
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
		}
	}

	private Component createHeader() {
		final Header header = new Header();
		header.addClassName("tt-header");

		final Image logo = new Image("images/topteacher-logo.png", "TopTeacher!");
		logo.addClassName("tt-app-logo");

		navigationTabs.addClassName("tt-top-navigation");
		navigationTabs.add(pupilsTab, coursesTab, examsTab);

		final HorizontalLayout spacer = new HorizontalLayout();
		spacer.addClassName("tt-header-spacer");

		header.add(logo, navigationTabs, spacer);
		return header;
	}

	private Tab navigationTab(final String label, final VaadinIcon icon, final Class<? extends Component> view) {
		final RouterLink link = new RouterLink();
		link.addClassName("tt-top-navigation-link");
		link.setRoute(view);
		link.add(icon.create(), new Span(label));
		return new Tab(link);
	}
}
