package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.html.Span;

import de.westarps.validate.TestResult;

final class LoeAllocationMessage extends Span {

	LoeAllocationMessage(final String scopeClassName) {
		addClassNames("tt-eh-allocation-message", scopeClassName);
		setVisible(false);
	}

	void setValidation(final List<TestResult> results) {
		if (results.isEmpty()) {
			setText("");
			setVisible(false);
			getElement().removeAttribute("data-severity");
			return;
		}
		final TestResult result = results.getFirst();
		getElement().setAttribute("data-severity", result.severity().name().toLowerCase(Locale.ROOT));
		setText(result.message());
		setVisible(true);
	}
}
