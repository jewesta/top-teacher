package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GradeLevelTests {

	@Test
	void mapsGradePointsToFixedGradeLevels() {
		assertThat(GradeLevel.fromPoints(15)).isEqualTo(GradeLevel.SEHR_GUT_PLUS);
		assertThat(GradeLevel.fromPoints(0)).isEqualTo(GradeLevel.UNGENUEGEND);
		assertThat(GradeLevel.SEHR_GUT_PLUS.getDisplayName()).isEqualTo("sehr gut plus");
		assertThat(GradeLevel.UNGENUEGEND.getDisplayName()).isEqualTo("ungenügend");
	}
}
