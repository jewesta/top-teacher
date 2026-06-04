package de.topteacher.ui.view;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import de.topteacher.ui.MainLayout;

@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "pupils", layout = MainLayout.class)
public class PupilsView extends VerticalLayout implements HasDynamicTitle {

	public PupilsView() {
		setSizeFull();
		setPadding(true);
		setSpacing(true);

		add(new H1("Pupils"));
	}

	@Override
	public String getPageTitle() {
		return "Pupils";
	}
}
