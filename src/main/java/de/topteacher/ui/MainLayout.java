package de.topteacher.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.PageTitle;

import de.topteacher.ui.view.CoursesView;
import de.topteacher.ui.view.ExamsView;
import de.topteacher.ui.view.PupilsView;

public class MainLayout extends AppLayout implements AfterNavigationObserver {

	private final H2 viewTitle = new H2();

	public MainLayout() {
		setPrimarySection(Section.DRAWER);

		addToNavbar(createHeader());
		addToDrawer(createDrawerHeader(), createNavigation());
	}

	@Override
	public void afterNavigation(final AfterNavigationEvent event) {
		viewTitle.setText(getCurrentPageTitle());
	}

	private Component createHeader() {
		final DrawerToggle drawerToggle = new DrawerToggle();

		viewTitle.addClassName("tt-view-title");

		final HorizontalLayout header = new HorizontalLayout(drawerToggle, viewTitle);
		header.addClassName("tt-header");

		return header;
	}

	private Component createDrawerHeader() {
		final Header header = new Header();
		header.addClassName("tt-drawer-header");

		final Image appName = new Image("images/topteacher-logo.png", "TopTeacher!");
		appName.addClassName("tt-app-logo");

		header.add(appName);
		return header;
	}

	private Component createNavigation() {
		final SideNav navigation = new SideNav();
		navigation.addClassName("tt-side-nav");
		navigation.addItem(new SideNavItem("Schüler", PupilsView.class, VaadinIcon.ACADEMY_CAP.create()));
		navigation.addItem(new SideNavItem("Kurse", CoursesView.class, VaadinIcon.BOOK.create()));
		navigation.addItem(new SideNavItem("Klausuren", ExamsView.class, VaadinIcon.CLIPBOARD_TEXT.create()));

		final Main wrapper = new Main(navigation);
		wrapper.addClassName("tt-navigation");
		return wrapper;
	}

	private String getCurrentPageTitle() {
		final Component content = getContent();
		if (content instanceof final HasDynamicTitle dynamicTitle) {
			return dynamicTitle.getPageTitle();
		}

		final PageTitle title = content.getClass().getAnnotation(PageTitle.class);
		return title == null ? "TopTeacher" : title.value();
	}
}
