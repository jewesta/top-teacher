package de.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SchoolYearTests {

	@Test
	void exposesStartEndAndDisplayName() {
		final SchoolYear schoolYear = new SchoolYear(2025);

		assertThat(schoolYear.getStartYear()).isEqualTo(2025);
		assertThat(schoolYear.getEndYear()).isEqualTo(2026);
		assertThat(schoolYear.getDisplayName()).isEqualTo("2025/2026");
		assertThat(schoolYear).hasToString("2025/2026");
	}

	@Test
	void rejectsInvalidStartYear() {
		assertThatThrownBy(() -> new SchoolYear(1899)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("startYear must be between 1900 and 9998");
	}
}
