package de.westarps.vaadin.tray;

import java.util.Locale;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import de.westarps.validate.TestResult;
import de.westarps.validate.TestResults;
import de.westarps.validate.ValidationSeverity;
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
		setVisible(false);
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

		final boolean hasResults = this.results.hasAny();
		setVisible(hasResults);
		if (!hasResults) {
			close();
		}
	}

	private static Component createEntry(final TestResult result) {
		final HorizontalLayout entry = new HorizontalLayout();
		entry.addClassName("ws-status-tray-entry");
		entry.getElement().setAttribute("data-severity", result.severity().name().toLowerCase(Locale.ROOT));
		entry.getElement().setAttribute("role", "listitem");
		entry.setAlignItems(HorizontalLayout.Alignment.CENTER);
		entry.setPadding(false);
		entry.setSpacing(false);
		entry.setWidthFull();

		final Icon icon = iconFor(result.severity());
		icon.addClassName("ws-status-tray-entry-icon");
		icon.getElement().setAttribute("aria-hidden", "true");
		entry.add(icon);

		final Span message = new Span(result.message());
		message.addClassName("ws-status-tray-entry-message");
		entry.addAndExpand(message);
		return entry;
	}

	private static Icon iconFor(final ValidationSeverity severity) {
		return switch (severity) {
		case INFO -> VaadinIcon.INFO_CIRCLE.create();
		case WARNING -> VaadinIcon.WARNING.create();
		case ERROR -> VaadinIcon.EXCLAMATION_CIRCLE.create();
		};
	}
}
