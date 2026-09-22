package de.westarps.topteacher.model.loe;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoePointUnits {

	public static final int UNITS_PER_POINT = 2;
	public static final int MAX_CRITERION_POINTS = 999;

	private static final Pattern POINTS_PATTERN = Pattern.compile("(0|[1-9]\\d*)(?:[.,]([05]))?");
	private static final int MAX_CRITERION_POINT_UNITS = MAX_CRITERION_POINTS * UNITS_PER_POINT;

	private LoePointUnits() {
	}

	public static int fromWholePoints(final int points) {
		if (points < 0) {
			throw new IllegalArgumentException("points must not be negative");
		}
		return Math.multiplyExact(points, UNITS_PER_POINT);
	}

	public static int roundedWholePoints(final int units) {
		if (units < 0) {
			throw new IllegalArgumentException("units must not be negative");
		}
		return (units + 1) / UNITS_PER_POINT;
	}

	public static OptionalInt parsePositive(final String value) {
		if (value == null) {
			return OptionalInt.empty();
		}
		final Matcher matcher = POINTS_PATTERN.matcher(value.trim());
		if (!matcher.matches()) {
			return OptionalInt.empty();
		}
		try {
			final int units = Math.addExact(Math.multiplyExact(Integer.parseInt(matcher.group(1)), UNITS_PER_POINT),
					"5".equals(matcher.group(2)) ? 1 : 0);
			return units > 0 && units <= MAX_CRITERION_POINT_UNITS ? OptionalInt.of(units) : OptionalInt.empty();
		} catch (final ArithmeticException exception) {
			return OptionalInt.empty();
		}
	}

	public static String formatGerman(final int units) {
		if (units < 0) {
			throw new IllegalArgumentException("units must not be negative");
		}
		return units % UNITS_PER_POINT == 0 ? Integer.toString(units / UNITS_PER_POINT)
				: units / UNITS_PER_POINT + ",5";
	}
}
