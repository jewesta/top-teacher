package de.westarps.vaadin.animate;

public enum Speed {

	SLOW("slow"),
	SLOWER("slower"),
	FAST("fast"),
	FASTER("faster");

	private final String className;

	Speed(final String className) {
		this.className = Animated.PREFIX + className;
	}

	public String getSpeedClassName() {
		return className;
	}
}
