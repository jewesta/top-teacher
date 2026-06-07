package de.westarps.topteacher.ui.component.loe;

record LoePoints(int regular, int bonus) {

	LoePoints plus(final LoePoints other) {
		return new LoePoints(regular + other.regular, bonus + other.bonus);
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
