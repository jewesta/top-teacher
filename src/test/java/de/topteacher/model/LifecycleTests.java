package de.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LifecycleTests {

	@Test
	void exposesGermanDisplayNames() {
		assertThat(Lifecycle.ACTIVE.getDisplayName()).isEqualTo("Aktiv");
		assertThat(Lifecycle.INACTIVE.getDisplayName()).isEqualTo("Inaktiv");
	}
}
