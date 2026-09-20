package de.westarps.vaadin.badge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import de.westarps.vaadin.badge.AttachedBadge.Position;
import de.westarps.vaadin.badge.Badge.BadgeVariant;

class BadgedComponentTests {

	private static final class BadgeableDiv extends Div implements Badgeable<BadgeableDiv> {

		private BadgeController badgeController;

		@Override
		public void onBadged(final BadgeController badgeController) {
			this.badgeController = badgeController;
		}
	}

	@Test
	void wrapsAndInformsABadgeableComponent() {
		final BadgeableDiv component = new BadgeableDiv();

		final Component result = Badgeable.badgeIfPossible(Position.TOP_RIGHT, component);

		assertThat(result).isInstanceOf(BadgedComponent.class);
		assertThat(component.badgeController).isNotNull();
		assertThat(component.badgeController.getWrapper()).isSameAs(result);
		assertThat(((BadgedComponent<?>) result).getBadge().getClassNames())
				.contains(BadgeController.FADE_CLASS_NAME, BadgeController.HIDDEN_CLASS_NAME);
	}

	@Test
	void leavesANonBadgeableComponentUnchanged() {
		final Div component = new Div();

		assertThat(Badgeable.badgeIfPossible(Position.RIGHT, component)).isSameAs(component);
	}

	@Test
	void controllerShowsTextAndIconBadges() {
		final BadgeableDiv component = new BadgeableDiv();
		final BadgedComponent<BadgeableDiv> wrapper = new BadgedComponent<>(Position.RIGHT, component);

		component.badgeController.show(BadgeVariant.WARNING, "7");

		assertThat(wrapper.getBadge().getClassNames()).doesNotContain(BadgeController.HIDDEN_CLASS_NAME);
		assertThat(wrapper.getBadge().getBadgeComponent().getElement().getChild(0).getText()).isEqualTo("7");
		assertThat(wrapper.getBadge().getBadgeComponent().getElement().getThemeList()).contains("warning");

		final Icon icon = VaadinIcon.CHECK.create();
		component.badgeController.show(BadgeVariant.SUCCESS, icon);

		assertThat(wrapper.getBadge().getBadgeComponent().getElement().getChildCount()).isEqualTo(1);
		assertThat(wrapper.getBadge().getBadgeComponent().getElement().getThemeList()).contains("success")
				.doesNotContain("warning");
	}

	@Test
	void positionsBadgesAtTheRequestedEdge() {
		final AttachedBadge topRight = new AttachedBadge(Position.TOP_RIGHT);
		final AttachedBadge right = new AttachedBadge(Position.RIGHT);

		assertThat(topRight.getStyle().get("top")).isEqualTo("-0.5em");
		assertThat(topRight.getStyle().get("right")).isEqualTo("-0.5em");
		assertThat(right.getStyle().get("top")).isEqualTo("50%");
		assertThat(right.getStyle().get("transform")).isEqualTo("translateY(-50%)");
	}
}
