package de.topteacher.model;

import java.util.Objects;

public final class SchoolYear {

	private static final int MIN_START_YEAR = 1900;
	private static final int MAX_START_YEAR = 9998;

	private final int startYear;

	public SchoolYear(final int startYear) {
		if (startYear < MIN_START_YEAR || startYear > MAX_START_YEAR) {
			throw new IllegalArgumentException("startYear must be between 1900 and 9998");
		}
		this.startYear = startYear;
	}

	public int getStartYear() {
		return startYear;
	}

	public int getEndYear() {
		return startYear + 1;
	}

	public String getDisplayName() {
		return startYear + "/" + getEndYear();
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof final SchoolYear schoolYear)) {
			return false;
		}
		return startYear == schoolYear.startYear;
	}

	@Override
	public int hashCode() {
		return Objects.hash(startYear);
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
