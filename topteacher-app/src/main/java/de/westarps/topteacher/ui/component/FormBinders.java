package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.data.binder.Binder;

public final class FormBinders {

	private FormBinders() {
	}

	public static void clearValidation(final Binder<?> binder) {
		binder.getFields().filter(HasValidation.class::isInstance).map(HasValidation.class::cast)
				.forEach(field -> field.setInvalid(false));
	}
}
