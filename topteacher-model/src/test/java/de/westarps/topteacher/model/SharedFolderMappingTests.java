package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SharedFolderMappingTests {

	@Test
	void requiresAndNormalizesAbsoluteRoots() {
		final SharedFolderMapping mapping = new SharedFolderMapping(" /Volumes/School/./Exams/ ",
				" /mnt/school/exams/ ", 0);

		assertThat(mapping.clientPath()).isEqualTo("/Volumes/School/Exams");
		assertThat(mapping.serverPath()).isEqualTo("/mnt/school/exams");
		assertThatThrownBy(() -> new SharedFolderMapping("School", "/mnt/school", 0))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absolute");
	}
}
