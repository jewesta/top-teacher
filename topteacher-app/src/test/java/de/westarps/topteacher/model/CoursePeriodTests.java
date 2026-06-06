package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoursePeriodTests {

	@Test
	void exposesGermanDisplayNames() {
		assertThat(CoursePeriod.FIRST_HALF.getDisplayName()).isEqualTo("1. Hj.");
		assertThat(CoursePeriod.SECOND_HALF.getDisplayName()).isEqualTo("2. Hj.");
		assertThat(CoursePeriod.FULL_YEAR.getDisplayName()).isEqualTo("Ganzjahr");
	}
}
