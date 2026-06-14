package de.westarps.topteacher.backend.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.model.HasDisplayName;

class DatabaseInitializationModeTests {

	@Test
	void exposesDisplayNames() {
		assertThat(DatabaseInitializationMode.EMPTY).isInstanceOf(HasDisplayName.class);
		assertThat(DatabaseInitializationMode.EMPTY.getDisplayName()).isEqualTo("Leere Datenbank");
		assertThat(DatabaseInitializationMode.DEMO.getDisplayName()).isEqualTo("Demodaten");
	}
}
