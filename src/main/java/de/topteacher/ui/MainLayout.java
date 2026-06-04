package de.topteacher.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;

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

		viewTitle.getStyle().set("font-size", "var(--aura-font-size-l)").set("line-height", "var(--aura-line-height-m)")
				.set("margin", "0");

		final HorizontalLayout header = new HorizontalLayout(drawerToggle, viewTitle);
		header.setAlignItems(FlexComponent.Alignment.CENTER);
		header.setSpacing(false);
		header.setWidthFull();
		header.getStyle().set("min-height", "3.5rem").set("padding", "0 var(--vaadin-padding-m)");

		return header;
	}

	private Component createDrawerHeader() {
		final Header header = new Header();
		header.getStyle().set("align-items", "center")
				.set("border-bottom", "1px solid var(--vaadin-border-color-secondary)").set("box-sizing", "border-box")
				.set("display", "flex").set("height", "3.5rem").set("padding", "0 var(--vaadin-padding-m)");

		final Span appName = new Span("TopTeacher");
		appName.getStyle().set("font-size", "var(--aura-font-size-l)").set("font-weight", "600");

		header.add(appName);
		return header;
	}

	private Component createNavigation() {
		final SideNav navigation = new SideNav();
		navigation.addItem(new SideNavItem("Pupils", PupilsView.class, VaadinIcon.USERS.create()));

		final Main wrapper = new Main(navigation);
		wrapper.getStyle().set("padding", "var(--vaadin-padding-s)");
		return wrapper;
	}

	private String getCurrentPageTitle() {
		final PageTitle title = getContent().getClass().getAnnotation(PageTitle.class);
		return title == null ? "TopTeacher" : title.value();
	}
}
