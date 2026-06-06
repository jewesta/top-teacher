package de.westarps.topteacher.ui.component;

record EhPoints(int regular, int bonus) {

	EhPoints plus(final EhPoints other) {
		return new EhPoints(regular + other.regular, bonus + other.bonus);
	}

	int total() {
		return regular;
	}

	String label() {
		if (bonus == 0) {
			return String.valueOf(regular);
		}
		return regular + " (+ " + bonus + ")";
	}
}
