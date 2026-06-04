package de.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchoolClassTests {

	@Test
	void exposesStableKeysAndDisplayNames() {
		assertThat(SchoolClass.CLS_5A.getDisplayName()).isEqualTo("5a");
		assertThat(SchoolClass.CLS_10F.getDisplayName()).isEqualTo("10f");
		assertThat(SchoolClass.CLS_EF.getDisplayName()).isEqualTo("EF");
		assertThat(SchoolClass.CLS_Q1.getDisplayName()).isEqualTo("Q1");
		assertThat(SchoolClass.CLS_Q2.getDisplayName()).isEqualTo("Q2");
	}

	@Test
	void containsSixClassesPerSecondaryGradeAndUpperSchool() {
		assertThat(SchoolClass.values()).hasSize(39);
	}
}
