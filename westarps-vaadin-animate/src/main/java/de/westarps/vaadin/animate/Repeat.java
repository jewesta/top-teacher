package de.westarps.vaadin.animate;

public enum Repeat {

	REPEAT_1("repeat-1"),
	REPEAT_2("repeat-2"),
	REPEAT_3("repeat-3"),
	INFINITE("infinite");

	private final String className;

	Repeat(final String className) {
		this.className = Animated.PREFIX + className;
	}

	public String getRepeatClassName() {
		return className;
	}
}
