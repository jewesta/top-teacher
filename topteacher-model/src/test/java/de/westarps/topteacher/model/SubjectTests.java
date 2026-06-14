package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubjectTests {

	@Test
	void trimsAndExposesDisplayName() {
		final Subject subject = new Subject(1, " Erdkunde ", Lifecycle.ACTIVE);

		assertThat(subject.name()).isEqualTo("Erdkunde");
		assertThat(subject.getDisplayName()).isEqualTo("Erdkunde");
	}

	@Test
	void rejectsBlankName() {
		assertThatThrownBy(() -> new Subject(1, " ", Lifecycle.ACTIVE)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("name must not be blank");
	}
}
