package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExamNumberTests {

	@Test
	void displaysMainAndMakeupExamNumbers() {
		assertThat(new ExamNumber(1, false).getDisplayName()).isEqualTo("1.");
		assertThat(new ExamNumber(1, true).getDisplayName()).isEqualTo("1. (NK)");
		assertThat(new ExamNumber(2, false).getHeaderDisplayName()).isEqualTo("Klausur Nr. 2");
	}

	@Test
	void rejectsInvalidNumbers() {
		assertThatThrownBy(() -> new ExamNumber(0, false)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("number must be positive");
	}
}
