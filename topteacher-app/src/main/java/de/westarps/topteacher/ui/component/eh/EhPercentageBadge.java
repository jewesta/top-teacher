package de.westarps.topteacher.ui.component.eh;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class EhPercentageBadge extends EhBadge {

	private final Supplier<Integer> percentageSupplier;

	EhPercentageBadge(final Supplier<Integer> percentageSupplier) {
		super("tt-eh-percentage");
		this.percentageSupplier = percentageSupplier;
		refreshBadges();
	}

	@Override
	public void refreshBadges() {
		final String percentage = String.valueOf(percentageSupplier.get());
		setBadgeComponents(percentage + " %", number(percentage), new Span("\u00a0%"));
		getContent().addClassName("tt-eh-percentage-badge");
	}

	private static Span number(final String text) {
		final Span span = new Span(text);
		span.addClassName("tt-eh-percentage-number");
		return span;
	}
}
