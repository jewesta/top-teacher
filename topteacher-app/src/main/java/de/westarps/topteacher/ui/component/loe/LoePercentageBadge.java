package de.westarps.topteacher.ui.component.loe;

import java.util.function.Supplier;

import com.vaadin.flow.component.html.Span;

final class LoePercentageBadge extends LoeBadge {

	private final Supplier<Integer> percentageSupplier;

	LoePercentageBadge(final Supplier<Integer> percentageSupplier) {
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
