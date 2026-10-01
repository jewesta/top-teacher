package de.westarps.vaadin.animate;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.dom.DomEventListener;
import com.vaadin.flow.dom.DomListenerRegistration;

public final class Animations {

	private static final class RemoveAnimationOnEndListener implements DomEventListener {

		private final HasStyle target;

		private DomListenerRegistration registration;

		private RemoveAnimationOnEndListener(final HasStyle target) {
			this.target = target;
		}

		private void register() {
			registration = target.getElement().addEventListener("animationend", this)
					.setFilter("event.target === element");
		}

		@Override
		public void handleEvent(final DomEvent event) {
			stop(target);
			registration.remove();
		}
	}

	public static final String STYLESHEET = "./styles/ws-animate.css";

	private Animations() {
		// static utility class
	}

	public static void playOnce(final HasStyle target, final Effect effect) {
		playOnce(target, effect, null);
	}

	public static void playOnce(final HasStyle target, final Effect effect, final Speed speed) {
		final List<String> animationClasses = Animated.getAnimatedClasses(effect, speed);
		new RemoveAnimationOnEndListener(Objects.requireNonNull(target, "target must not be null")).register();
		start(target, animationClasses);
	}

	public static void start(final HasStyle target, final Effect effect, final Repeat repeat) {
		start(target, effect, repeat, null);
	}

	public static void start(final HasStyle target, final Effect effect, final Repeat repeat, final Speed speed) {
		start(target, Animated.getAnimatedClasses(effect, Objects.requireNonNull(repeat, "repeat must not be null"), speed));
	}

	public static void stop(final HasStyle target) {
		Objects.requireNonNull(target, "target must not be null").getClassNames()
				.removeAll(Animated.getAnimatedClassNames());
	}

	private static void start(final HasStyle target, final List<String> animationClasses) {
		Objects.requireNonNull(target, "target must not be null");
		stop(target);
		target.getClassNames().addAll(animationClasses);
		restartOnClient(target, animationClasses);
	}

	private static void restartOnClient(final HasStyle target, final List<String> animationClasses) {
		final String arguments = IntStream.range(0, animationClasses.size()).mapToObj(index -> "$" + index)
				.collect(Collectors.joining(", "));
		target.getElement().executeJs("""
			const animationClasses = [%s];
			this.classList.remove(...animationClasses);
			void this.offsetWidth;
			this.classList.add(...animationClasses);
			""".formatted(arguments), animationClasses.toArray());
	}
}
