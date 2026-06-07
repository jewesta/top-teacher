package de.westarps.topteacher.model;

import java.util.Objects;

public final class Term {

	private final SchoolYear schoolYear;
	private final Half half;

	public Term(final SchoolYear schoolYear, final Half half) {
		this.schoolYear = Objects.requireNonNull(schoolYear, "schoolYear must not be null");
		this.half = Objects.requireNonNull(half, "half must not be null");
	}

	public SchoolYear getSchoolYear() {
		return schoolYear;
	}

	public Half getHalf() {
		return half;
	}

	public String getDisplayName() {
		return schoolYear.getDisplayName() + ", " + half.getDisplayName();
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof final Term term)) {
			return false;
		}
		return schoolYear.equals(term.schoolYear) && half == term.half;
	}

	@Override
	public int hashCode() {
		return Objects.hash(schoolYear, half);
	}

	@Override
	public String toString() {
		return getDisplayName();
	}
}
