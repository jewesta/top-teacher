package de.westarps.vaadin.tray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.function.SerializableConsumer;
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

	private record ActionTarget<T>(int index, T target) {
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

		private void clearAction() {
			removeClassName("ws-status-tray-entry-action");
			removeAll();
			add(message);
		}

		private void setAction(final SerializableRunnable action) {
			final Button button = new Button(message.getText(), event -> action.run());
			button.addClassName("ws-status-tray-entry-button");
			button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
			button.setTooltipText("Zum betroffenen Abschnitt springen");
			button.setWidthFull();
			addClassName("ws-status-tray-entry-action");
			removeAll();
			add(button);
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
		clearActions();
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
		clearActions();
		StatusTrayController.super.setResults(results);
	}

	@Override
	public <T extends Serializable> void setResults(final ValidationResults<T> results,
			final Predicate<? super T> actionableTarget,
			final SerializableConsumer<? super T> action) {
		Objects.requireNonNull(results, "results must not be null");
		Objects.requireNonNull(actionableTarget, "actionableTarget must not be null");
		Objects.requireNonNull(action, "action must not be null");

		final List<TestResult> testResults = new ArrayList<>();
		final List<ActionTarget<T>> actionTargets = new ArrayList<>();
		results.forEach(result -> result.getTestResults().forEach(testResult -> {
			final int index = testResults.size();
			testResults.add(testResult);
			if (actionableTarget.test(result.getTarget())) {
				actionTargets.add(new ActionTarget<>(index, result.getTarget()));
			}
		}));

		clearActions();
		setItems(testResults);
		final List<Component> renderedComponents = getRenderedComponents();
		actionTargets.forEach(actionTarget -> ((StatusEntry) renderedComponents.get(actionTarget.index()))
				.setAction(() -> action.accept(actionTarget.target())));
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

	private void clearActions() {
		getRenderedComponents().stream().map(StatusEntry.class::cast).forEach(StatusEntry::clearAction);
	}
}
