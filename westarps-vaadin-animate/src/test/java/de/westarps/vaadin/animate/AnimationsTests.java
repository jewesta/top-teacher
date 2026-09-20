package de.westarps.vaadin.animate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;

class AnimationsTests {

	@Tag("div")
	private static final class TestComponent extends Component {
	}

	@Test
	void startsAndStopsARepeatedAnimationWithoutDisturbingOtherClasses() {
		final TestComponent target = new TestComponent();
		target.addClassName("application-class");

		Animations.start(target, Effect.BOUNCE, Repeat.INFINITE, Speed.FAST);

		assertThat(target.getClassNames()).containsExactlyInAnyOrder("application-class", "animate__animated",
				"animate__bounce", "animate__infinite", "animate__fast");

		Animations.stop(target);

		assertThat(target.getClassNames()).containsExactly("application-class");
	}

	@Test
	void replacingAnAnimationRemovesAllPreviousAnimationClasses() {
		final TestComponent target = new TestComponent();
		Animations.start(target, Effect.BOUNCE, Repeat.INFINITE, Speed.SLOW);

		Animations.playOnce(target, Effect.HEAD_SHAKE, Speed.FASTER);

		assertThat(target.getClassNames()).containsExactlyInAnyOrder("animate__animated", "animate__headShake",
				"animate__faster");
	}
}
