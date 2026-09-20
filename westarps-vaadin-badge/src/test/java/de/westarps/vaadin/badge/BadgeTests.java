package de.westarps.vaadin.badge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.icon.VaadinIcon;

import de.westarps.vaadin.badge.Badge.BadgeVariant;

class BadgeTests {

	@Test
	void switchesVariantsWithoutRetainingThePreviousVariant() {
		final Badge badge = new Badge("3");
		badge.setVariant(BadgeVariant.WARNING);

		badge.setVariant(BadgeVariant.SUCCESS);

		assertThat(badge.getElement().getThemeList()).contains("success").doesNotContain("warning");
	}

	@Test
	void combinesTextAndIconInTheRequestedOrder() {
		final Badge badge = new Badge("3");
		badge.setIcon(VaadinIcon.BELL.create(), false);

		assertThat(badge.getElement().getChildCount()).isEqualTo(2);
		assertThat(badge.getElement().getChild(0).getTag()).isEqualTo("span");
		assertThat(badge.getElement().getChild(1).getTag()).isEqualTo("vaadin-icon");
	}

	@Test
	void appliesShapeAndEmphasisThemesIndependently() {
		final Badge badge = new Badge();

		badge.setPrimary(true);
		badge.setPill(true);
		badge.setSmall(true);
		badge.setPadding(true);

		assertThat(badge.getElement().getThemeList()).containsExactlyInAnyOrder("primary", "pill", "small", "badge");
	}
}
