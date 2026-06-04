package de.topteacher.model;

import java.util.Objects;

public final class SchoolYear {

	private static final int MIN_CALENDAR_YEAR = 1900;
	private static final int MAX_CALENDAR_YEAR = 9998;

	private final int calendarYear;

	public SchoolYear(final int calendarYear) {
		if (calendarYear < MIN_CALENDAR_YEAR || calendarYear > MAX_CALENDAR_YEAR) {
			throw new IllegalArgumentException("calendarYear must be between 1900 and 9998");
		}
		this.calendarYear = calendarYear;
	}

	public int getCalendarYear() {
		return calendarYear;
	}

	public int getEndYear() {
		return calendarYear + 1;
	}

	public String getDisplayName() {
		return calendarYear + "/" + getEndYear();
	}

	public String getShortDisplayName() {
		return "'" + twoDigitYear(calendarYear) + "/'" + twoDigitYear(getEndYear());
	}

	private String twoDigitYear(final int year) {
		return String.format("%02d", year % 100);
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof final SchoolYear schoolYear)) {
			return false;
		}
		return calendarYear == schoolYear.calendarYear;
	}

	@Override
	public int hashCode() {
		return Objects.hash(calendarYear);
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
