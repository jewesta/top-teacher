package de.westarps.vaadin.tray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.SerializableRunnable;

import de.westarps.vaadin.badge.AttachedBadge.Position;
import de.westarps.vaadin.badge.Badge.BadgeVariant;
import de.westarps.vaadin.badge.BadgeController;
import de.westarps.validate.TestResult;
import de.westarps.validate.ValidationSeverity;
import de.westarps.validate.ValidationResults;
import de.westarps.validate.ValidationSummary;

@SuppressWarnings("serial")
@CssImport("./styles/ws-status-tray.css")
public class StatusTray extends Tray<TestResult> implements StatusTrayController {

	private record LinkTarget<T>(int index, T target) {
	}

	private static final class StatusLink extends Anchor {

		private StatusLink(final String href, final String text, final SerializableRunnable action) {
			super(href, text);
			setRouterIgnore(true);
			addClassName("ws-status-tray-entry-link");
			getElement().setAttribute("title", "Zum betroffenen Abschnitt springen");
			getElement().addEventListener("click", event -> action.run()).preventDefault();
		}
	}

	private static final class StatusEntry extends Div {

		private final Span message;

		private StatusEntry(final TestResult result) {
			message = new Span(result.message());
			message.addClassName("ws-status-tray-entry-message");
			add(message);
			addClassName("ws-status-tray-entry");
			getElement().setAttribute("data-severity", result.severity().name().toLowerCase(Locale.ROOT));
			getElement().setAttribute("role", "listitem");
			setWidthFull();
		}

		private void clearLink() {
			removeClassName("ws-status-tray-entry-linked");
			removeAll();
			add(message);
		}

		private void setLink(final String href, final SerializableRunnable action) {
			final StatusLink link = new StatusLink(href, message.getText(), action);
			addClassName("ws-status-tray-entry-linked");
			removeAll();
			add(link);
		}
	}

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
		clearLinks();
		if (currentItems.isEmpty()) {
			statusBadge.hide();
			return;
		}
		statusBadge.show(badgeVariant(currentItems), Integer.toString(currentItems.size()));
	}

	@Override
	protected Component renderItem(final TestResult result) {
		return new StatusEntry(result);
	}

	@Override
	public void setResults(final ValidationSummary results) {
		clearLinks();
		StatusTrayController.super.setResults(results);
	}

	@Override
	public <T extends Serializable> void setResults(final ValidationResults<T> results,
			final Predicate<? super T> linkedTarget,
			final SerializableFunction<? super T, String> href,
			final SerializableConsumer<? super T> action) {
		Objects.requireNonNull(results, "results must not be null");
		Objects.requireNonNull(linkedTarget, "linkedTarget must not be null");
		Objects.requireNonNull(href, "href must not be null");
		Objects.requireNonNull(action, "action must not be null");

		final List<TestResult> testResults = new ArrayList<>();
		final List<LinkTarget<T>> linkTargets = new ArrayList<>();
		results.forEach(result -> result.getTestResults().forEach(testResult -> {
			final int index = testResults.size();
			testResults.add(testResult);
			if (linkedTarget.test(result.getTarget())) {
				linkTargets.add(new LinkTarget<>(index, result.getTarget()));
			}
		}));

		clearLinks();
		setItems(testResults);
		final List<Component> renderedComponents = getRenderedComponents();
		linkTargets.forEach(linkTarget -> ((StatusEntry) renderedComponents.get(linkTarget.index()))
				.setLink(Objects.requireNonNull(href.apply(linkTarget.target()), "href must not return null"),
						() -> action.accept(linkTarget.target())));
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

	private void clearLinks() {
		getRenderedComponents().stream().map(StatusEntry.class::cast).forEach(StatusEntry::clearLink);
	}
}
