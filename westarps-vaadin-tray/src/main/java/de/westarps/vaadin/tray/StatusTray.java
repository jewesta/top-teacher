package de.westarps.vaadin.tray;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;

import de.westarps.vaadin.badge.AttachedBadge.Position;
import de.westarps.vaadin.badge.Badge.BadgeVariant;
import de.westarps.vaadin.badge.BadgeController;
import de.westarps.validate.TestResult;
import de.westarps.validate.ValidationSeverity;
import de.westarps.validate.ValidationSummary;

@SuppressWarnings("serial")
@CssImport("./styles/ws-status-tray.css")
public class StatusTray extends Tray<TestResult> implements StatusTrayController {

	private final BadgeController statusBadge;

	public StatusTray() {
		this("");
	}

	public StatusTray(final String label) {
		super(label, Position.TOP_RIGHT);
		statusBadge = getNotchBadgeController();
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

	@Override
	protected void onItemsChanged(final List<TestResult> previousItems, final List<TestResult> currentItems) {
		if (currentItems.isEmpty()) {
			statusBadge.hide();
			return;
		}
		statusBadge.show(badgeVariant(currentItems), Integer.toString(currentItems.size()));
	}

	@Override
	protected Component renderItem(final TestResult result) {
		final Div entry = new Div(result.message());
		entry.addClassNames("ws-status-tray-entry", "ws-status-tray-entry-message");
		entry.getElement().setAttribute("data-severity", result.severity().name().toLowerCase(Locale.ROOT));
		entry.getElement().setAttribute("role", "listitem");
		entry.setWidthFull();
		return entry;
	}

	private BadgeVariant badgeVariant(final List<TestResult> results) {
		if (containsSeverity(results, ValidationSeverity.ERROR)) {
			return BadgeVariant.ERROR;
		}
		if (containsSeverity(results, ValidationSeverity.WARNING)) {
			return BadgeVariant.WARNING;
		}
		return BadgeVariant.CONTRAST;
	}

	private boolean containsSeverity(final List<TestResult> results, final ValidationSeverity severity) {
		return results.stream().anyMatch(result -> result.severity() == severity);
	}
}
