package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class TermTests {

	@Test
	void exposesSchoolYearHalfAndDisplayName() {
		final SchoolYear schoolYear = new SchoolYear(2025);
		final Term term = new Term(schoolYear, Half.FIRST);

		assertThat(term.getSchoolYear()).isEqualTo(schoolYear);
		assertThat(term.getHalf()).isEqualTo(Half.FIRST);
		assertThat(term.getDisplayName()).isEqualTo("2025/2026, 1. Hj.");
		assertThat(term).hasToString("2025/2026, 1. Hj.");
	}

	@Test
	void rejectsMissingValues() {
		assertThatNullPointerException().isThrownBy(() -> new Term(null, Half.FIRST))
				.withMessage("schoolYear must not be null");
		assertThatNullPointerException().isThrownBy(() -> new Term(new SchoolYear(2025), null))
				.withMessage("half must not be null");
	}
}
