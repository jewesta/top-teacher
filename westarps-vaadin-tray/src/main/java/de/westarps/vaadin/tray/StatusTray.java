package de.westarps.vaadin.tray;

import java.util.Locale;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;

import de.westarps.validate.TestResult;
import de.westarps.validate.TestResults;
import de.westarps.validate.ValidationSummary;

@SuppressWarnings("serial")
@CssImport("./styles/ws-status-tray.css")
public class StatusTray extends Tray {

	private ValidationSummary results = TestResults.pass();

	public StatusTray() {
		this("");
	}

	public StatusTray(final String label) {
		super(label);
		addClassName("ws-status-tray");
		getContentLayout().setSpacing(false);
		getContentLayout().getElement().setAttribute("role", "list");
	}

	public StatusTray(final ValidationSummary results) {
		this("", results);
	}

	public StatusTray(final String label, final ValidationSummary results) {
		this(label);
		setResults(results);
	}

	public ValidationSummary getResults() {
		return results;
	}

	public void setResults(final ValidationSummary results) {
		this.results = Objects.requireNonNull(results, "results must not be null");
		getContentLayout().removeAll();
		this.results.getTestResults().stream().map(StatusTray::createEntry).forEach(getContentLayout()::add);

		if (!this.results.hasAny() && getState() != TrayState.HIDE) {
			peek();
		}
	}

	private static Component createEntry(final TestResult result) {
		final Div entry = new Div(result.message());
		entry.addClassNames("ws-status-tray-entry", "ws-status-tray-entry-message");
		entry.getElement().setAttribute("data-severity", result.severity().name().toLowerCase(Locale.ROOT));
		entry.getElement().setAttribute("role", "listitem");
		entry.setWidthFull();
		return entry;
	}
}
