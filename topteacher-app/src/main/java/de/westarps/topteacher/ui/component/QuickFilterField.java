package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

public class QuickFilterField extends TextField {

	public QuickFilterField() {
		addClassName("tt-quick-filter");
		setClearButtonVisible(true);
		setLabel("Schnellfilter");
		setPlaceholder("Suchbegriff");
		setPrefixComponent(VaadinIcon.SEARCH.create());
		setValueChangeMode(ValueChangeMode.EAGER);
	}
}
