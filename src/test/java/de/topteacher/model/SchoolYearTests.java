package de.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SchoolYearTests {

	@Test
	void exposesCalendarYearEndYearAndDisplayName() {
		final SchoolYear schoolYear = new SchoolYear(2025);

		assertThat(schoolYear.getCalendarYear()).isEqualTo(2025);
		assertThat(schoolYear.getEndYear()).isEqualTo(2026);
		assertThat(schoolYear.getDisplayName()).isEqualTo("2025/2026");
		assertThat(schoolYear.getShortDisplayName()).isEqualTo("'25/'26");
		assertThat(schoolYear).hasToString("2025/2026");
	}

	@Test
	void rejectsInvalidCalendarYear() {
		assertThatThrownBy(() -> new SchoolYear(1899)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("calendarYear must be between 1900 and 9998");
	}
}
