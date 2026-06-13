package de.westarps.topteacher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationVersionTests {

	@Test
	void stripsSnapshotSuffixFromDisplayVersion() {
		assertThat(ApplicationVersion.normalizedDisplayVersion("1.2.0-SNAPSHOT")).contains("1.2.0");
	}

	@Test
	void mapsZeroSeriesVersionsToMinimumDisplayVersion() {
		assertThat(ApplicationVersion.normalizedDisplayVersion("0.0.1-SNAPSHOT")).contains("1.0.0");
		assertThat(ApplicationVersion.normalizedDisplayVersion("0")).contains("1.0.0");
	}

	@Test
	void ignoresBlankDisplayVersion() {
		assertThat(ApplicationVersion.normalizedDisplayVersion(" ")).isEmpty();
	}

	@Test
	void ignoresUnresolvedBuildPlaceholders() {
		assertThat(ApplicationVersion.normalizedDisplayVersion("@project.version@")).isEmpty();
		assertThat(ApplicationVersion.normalizedDisplayVersion("${project.version}")).isEmpty();
	}

	@Test
	void readsFilteredBuildResourceVersion() {
		assertThat(ApplicationVersion.buildResourceVersion())
				.hasValueSatisfying(version -> assertThat(version).isNotBlank());
	}
}
