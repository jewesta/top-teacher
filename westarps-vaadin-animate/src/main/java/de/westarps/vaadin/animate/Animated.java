package de.westarps.vaadin.animate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Animated {

	static final String PREFIX = "animate__";

	public static final String ANIMATED = PREFIX + "animated";

	private static final List<String> ANIMATION_CLASS_NAMES = createAnimationClassNames();

	private Animated() {
		// static utility class
	}

	/**
	 * Returns every class name controlled by this animation module. The immutable
	 * result can be used to remove an existing animation before starting another.
	 */
	public static List<String> getAnimatedClassNames() {
		return ANIMATION_CLASS_NAMES;
	}

	public static List<String> getAnimatedClasses(final Effect effect) {
		return getAnimatedClasses(effect, null, null);
	}

	public static List<String> getAnimatedClasses(final Effect effect, final Repeat repeat) {
		return getAnimatedClasses(effect, repeat, null);
	}

	public static List<String> getAnimatedClasses(final Effect effect, final Speed speed) {
		return getAnimatedClasses(effect, null, speed);
	}

	public static List<String> getAnimatedClasses(final Effect effect, final Repeat repeat, final Speed speed) {
		final List<String> classNames = new ArrayList<>();
		classNames.add(ANIMATED);
		classNames.add(Objects.requireNonNull(effect, "effect must not be null").getEffectClassName());
		if (repeat != null) {
			classNames.add(repeat.getRepeatClassName());
		}
		if (speed != null) {
			classNames.add(speed.getSpeedClassName());
		}
		return List.copyOf(classNames);
	}

	private static List<String> createAnimationClassNames() {
		final List<String> classNames = new ArrayList<>();
		classNames.add(ANIMATED);
		for (final Effect effect : Effect.values()) {
			classNames.add(effect.getEffectClassName());
		}
		for (final Repeat repeat : Repeat.values()) {
			classNames.add(repeat.getRepeatClassName());
		}
		for (final Speed speed : Speed.values()) {
			classNames.add(speed.getSpeedClassName());
		}
		return List.copyOf(classNames);
	}
}
