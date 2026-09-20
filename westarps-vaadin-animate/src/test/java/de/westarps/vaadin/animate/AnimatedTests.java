package de.westarps.vaadin.animate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AnimatedTests {

	@Test
	void buildsAnimationClassesInAnimateCssOrder() {
		assertThat(Animated.getAnimatedClasses(Effect.HEAD_SHAKE, Repeat.REPEAT_2, Speed.FASTER))
				.containsExactly("animate__animated", "animate__headShake", "animate__repeat-2", "animate__faster");
	}

	@Test
	void exposesAnImmutableCompleteAnimationClassList() {
		assertThat(Animated.getAnimatedClassNames()).contains(Animated.ANIMATED, Effect.HEAD_SHAKE.getEffectClassName(),
				Repeat.INFINITE.getRepeatClassName(), Speed.FASTER.getSpeedClassName()).doesNotHaveDuplicates();
		assertThat(Animated.getAnimatedClassNames()).isUnmodifiable();
	}

	@Test
	void everyDeclaredClassIsProvidedByTheBundledStylesheet() throws IOException {
		final String stylesheet;
		try (InputStream stream = getClass().getResourceAsStream("/META-INF/frontend/styles/ws-animate.css")) {
			assertThat(stream).isNotNull();
			stylesheet = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertThat(stylesheet).contains("." + Animated.ANIMATED);
		for (final Effect effect : Effect.values()) {
			assertThat(stylesheet).contains("." + effect.getEffectClassName());
		}
		for (final Repeat repeat : Repeat.values()) {
			assertThat(stylesheet).contains("." + repeat.getRepeatClassName());
		}
		for (final Speed speed : Speed.values()) {
			assertThat(stylesheet).contains("." + speed.getSpeedClassName());
		}
	}
}
