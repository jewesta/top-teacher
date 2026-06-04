package de.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubjectTests {

	@Test
	void exposesGermanDisplayNames() {
		assertThat(Subject.ENGLISH.getDisplayName()).isEqualTo("Englisch");
		assertThat(Subject.SPANISH.getDisplayName()).isEqualTo("Spanisch");
	}
}
